package com.WhereHouse.APITest.FinancialInstitutionDetail.Component;

import com.WhereHouse.APITest.FinancialInstitutionDetail.Client.KakaoLocalApiClient;
import com.WhereHouse.APITest.FinancialInstitutionDetail.Config.SeoulDistrictCoords;
import com.WhereHouse.APITest.FinancialInstitutionDetail.DTO.CollectionProgress;
import com.WhereHouse.APITest.FinancialInstitutionDetail.DTO.KakaoLocalApiResponse;
import com.WhereHouse.APITest.FinancialInstitutionDetail.Entity.BankStatistics;
import com.WhereHouse.APITest.FinancialInstitutionDetail.Repository.BankStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class BankDataLoader implements CommandLineRunner {

    private final BankStatisticsRepository bankRepository;
    private final KakaoLocalApiClient kakaoApiClient;

    @Value("${kakao.local-api.max-page}")
    private int maxPage;

    @Value("${kakao.local-api.request-delay}")
    private long requestDelay;

    private static final String BANK_CATEGORY_CODE = "BK9";

    private static final List<String> MAJOR_BANKS = Arrays.asList(
            "KB국민은행", "신한은행", "우리은행", "하나은행", "IBK기업은행",
            "NH농협은행", "부산은행", "대구은행", "경남은행", "광주은행",
            "전북은행", "제주은행"
    );

    private CollectionProgress progress = new CollectionProgress();

    @Override
    @Transactional
    public void run(String... args) {
        progress.setStartTime(LocalDateTime.now());

        try {
            // 기존 데이터 체크
            long existingCount = bankRepository.count();
            if (existingCount > 0) {
                log.info("은행 데이터 이미 존재 ({} 개). 로딩 스킵", existingCount);
                return;
            }

            log.info("🏦 서울시 은행 지점 수집을 시작합니다...");
            log.info("📋 수집 설정: 최대 {}페이지, {}ms 대기, {} 구 대상", maxPage, requestDelay, SeoulDistrictCoords.SEOUL_DISTRICTS.size());

            Set<String> processedIds = new HashSet<>();

            // 전략 1: 구별 카테고리 검색
            progress.setCurrentTask("구별 카테고리 검색");
            collectBanksByDistrict(processedIds);

            // 전략 2: 은행 브랜드별 키워드 검색
            progress.setCurrentTask("브랜드별 키워드 검색");
            collectBanksByBrand(processedIds);

            progress.setEndTime(LocalDateTime.now());
            printFinalSummary();

        } catch (Exception e) {
            progress.addError(new CollectionProgress.ErrorDetail(
                    "SYSTEM_ERROR",
                    "전체 시스템 오류: " + e.getMessage(),
                    "BankDataLoader.run()",
                    null, null, null
            ));
            log.error("🚨 시스템 전체 오류 발생", e);
            throw e;
        }
    }

    /**
     * 구별 은행 카테고리 검색 (상세 진행상황 추적)
     */
    private void collectBanksByDistrict(Set<String> processedIds) {
        log.info("📍 구별 은행 카테고리 검색 시작 ({} 개 구)", SeoulDistrictCoords.SEOUL_DISTRICTS.size());

        int districtIndex = 0;
        for (SeoulDistrictCoords district : SeoulDistrictCoords.SEOUL_DISTRICTS) {
            districtIndex++;
            progress.setCurrentDistrict(district.getName());
            progress.setCurrentPage(0);

            log.info("🔍 [{}/{}] {} 은행 검색 중...", districtIndex, SeoulDistrictCoords.SEOUL_DISTRICTS.size(), district.getName());

            int pageCount = 0;
            int districtBankCount = 0;
            boolean hasError = false;

            try {
                for (int page = 1; page <= maxPage; page++) {
                    progress.setCurrentPage(page);
                    pageCount++;

                    // API 호출 시작 로그
                    log.debug("  📄 페이지 {} 요청 중... (좌표: {}, {})", page, district.getLatitude(), district.getLongitude());

                    KakaoLocalApiResponse response = null;
                    try {
                        response = kakaoApiClient.searchByCategory(
                                BANK_CATEGORY_CODE,
                                district.getLongitude(),
                                district.getLatitude(),
                                page
                        );
                    } catch (HttpClientErrorException e) {
                        String errorMsg = String.format("카카오 API 클라이언트 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_CLIENT_ERROR", errorMsg,
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                null, null, district.getName()
                        ));
                        log.warn("  ⚠️ API 클라이언트 오류: {} - 페이지 {} 스킵", district.getName(), page);
                        hasError = true;
                        break;
                    } catch (HttpServerErrorException e) {
                        String errorMsg = String.format("카카오 API 서버 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_SERVER_ERROR", errorMsg,
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                null, null, district.getName()
                        ));
                        log.warn("  ⚠️ API 서버 오류: {} - 페이지 {} 스킵", district.getName(), page);
                        hasError = true;
                        break;
                    } catch (ResourceAccessException e) {
                        String errorMsg = String.format("네트워크 연결 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "NETWORK_ERROR", errorMsg,
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                null, null, district.getName()
                        ));
                        log.warn("  ⚠️ 네트워크 오류: {} - 페이지 {} 스킵 후 재시도", district.getName(), page);

                        // 네트워크 오류시 더 긴 대기 후 재시도
                        sleepDelay(requestDelay * 5);
                        continue;
                    } catch (Exception e) {
                        String errorMsg = String.format("API 호출 예상치 못한 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_UNKNOWN_ERROR", errorMsg,
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                null, null, district.getName()
                        ));
                        log.error("  🚨 예상치 못한 API 오류: {} - 페이지 {}", district.getName(), page, e);
                        hasError = true;
                        break;
                    }

                    // 응답 데이터 검증
                    if (response == null || response.getDocuments() == null) {
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "EMPTY_RESPONSE", "API 응답이 null이거나 documents가 없음",
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                null, null, district.getName()
                        ));
                        log.warn("  ⚠️ 빈 응답: {} - 페이지 {}", district.getName(), page);
                        break;
                    }

                    if (response.getDocuments().isEmpty()) {
                        log.debug("  📄 페이지 {} 결과 없음 - 검색 종료", page);
                        break;
                    }

                    log.debug("  📄 페이지 {} 응답: {} 개 은행 발견", page, response.getDocuments().size());

                    // 각 은행 데이터 처리
                    for (KakaoLocalApiResponse.Document doc : response.getDocuments()) {
                        try {
                            // 이미 처리된 경우 스킵
                            if (processedIds.contains(doc.getId())) {
                                log.trace("    🔄 중복 스킵: {} (ID: {})", doc.getPlaceName(), doc.getId());
                                progress.incrementSkip();
                                continue;
                            }

                            // 서울시 주소가 아닌 경우 스킵
                            if (!isSeoulAddress(doc.getAddressName())) {
                                log.trace("    🗺️ 서울시 외 주소 스킵: {} - {}", doc.getPlaceName(), doc.getAddressName());
                                progress.incrementSkip();
                                continue;
                            }

                            // 엔티티 변환 및 저장
                            BankStatistics bank = convertToEntity(doc, district.getName());

                            try {
                                bankRepository.save(bank);
                                processedIds.add(doc.getId());
                                districtBankCount++;
                                progress.incrementSuccess();

                                log.trace("    ✅ 저장완료: {} - {}", bank.getPlaceName(), bank.getAddressName());

                            } catch (DataIntegrityViolationException e) {
                                String errorMsg = String.format("데이터 무결성 위반 (중복 키 등): %s", e.getMessage());
                                progress.addError(new CollectionProgress.ErrorDetail(
                                        "DATA_INTEGRITY_ERROR", errorMsg,
                                        String.format("구별검색-%s-페이지%d", district.getName(), page),
                                        doc.getPlaceName(), doc.getId(), district.getName()
                                ));
                                log.debug("    ⚠️ 데이터 무결성 위반 스킵: {} - {}", doc.getPlaceName(), e.getMessage());

                            } catch (Exception e) {
                                String errorMsg = String.format("DB 저장 오류: %s", e.getMessage());
                                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                        "DATABASE_ERROR", errorMsg,
                                        String.format("구별검색-%s-페이지%d", district.getName(), page),
                                        doc.getPlaceName(), doc.getId(), district.getName()
                                );
                                error.setStackTrace(getStackTrace(e));
                                progress.addError(error);

                                log.warn("    🚨 DB 저장 실패: {} - {}", doc.getPlaceName(), e.getMessage());
                            }

                        } catch (Exception e) {
                            String errorMsg = String.format("데이터 처리 오류: %s", e.getMessage());
                            CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                    "DATA_PROCESSING_ERROR", errorMsg,
                                    String.format("구별검색-%s-페이지%d", district.getName(), page),
                                    doc.getPlaceName(), doc.getId(), district.getName()
                            );
                            error.setStackTrace(getStackTrace(e));
                            progress.addError(error);

                            log.error("    🚨 데이터 처리 실패: {} - {}", doc.getPlaceName(), e.getMessage(), e);
                        }
                    }

                    // 마지막 페이지 확인
                    if (response.getMeta().isEnd()) {
                        log.debug("  📄 마지막 페이지 도달 - 검색 완료");
                        break;
                    }

                    // API 호출 간격 조정
                    sleepDelay(requestDelay);
                }

            } catch (Exception e) {
                String errorMsg = String.format("구별 검색 전체 실패: %s", e.getMessage());
                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                        "DISTRICT_SEARCH_ERROR", errorMsg,
                        String.format("구별검색-%s-전체", district.getName()),
                        null, null, district.getName()
                );
                error.setStackTrace(getStackTrace(e));
                progress.addError(error);

                log.error("🚨 {} 전체 검색 실패", district.getName(), e);
                hasError = true;
            }

            // 구별 완료 로그
            progress.setCompletedDistricts(progress.getCompletedDistricts() + 1);
            String statusIcon = hasError ? "⚠️" : "✅";
            log.info("  {} {} 완료: {} 개 은행, {} 페이지 검색 | {}",
                    statusIcon, district.getName(), districtBankCount, pageCount, progress.getProgressStatus());
        }

        log.info("✅ 구별 검색 완료 | 전체 진행률: {:.1f}% | {}",
                progress.getProgressPercentage(), progress.getProgressStatus());
    }

    /**
     * 은행 브랜드별 키워드 검색 (상세 진행상황 추적)
     */
    private void collectBanksByBrand(Set<String> processedIds) {
        log.info("🏢 브랜드별 은행 키워드 검색 시작 ({} 개 브랜드)", MAJOR_BANKS.size());

        // 서울 중심부 좌표 (시청 기준)
        BigDecimal seoulCenterX = new BigDecimal("126.9780");
        BigDecimal seoulCenterY = new BigDecimal("37.5665");

        int brandIndex = 0;
        for (String bankBrand : MAJOR_BANKS) {
            brandIndex++;
            progress.setCurrentDistrict(bankBrand);
            progress.setCurrentPage(0);

            log.info("🔍 [{}/{}] {} 검색 중...", brandIndex, MAJOR_BANKS.size(), bankBrand);

            String query = bankBrand + " 서울";
            int pageCount = 0;
            int brandBankCount = 0;
            boolean hasError = false;

            try {
                for (int page = 1; page <= maxPage; page++) {
                    progress.setCurrentPage(page);
                    pageCount++;

                    log.debug("  📄 페이지 {} 요청 중... (키워드: {})", page, query);

                    KakaoLocalApiResponse response = null;
                    try {
                        response = kakaoApiClient.searchByKeyword(
                                query,
                                seoulCenterX,
                                seoulCenterY,
                                page
                        );
                    } catch (HttpClientErrorException e) {
                        String errorMsg = String.format("카카오 API 클라이언트 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_CLIENT_ERROR", errorMsg,
                                String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                null, null, bankBrand
                        ));
                        log.warn("  ⚠️ API 클라이언트 오류: {} - 페이지 {} 스킵", bankBrand, page);
                        hasError = true;
                        break;
                    } catch (HttpServerErrorException e) {
                        String errorMsg = String.format("카카오 API 서버 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_SERVER_ERROR", errorMsg,
                                String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                null, null, bankBrand
                        ));
                        log.warn("  ⚠️ API 서버 오류: {} - 페이지 {} 스킵", bankBrand, page);
                        hasError = true;
                        break;
                    } catch (ResourceAccessException e) {
                        String errorMsg = String.format("네트워크 연결 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "NETWORK_ERROR", errorMsg,
                                String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                null, null, bankBrand
                        ));
                        log.warn("  ⚠️ 네트워크 오류: {} - 페이지 {} 재시도", bankBrand, page);

                        sleepDelay(requestDelay * 5);
                        continue;
                    } catch (Exception e) {
                        String errorMsg = String.format("API 호출 예상치 못한 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_UNKNOWN_ERROR", errorMsg,
                                String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                null, null, bankBrand
                        ));
                        log.error("  🚨 예상치 못한 API 오류: {} - 페이지 {}", bankBrand, page, e);
                        hasError = true;
                        break;
                    }

                    if (response == null || response.getDocuments() == null) {
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "EMPTY_RESPONSE", "API 응답이 null이거나 documents가 없음",
                                String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                null, null, bankBrand
                        ));
                        log.warn("  ⚠️ 빈 응답: {} - 페이지 {}", bankBrand, page);
                        break;
                    }

                    if (response.getDocuments().isEmpty()) {
                        log.debug("  📄 페이지 {} 결과 없음 - 검색 종료", page);
                        break;
                    }

                    log.debug("  📄 페이지 {} 응답: {} 개 결과", page, response.getDocuments().size());

                    for (KakaoLocalApiResponse.Document doc : response.getDocuments()) {
                        try {
                            if (processedIds.contains(doc.getId())) {
                                log.trace("    🔄 중복 스킵: {} (ID: {})", doc.getPlaceName(), doc.getId());
                                progress.incrementSkip();
                                continue;
                            }

                            if (!isSeoulAddress(doc.getAddressName())) {
                                log.trace("    🗺️ 서울시 외 주소 스킵: {} - {}", doc.getPlaceName(), doc.getAddressName());
                                progress.incrementSkip();
                                continue;
                            }

                            String district = extractDistrictFromAddress(doc.getAddressName());
                            BankStatistics bank = convertToEntity(doc, district);
                            bank.setBankBrand(bankBrand);

                            try {
                                bankRepository.save(bank);
                                processedIds.add(doc.getId());
                                brandBankCount++;
                                progress.incrementSuccess();

                                log.trace("    ✅ 저장완료: {} - {}", bank.getPlaceName(), bank.getAddressName());

                            } catch (DataIntegrityViolationException e) {
                                String errorMsg = String.format("데이터 무결성 위반: %s", e.getMessage());
                                progress.addError(new CollectionProgress.ErrorDetail(
                                        "DATA_INTEGRITY_ERROR", errorMsg,
                                        String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                        doc.getPlaceName(), doc.getId(), bankBrand
                                ));
                                log.debug("    ⚠️ 데이터 무결성 위반 스킵: {} - {}", doc.getPlaceName(), e.getMessage());

                            } catch (Exception e) {
                                String errorMsg = String.format("DB 저장 오류: %s", e.getMessage());
                                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                        "DATABASE_ERROR", errorMsg,
                                        String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                        doc.getPlaceName(), doc.getId(), bankBrand
                                );
                                error.setStackTrace(getStackTrace(e));
                                progress.addError(error);

                                log.warn("    🚨 DB 저장 실패: {} - {}", doc.getPlaceName(), e.getMessage());
                            }

                        } catch (Exception e) {
                            String errorMsg = String.format("데이터 처리 오류: %s", e.getMessage());
                            CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                    "DATA_PROCESSING_ERROR", errorMsg,
                                    String.format("브랜드검색-%s-페이지%d", bankBrand, page),
                                    doc.getPlaceName(), doc.getId(), bankBrand
                            );
                            error.setStackTrace(getStackTrace(e));
                            progress.addError(error);

                            log.error("    🚨 데이터 처리 실패: {} - {}", doc.getPlaceName(), e.getMessage(), e);
                        }
                    }

                    if (response.getMeta().isEnd()) {
                        log.debug("  📄 마지막 페이지 도달 - 검색 완료");
                        break;
                    }

                    sleepDelay(requestDelay);
                }

            } catch (Exception e) {
                String errorMsg = String.format("브랜드 검색 전체 실패: %s", e.getMessage());
                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                        "BRAND_SEARCH_ERROR", errorMsg,
                        String.format("브랜드검색-%s-전체", bankBrand),
                        null, null, bankBrand
                );
                error.setStackTrace(getStackTrace(e));
                progress.addError(error);

                log.error("🚨 {} 브랜드 전체 검색 실패", bankBrand, e);
                hasError = true;
            }

            String statusIcon = hasError ? "⚠️" : "✅";
            log.info("  {} {} 완료: {} 개 은행, {} 페이지 검색", statusIcon, bankBrand, brandBankCount, pageCount);
        }

        log.info("✅ 브랜드별 검색 완료 | {}", progress.getProgressStatus());
    }

    /**
     * 최종 수집 결과 요약 출력
     */
    private void printFinalSummary() {
        Duration duration = Duration.between(progress.getStartTime(), progress.getEndTime());
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;

        log.info("🎯=================== 은행 데이터 수집 완료 ===================");
        log.info("📊 수집 통계:");
        log.info("   • 시작 시간: {}", progress.getStartTime());
        log.info("   • 종료 시간: {}", progress.getEndTime());
        log.info("   • 소요 시간: {}분 {}초", minutes, seconds);
        log.info("   • 총 처리: {} 개", progress.getTotalProcessed().get());
        log.info("   • 성공 저장: {} 개", progress.getSuccessCount().get());
        log.info("   • 중복 스킵: {} 개", progress.getSkipCount().get());
        log.info("   • 오류 발생: {} 개", progress.getErrorCount().get());

        if (progress.getErrorCount().get() > 0) {
            log.info("📋 오류 유형별 통계:");
            Map<String, Long> errorStats = progress.getErrors().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            CollectionProgress.ErrorDetail::getErrorType,
                            java.util.stream.Collectors.counting()
                    ));

            errorStats.forEach((type, count) ->
                    log.info("   • {}: {} 건", type, count));

            log.info("⚠️ 상세 오류 내역:");
            progress.getErrors().stream()
                    .limit(10) // 최대 10개만 출력
                    .forEach(error -> log.warn("   [{}] {} - {} ({})",
                            error.getErrorType(),
                            error.getErrorMessage(),
                            error.getPlaceName() != null ? error.getPlaceName() : "N/A",
                            error.getContext()));

            if (progress.getErrors().size() > 10) {
                log.info("   ... 외 {} 건의 오류가 더 발생했습니다.", progress.getErrors().size() - 10);
            }
        }

        // DB 최종 확인
        try {
            long finalCount = bankRepository.count();
            log.info("💾 DB 저장 확인: {} 개 은행 데이터", finalCount);

            // 구별 분포 확인
            List<Object[]> districtStats = bankRepository.countBanksByDistrict();
            log.info("🗺️ 구별 분포 상위 5개:");
            districtStats.stream()
                    .limit(5)
                    .forEach(stat -> log.info("   • {}: {} 개", stat[0], stat[1]));

        } catch (Exception e) {
            log.error("DB 최종 확인 실패", e);
        }

        log.info("🎯==========================================================");
    }

    /**
     * 카카오 응답 데이터를 Entity로 변환
     */
    private BankStatistics convertToEntity(KakaoLocalApiResponse.Document doc, String district) {
        try {
            return BankStatistics.builder()
                    .kakaoPlaceId(doc.getId())
                    .placeName(doc.getPlaceName())
                    .categoryName(doc.getCategoryName())
                    .categoryGroupCode(doc.getCategoryGroupCode())
                    .phone(doc.getPhone())
                    .addressName(doc.getAddressName())
                    .roadAddressName(doc.getRoadAddressName())
                    .longitude(parseBigDecimal(doc.getLongitude()))
                    .latitude(parseBigDecimal(doc.getLatitude()))
                    .placeUrl(doc.getPlaceUrl())
                    .district(district)
                    .bankBrand(extractBankBrandFromName(doc.getPlaceName()))
                    .build();
        } catch (Exception e) {
            log.error("Entity 변환 실패: {}", doc.getPlaceName(), e);
            throw new RuntimeException("Entity 변환 실패: " + doc.getPlaceName(), e);
        }
    }

    /**
     * 서울시 주소인지 확인
     */
    private boolean isSeoulAddress(String address) {
        return address != null && address.startsWith("서울");
    }

    /**
     * 주소에서 구 정보 추출
     */
    private String extractDistrictFromAddress(String address) {
        if (address == null) return null;

        for (SeoulDistrictCoords district : SeoulDistrictCoords.SEOUL_DISTRICTS) {
            if (address.contains(district.getName())) {
                return district.getName();
            }
        }
        return null;
    }

    /**
     * 상호명에서 은행 브랜드 추출
     */
    private String extractBankBrandFromName(String placeName) {
        if (placeName == null) return null;

        for (String brand : MAJOR_BANKS) {
            if (placeName.contains(brand.replace("은행", ""))) {
                return brand;
            }
        }
        return null;
    }

    /**
     * 문자열을 BigDecimal로 변환
     */
    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("BigDecimal 변환 실패: {}", value);
            return null;
        }
    }

    /**
     * API 호출 간 대기 (오버로드 버전)
     */
    private void sleepDelay(long delayMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("대기 중 인터럽트 발생");
        }
    }

    /**
     * API 호출 간 대기 (기본 버전)
     */
    private void sleepDelay() {
        sleepDelay(requestDelay);
    }

    /**
     * Exception Stack Trace를 문자열로 변환
     */
    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
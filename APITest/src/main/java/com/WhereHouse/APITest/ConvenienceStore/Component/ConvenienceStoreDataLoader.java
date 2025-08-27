package com.WhereHouse.APITest.ConvenienceStore.Component;

import com.WhereHouse.APITest.ConvenienceStore.client.KakaoLocalApiClient;
import com.WhereHouse.APITest.ConvenienceStore.config.SeoulDistrictCoords;
import com.WhereHouse.APITest.ConvenienceStore.DTO.CollectionProgress;
import com.WhereHouse.APITest.ConvenienceStore.DTO.KakaoLocalApiResponse;
import com.WhereHouse.APITest.ConvenienceStore.Entity.ConvenienceStoreStatistics;
import com.WhereHouse.APITest.ConvenienceStore.Repository.ConvenienceStoreStatisticsRepository;
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
public class ConvenienceStoreDataLoader implements CommandLineRunner {

    private final ConvenienceStoreStatisticsRepository convenienceStoreRepository;
    private final KakaoLocalApiClient kakaoApiClient;

    @Value("${kakao.local-api.max-page}")
    private int maxPage;

    @Value("${kakao.local-api.request-delay}")
    private long requestDelay;

    private static final String CONVENIENCE_STORE_CATEGORY_CODE = "CS2";

    private static final List<String> MAJOR_CONVENIENCE_BRANDS = Arrays.asList(
            "GS25", "CU", "세븐일레븐", "이마트24", "미니스톱"
    );

    private static final List<String> CONVENIENCE_STORE_KEYWORDS = Arrays.asList(
            "편의점", "GS25", "CU", "세븐일레븐", "7-Eleven", "이마트24", "미니스톱"
    );

    private CollectionProgress progress = new CollectionProgress();

    @Override
    @Transactional
    public void run(String... args) {
        progress.setStartTime(LocalDateTime.now());

        try {
            // 기존 데이터 체크
            long existingCount = convenienceStoreRepository.count();
            if (existingCount > 0) {
                log.info("편의점 데이터 이미 존재 ({} 개). 로딩 스킵", existingCount);
                return;
            }

            log.info("편의점 서울시 편의점 지점 수집을 시작합니다...");
            log.info("수집 설정: 최대 {}페이지, {}ms 대기, {} 구 대상", maxPage, requestDelay, SeoulDistrictCoords.SEOUL_DISTRICTS.size());

            Set<String> processedIds = new HashSet<>();

            // 전략 1: 구별 카테고리 검색
            progress.setCurrentTask("구별 카테고리 검색");
            collectConvenienceStoresByDistrict(processedIds);

            // 전략 2: 편의점 브랜드별 키워드 검색
            progress.setCurrentTask("브랜드별 키워드 검색");
            collectConvenienceStoresByBrand(processedIds);

            // 전략 3: 편의점 일반 키워드 검색
            progress.setCurrentTask("일반 키워드 검색");
            collectConvenienceStoresByKeyword(processedIds);

            progress.setEndTime(LocalDateTime.now());
            printFinalSummary();

        } catch (Exception e) {
            progress.addError(new CollectionProgress.ErrorDetail(
                    "SYSTEM_ERROR",
                    "전체 시스템 오류: " + e.getMessage(),
                    "ConvenienceStoreDataLoader.run()",
                    null, null, null
            ));
            log.error("시스템 전체 오류 발생", e);
            throw e;
        }
    }

    /**
     * 구별 편의점 카테고리 검색 (상세 진행상황 추적)
     */
    private void collectConvenienceStoresByDistrict(Set<String> processedIds) {
        log.info("구별 편의점 카테고리 검색 시작 ({} 개 구)", SeoulDistrictCoords.SEOUL_DISTRICTS.size());

        int districtIndex = 0;
        for (SeoulDistrictCoords district : SeoulDistrictCoords.SEOUL_DISTRICTS) {
            districtIndex++;
            progress.setCurrentDistrict(district.getName());
            progress.setCurrentPage(0);

            log.info("[{}/{}] {} 편의점 검색 중...", districtIndex, SeoulDistrictCoords.SEOUL_DISTRICTS.size(), district.getName());

            int pageCount = 0;
            int districtStoreCount = 0;
            boolean hasError = false;

            try {
                for (int page = 1; page <= maxPage; page++) {
                    progress.setCurrentPage(page);
                    pageCount++;

                    log.debug("  페이지 {} 요청 중... (좌표: {}, {})", page, district.getLatitude(), district.getLongitude());

                    KakaoLocalApiResponse response = null;
                    try {
                        response = kakaoApiClient.searchByCategory(
                                CONVENIENCE_STORE_CATEGORY_CODE,
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
                        log.warn("  API 클라이언트 오류: {} - 페이지 {} 스킵", district.getName(), page);
                        hasError = true;
                        break;
                    } catch (HttpServerErrorException e) {
                        String errorMsg = String.format("카카오 API 서버 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_SERVER_ERROR", errorMsg,
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                null, null, district.getName()
                        ));
                        log.warn("  API 서버 오류: {} - 페이지 {} 스킵", district.getName(), page);
                        hasError = true;
                        break;
                    } catch (ResourceAccessException e) {
                        String errorMsg = String.format("네트워크 연결 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "NETWORK_ERROR", errorMsg,
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                null, null, district.getName()
                        ));
                        log.warn("  네트워크 오류: {} - 페이지 {} 스킵 후 재시도", district.getName(), page);

                        sleepDelay(requestDelay * 5);
                        continue;
                    } catch (Exception e) {
                        String errorMsg = String.format("API 호출 예상치 못한 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_UNKNOWN_ERROR", errorMsg,
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                null, null, district.getName()
                        ));
                        log.error("  예상치 못한 API 오류: {} - 페이지 {}", district.getName(), page, e);
                        hasError = true;
                        break;
                    }

                    if (response == null || response.getDocuments() == null) {
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "EMPTY_RESPONSE", "API 응답이 null이거나 documents가 없음",
                                String.format("구별검색-%s-페이지%d", district.getName(), page),
                                null, null, district.getName()
                        ));
                        log.warn("  빈 응답: {} - 페이지 {}", district.getName(), page);
                        break;
                    }

                    if (response.getDocuments().isEmpty()) {
                        log.debug("  페이지 {} 결과 없음 - 검색 종료", page);
                        break;
                    }

                    log.debug("  페이지 {} 응답: {} 개 편의점 발견", page, response.getDocuments().size());

                    for (KakaoLocalApiResponse.Document doc : response.getDocuments()) {
                        try {
                            if (processedIds.contains(doc.getId())) {
                                log.trace("    중복 스킵: {} (ID: {})", doc.getPlaceName(), doc.getId());
                                progress.incrementSkip();
                                continue;
                            }

                            if (!isSeoulAddress(doc.getAddressName())) {
                                log.trace("    서울시 외 주소 스킵: {} - {}", doc.getPlaceName(), doc.getAddressName());
                                progress.incrementSkip();
                                continue;
                            }

                            ConvenienceStoreStatistics store = convertToEntity(doc, district.getName());

                            try {
                                convenienceStoreRepository.save(store);
                                processedIds.add(doc.getId());
                                districtStoreCount++;
                                progress.incrementSuccess();

                                log.trace("    저장완료: {} - {}", store.getPlaceName(), store.getAddressName());

                            } catch (DataIntegrityViolationException e) {
                                String errorMsg = String.format("데이터 무결성 위반 (중복 키 등): %s", e.getMessage());
                                progress.addError(new CollectionProgress.ErrorDetail(
                                        "DATA_INTEGRITY_ERROR", errorMsg,
                                        String.format("구별검색-%s-페이지%d", district.getName(), page),
                                        doc.getPlaceName(), doc.getId(), district.getName()
                                ));
                                log.debug("    데이터 무결성 위반 스킵: {} - {}", doc.getPlaceName(), e.getMessage());

                            } catch (Exception e) {
                                String errorMsg = String.format("DB 저장 오류: %s", e.getMessage());
                                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                        "DATABASE_ERROR", errorMsg,
                                        String.format("구별검색-%s-페이지%d", district.getName(), page),
                                        doc.getPlaceName(), doc.getId(), district.getName()
                                );
                                error.setStackTrace(getStackTrace(e));
                                progress.addError(error);

                                log.warn("    DB 저장 실패: {} - {}", doc.getPlaceName(), e.getMessage());
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

                            log.error("    데이터 처리 실패: {} - {}", doc.getPlaceName(), e.getMessage(), e);
                        }
                    }

                    if (response.getMeta().isEnd()) {
                        log.debug("  마지막 페이지 도달 - 검색 완료");
                        break;
                    }

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

                log.error("{} 전체 검색 실패", district.getName(), e);
                hasError = true;
            }

            progress.setCompletedDistricts(progress.getCompletedDistricts() + 1);
            String statusIcon = hasError ? "경고" : "완료";
            log.info("  {} {} 완료: {} 개 편의점, {} 페이지 검색 | {}",
                    statusIcon, district.getName(), districtStoreCount, pageCount, progress.getProgressStatus());
        }

        log.info("구별 검색 완료 | 전체 진행률: {:.1f}% | {}",
                progress.getProgressPercentage(), progress.getProgressStatus());
    }

    /**
     * 편의점 브랜드별 키워드 검색 (상세 진행상황 추적)
     */
    private void collectConvenienceStoresByBrand(Set<String> processedIds) {
        log.info("브랜드별 편의점 키워드 검색 시작 ({} 개 브랜드)", MAJOR_CONVENIENCE_BRANDS.size());

        BigDecimal seoulCenterX = new BigDecimal("126.9780");
        BigDecimal seoulCenterY = new BigDecimal("37.5665");

        int brandIndex = 0;
        for (String storeBrand : MAJOR_CONVENIENCE_BRANDS) {
            brandIndex++;
            progress.setCurrentDistrict(storeBrand);
            progress.setCurrentPage(0);

            log.info("[{}/{}] {} 검색 중...", brandIndex, MAJOR_CONVENIENCE_BRANDS.size(), storeBrand);

            String query = storeBrand + " 서울";
            int pageCount = 0;
            int brandStoreCount = 0;
            boolean hasError = false;

            try {
                for (int page = 1; page <= maxPage; page++) {
                    progress.setCurrentPage(page);
                    pageCount++;

                    log.debug("  페이지 {} 요청 중... (키워드: {})", page, query);

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
                                String.format("브랜드검색-%s-페이지%d", storeBrand, page),
                                null, null, storeBrand
                        ));
                        log.warn("  API 클라이언트 오류: {} - 페이지 {} 스킵", storeBrand, page);
                        hasError = true;
                        break;
                    } catch (HttpServerErrorException e) {
                        String errorMsg = String.format("카카오 API 서버 오류 (HTTP %d): %s", e.getStatusCode().value(), e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_SERVER_ERROR", errorMsg,
                                String.format("브랜드검색-%s-페이지%d", storeBrand, page),
                                null, null, storeBrand
                        ));
                        log.warn("  API 서버 오류: {} - 페이지 {} 스킵", storeBrand, page);
                        hasError = true;
                        break;
                    } catch (ResourceAccessException e) {
                        String errorMsg = String.format("네트워크 연결 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "NETWORK_ERROR", errorMsg,
                                String.format("브랜드검색-%s-페이지%d", storeBrand, page),
                                null, null, storeBrand
                        ));
                        log.warn("  네트워크 오류: {} - 페이지 {} 재시도", storeBrand, page);

                        sleepDelay(requestDelay * 5);
                        continue;
                    } catch (Exception e) {
                        String errorMsg = String.format("API 호출 예상치 못한 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_UNKNOWN_ERROR", errorMsg,
                                String.format("브랜드검색-%s-페이지%d", storeBrand, page),
                                null, null, storeBrand
                        ));
                        log.error("  예상치 못한 API 오류: {} - 페이지 {}", storeBrand, page, e);
                        hasError = true;
                        break;
                    }

                    if (response == null || response.getDocuments() == null) {
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "EMPTY_RESPONSE", "API 응답이 null이거나 documents가 없음",
                                String.format("브랜드검색-%s-페이지%d", storeBrand, page),
                                null, null, storeBrand
                        ));
                        log.warn("  빈 응답: {} - 페이지 {}", storeBrand, page);
                        break;
                    }

                    if (response.getDocuments().isEmpty()) {
                        log.debug("  페이지 {} 결과 없음 - 검색 종료", page);
                        break;
                    }

                    log.debug("  페이지 {} 응답: {} 개 결과", page, response.getDocuments().size());

                    for (KakaoLocalApiResponse.Document doc : response.getDocuments()) {
                        try {
                            if (processedIds.contains(doc.getId())) {
                                log.trace("    중복 스킵: {} (ID: {})", doc.getPlaceName(), doc.getId());
                                progress.incrementSkip();
                                continue;
                            }

                            if (!isSeoulAddress(doc.getAddressName())) {
                                log.trace("    서울시 외 주소 스킵: {} - {}", doc.getPlaceName(), doc.getAddressName());
                                progress.incrementSkip();
                                continue;
                            }

                            // 편의점인지 확인
                            if (!isConvenienceStore(doc.getPlaceName(), doc.getCategoryName())) {
                                log.trace("    편의점 아님 스킵: {} - {}", doc.getPlaceName(), doc.getCategoryName());
                                progress.incrementSkip();
                                continue;
                            }

                            String district = extractDistrictFromAddress(doc.getAddressName());
                            ConvenienceStoreStatistics store = convertToEntity(doc, district);
                            store.setStoreBrand(storeBrand);

                            try {
                                convenienceStoreRepository.save(store);
                                processedIds.add(doc.getId());
                                brandStoreCount++;
                                progress.incrementSuccess();

                                log.trace("    저장완료: {} - {}", store.getPlaceName(), store.getAddressName());

                            } catch (DataIntegrityViolationException e) {
                                String errorMsg = String.format("데이터 무결성 위반: %s", e.getMessage());
                                progress.addError(new CollectionProgress.ErrorDetail(
                                        "DATA_INTEGRITY_ERROR", errorMsg,
                                        String.format("브랜드검색-%s-페이지%d", storeBrand, page),
                                        doc.getPlaceName(), doc.getId(), storeBrand
                                ));
                                log.debug("    데이터 무결성 위반 스킵: {} - {}", doc.getPlaceName(), e.getMessage());

                            } catch (Exception e) {
                                String errorMsg = String.format("DB 저장 오류: %s", e.getMessage());
                                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                        "DATABASE_ERROR", errorMsg,
                                        String.format("브랜드검색-%s-페이지%d", storeBrand, page),
                                        doc.getPlaceName(), doc.getId(), storeBrand
                                );
                                error.setStackTrace(getStackTrace(e));
                                progress.addError(error);

                                log.warn("    DB 저장 실패: {} - {}", doc.getPlaceName(), e.getMessage());
                            }

                        } catch (Exception e) {
                            String errorMsg = String.format("데이터 처리 오류: %s", e.getMessage());
                            CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                                    "DATA_PROCESSING_ERROR", errorMsg,
                                    String.format("브랜드검색-%s-페이지%d", storeBrand, page),
                                    doc.getPlaceName(), doc.getId(), storeBrand
                            );
                            error.setStackTrace(getStackTrace(e));
                            progress.addError(error);

                            log.error("    데이터 처리 실패: {} - {}", doc.getPlaceName(), e.getMessage(), e);
                        }
                    }

                    if (response.getMeta().isEnd()) {
                        log.debug("  마지막 페이지 도달 - 검색 완료");
                        break;
                    }

                    sleepDelay(requestDelay);
                }

            } catch (Exception e) {
                String errorMsg = String.format("브랜드 검색 전체 실패: %s", e.getMessage());
                CollectionProgress.ErrorDetail error = new CollectionProgress.ErrorDetail(
                        "BRAND_SEARCH_ERROR", errorMsg,
                        String.format("브랜드검색-%s-전체", storeBrand),
                        null, null, storeBrand
                );
                error.setStackTrace(getStackTrace(e));
                progress.addError(error);

                log.error("{} 브랜드 전체 검색 실패", storeBrand, e);
                hasError = true;
            }

            String statusIcon = hasError ? "경고" : "완료";
            log.info("  {} {} 완료: {} 개 편의점, {} 페이지 검색", statusIcon, storeBrand, brandStoreCount, pageCount);
        }

        log.info("브랜드별 검색 완료 | {}", progress.getProgressStatus());
    }

    /**
     * 일반 키워드로 편의점 검색 (누락된 편의점 보완)
     */
    private void collectConvenienceStoresByKeyword(Set<String> processedIds) {
        log.info("일반 키워드 편의점 검색 시작 ({} 개 키워드)", CONVENIENCE_STORE_KEYWORDS.size());

        BigDecimal seoulCenterX = new BigDecimal("126.9780");
        BigDecimal seoulCenterY = new BigDecimal("37.5665");

        int keywordIndex = 0;
        for (String keyword : CONVENIENCE_STORE_KEYWORDS) {
            keywordIndex++;
            progress.setCurrentDistrict(keyword);
            progress.setCurrentPage(0);

            log.info("[{}/{}] '{}' 키워드 검색 중...", keywordIndex, CONVENIENCE_STORE_KEYWORDS.size(), keyword);

            String query = keyword + " 서울";
            int pageCount = 0;
            int keywordStoreCount = 0;
            boolean hasError = false;

            try {
                for (int page = 1; page <= maxPage; page++) {
                    progress.setCurrentPage(page);
                    pageCount++;

                    log.debug("  페이지 {} 요청 중... (키워드: {})", page, query);

                    KakaoLocalApiResponse response = null;
                    try {
                        response = kakaoApiClient.searchByKeyword(
                                query,
                                seoulCenterX,
                                seoulCenterY,
                                page
                        );
                    } catch (Exception e) {
                        String errorMsg = String.format("API 호출 오류: %s", e.getMessage());
                        progress.addError(new CollectionProgress.ErrorDetail(
                                "API_ERROR", errorMsg,
                                String.format("키워드검색-%s-페이지%d", keyword, page),
                                null, null, keyword
                        ));
                        log.warn("  API 오류: {} - 페이지 {} 스킵", keyword, page);
                        hasError = true;
                        break;
                    }

                    if (response == null || response.getDocuments() == null || response.getDocuments().isEmpty()) {
                        log.debug("  페이지 {} 결과 없음 - 검색 종료", page);
                        break;
                    }

                    log.debug("  페이지 {} 응답: {} 개 결과", page, response.getDocuments().size());

                    for (KakaoLocalApiResponse.Document doc : response.getDocuments()) {
                        try {
                            if (processedIds.contains(doc.getId())) {
                                progress.incrementSkip();
                                continue;
                            }

                            if (!isSeoulAddress(doc.getAddressName()) ||
                                    !isConvenienceStore(doc.getPlaceName(), doc.getCategoryName())) {
                                progress.incrementSkip();
                                continue;
                            }

                            String district = extractDistrictFromAddress(doc.getAddressName());
                            ConvenienceStoreStatistics store = convertToEntity(doc, district);

                            try {
                                convenienceStoreRepository.save(store);
                                processedIds.add(doc.getId());
                                keywordStoreCount++;
                                progress.incrementSuccess();

                                log.trace("    저장완료: {} - {}", store.getPlaceName(), store.getAddressName());

                            } catch (DataIntegrityViolationException e) {
                                log.debug("    중복 데이터 스킵: {}", doc.getPlaceName());
                            } catch (Exception e) {
                                log.warn("    DB 저장 실패: {} - {}", doc.getPlaceName(), e.getMessage());
                            }

                        } catch (Exception e) {
                            log.error("    데이터 처리 실패: {} - {}", doc.getPlaceName(), e.getMessage());
                        }
                    }

                    if (response.getMeta().isEnd()) {
                        break;
                    }

                    sleepDelay(requestDelay);
                }

            } catch (Exception e) {
                log.error("키워드 '{}' 전체 검색 실패", keyword, e);
                hasError = true;
            }

            String statusIcon = hasError ? "경고" : "완료";
            log.info("  {} '{}' 완료: {} 개 편의점, {} 페이지 검색", statusIcon, keyword, keywordStoreCount, pageCount);
        }

        log.info("키워드 검색 완료 | {}", progress.getProgressStatus());
    }

    /**
     * 최종 수집 결과 요약 출력
     */
    private void printFinalSummary() {
        Duration duration = Duration.between(progress.getStartTime(), progress.getEndTime());
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;

        log.info("=================== 편의점 데이터 수집 완료 ===================");
        log.info("수집 통계:");
        log.info("   시작 시간: {}", progress.getStartTime());
        log.info("   종료 시간: {}", progress.getEndTime());
        log.info("   소요 시간: {}분 {}초", minutes, seconds);
        log.info("   이 처리: {} 개", progress.getTotalProcessed().get());
        log.info("   성공 저장: {} 개", progress.getSuccessCount().get());
        log.info("   중복 스킵: {} 개", progress.getSkipCount().get());
        log.info("   오류 발생: {} 개", progress.getErrorCount().get());

        if (progress.getErrorCount().get() > 0) {
            log.info("오류 유형별 통계:");
            Map<String, Long> errorStats = progress.getErrors().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            CollectionProgress.ErrorDetail::getErrorType,
                            java.util.stream.Collectors.counting()
                    ));

            errorStats.forEach((type, count) ->
                    log.info("   {}: {} 건", type, count));

            log.info("상세 오류 내역:");
            progress.getErrors().stream()
                    .limit(10)
                    .forEach(error -> log.warn("   [{}] {} - {} ({})",
                            error.getErrorType(),
                            error.getErrorMessage(),
                            error.getPlaceName() != null ? error.getPlaceName() : "N/A",
                            error.getContext()));

            if (progress.getErrors().size() > 10) {
                log.info("   ... 외 {} 건의 오류가 더 발생했습니다.", progress.getErrors().size() - 10);
            }
        }

        try {
            long finalCount = convenienceStoreRepository.count();
            log.info("DB 저장 확인: {} 개 편의점 데이터", finalCount);

            List<Object[]> districtStats = convenienceStoreRepository.countStoresByDistrict();
            log.info("구별 분포 상위 5개:");
            districtStats.stream()
                    .limit(5)
                    .forEach(stat -> log.info("   {}: {} 개", stat[0], stat[1]));

            List<Object[]> brandStats = convenienceStoreRepository.countStoresByBrand();
            log.info("브랜드별 분포 상위 5개:");
            brandStats.stream()
                    .limit(5)
                    .forEach(stat -> log.info("   {}: {} 개", stat[0], stat[1]));

        } catch (Exception e) {
            log.error("DB 최종 확인 실패", e);
        }

        log.info("==========================================================");
    }

    /**
     * 카카오 응답 데이터를 Entity로 변환
     */
    private ConvenienceStoreStatistics convertToEntity(KakaoLocalApiResponse.Document doc, String district) {
        try {
            return ConvenienceStoreStatistics.builder()
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
                    .storeBrand(extractStoreBrandFromName(doc.getPlaceName()))
                    .is24Hours(detect24Hours(doc.getPlaceName()) ? "Y" : "N")
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
     * 편의점인지 확인 (상호명과 카테고리명 기준)
     */
    private boolean isConvenienceStore(String placeName, String categoryName) {
        if (placeName == null && categoryName == null) return false;

        // 편의점 관련 키워드 확인
        String[] convenienceKeywords = {"편의점", "GS25", "CU", "세븐일레븐", "7-Eleven", "이마트24", "미니스톱"};

        for (String keyword : convenienceKeywords) {
            if ((placeName != null && placeName.contains(keyword)) ||
                    (categoryName != null && categoryName.contains(keyword))) {
                return true;
            }
        }

        return false;
    }

    /**
     * 24시간 운영 여부 감지 (상호명 기준)
     */
    private boolean detect24Hours(String placeName) {
        if (placeName == null) return false;

        String[] hour24Keywords = {"24", "24시", "24시간", "24H", "24hour"};

        for (String keyword : hour24Keywords) {
            if (placeName.contains(keyword)) {
                return true;
            }
        }

        return false;
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
     * 상호명에서 편의점 브랜드 추출
     */
    private String extractStoreBrandFromName(String placeName) {
        if (placeName == null) return null;

        for (String brand : MAJOR_CONVENIENCE_BRANDS) {
            if (placeName.contains(brand)) {
                return brand;
            }
        }

        // 추가 브랜드 패턴 확인
        if (placeName.contains("세븐일레븐") || placeName.contains("7-Eleven")) {
            return "세븐일레븐";
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
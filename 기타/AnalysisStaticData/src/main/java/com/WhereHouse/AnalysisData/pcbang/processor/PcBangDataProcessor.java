package com.WhereHouse.AnalysisData.pcbang.processor;

import com.WhereHouse.AnalysisData.pcbang.client.KakaoAddressApiClient;
import com.WhereHouse.AnalysisData.pcbang.entity.AnalysisPcBangStatistics;
import com.WhereHouse.AnalysisData.pcbang.repository.AnalysisPcBangRepository;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.PcRoom.Entity.PcBangs;
import com.WhereHouse.AnalysisStaticData.PcRoom.Repository.PcBangsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * PC방 위치 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 PC_BANGS 테이블에서 7개 핵심 필드를 선별하고
 * 카카오맵 API를 통해 주소를 위도/경도 좌표로 변환하여
 * 분석 전용 ANALYSIS_PC_BANG_STATISTICS 테이블에 9개 필드로 저장한다.
 *
 * 주요 기능:
 * - 원본 PC방 데이터 조회 및 검증
 * - 7개 핵심 필드 선별 및 카카오맵 API 좌표 변환
 * - API 호출 제한 고려한 속도 조절
 * - 분석용 테이블 데이터 품질 검증
 * - 구별 PC방 밀도 및 영업상태 분포 순위 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PcBangDataProcessor {

    // 원본 PC방 테이블 접근을 위한 Repository
    private final PcBangsRepository originalPcBangRepository;

    // 분석용 PC방 테이블 접근을 위한 Repository
    private final AnalysisPcBangRepository analysisPcBangRepository;

    // 카카오맵 API 클라이언트
    private final KakaoAddressApiClient kakaoAddressApiClient;

    // API 호출 간격 설정
    @Value("${kakao.local-api.request-delay}")
    private long requestDelay;

    /**
     * PC방 위치 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 PC방 데이터 조회 및 검증
     * 3. 7개 핵심 필드 선별 및 카카오맵 API 좌표 변환
     * 4. 분석용 테이블 저장
     * 5. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisPcBangData() {
        log.info("=== PC방 위치 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisPcBangRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 PC방 위치 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 PC방 데이터 조회 및 검증
        List<PcBangs> originalPcBangDataList = originalPcBangRepository.findAll();
        if (originalPcBangDataList.isEmpty()) {
            log.warn("원본 PC방 데이터가 존재하지 않습니다. 먼저 PcBangsDataLoader를 통해 CSV 데이터를 수집해주세요.");
            return;
        }

        log.info("원본 PC방 데이터 {} 개 업소 발견", originalPcBangDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;  // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;      // 변환 실패한 데이터 개수
        int apiSuccessCount = 0;            // API 좌표 변환 성공 개수
        int apiFailedCount = 0;             // API 좌표 변환 실패 개수

        for (int i = 0; i < originalPcBangDataList.size(); i++) {
            PcBangs originalPcBangData = originalPcBangDataList.get(i);

            // 진행률 로깅 (100건마다)
            if (i > 0 && i % 100 == 0) {
                double progress = ((double) i / originalPcBangDataList.size()) * 100;
                log.info("처리 진행률: {:.1f}% ({}/{}) - API 성공: {}, 실패: {}",
                        progress, i, originalPcBangDataList.size(), apiSuccessCount, apiFailedCount);
            }

            try {
                // 원본 데이터에서 7개 핵심 필드 선별 및 분석용 엔티티로 변환
                AnalysisPcBangStatistics analysisTargetPcBangData = convertToAnalysisEntity(originalPcBangData);

                // 카카오맵 API를 통한 좌표 변환 시도
                AddressInfo addressInfo = determineAndCleanAddress(originalPcBangData);

                if (addressInfo.getCleanedAddress() != null) {
                    try {
                        log.debug("주소 정제 완료: [{}] {} → {}",
                                addressInfo.getAddressType(),
                                addressInfo.getCleanedAddress().length() > 50 ?
                                        addressInfo.getCleanedAddress().substring(0, 50) + "..." :
                                        addressInfo.getCleanedAddress(),
                                addressInfo.getCleanedAddress());

                        // 여러 형태로 주소 검색 시도 (원본 PC방 데이터도 전달)
                        KakaoAddressApiClient.AddressSearchResponse response =
                                tryMultipleAddressFormats(addressInfo.getCleanedAddress(), originalPcBangData);

                        if (response.getDocuments() != null && !response.getDocuments().isEmpty()) {
                            KakaoAddressApiClient.Document document = response.getDocuments().get(0);
                            analysisTargetPcBangData.setLatitude(new BigDecimal(document.getLatitude()));
                            analysisTargetPcBangData.setLongitude(new BigDecimal(document.getLongitude()));
                            analysisTargetPcBangData.setGeocodingStatus(
                                    "좌표변환 성공(" + addressInfo.getAddressType() + ")");
                            apiSuccessCount++;

                            log.debug("좌표 변환 성공: {} → lat: {}, lng: {}",
                                    addressInfo.getCleanedAddress(), document.getLatitude(), document.getLongitude());
                        } else {
                            analysisTargetPcBangData.setGeocodingStatus(
                                    "좌표변환 실패: 모든 주소 형태 시도했으나 결과 없음(" + addressInfo.getAddressType() + ")");
                            log.debug("좌표 변환 실패 (모든 형태 시도 실패): {}", addressInfo.getCleanedAddress());
                            apiFailedCount++;
                        }

                        // API 호출 제한 준수를 위한 추가 대기 (여러 번 호출했으므로)
                        Thread.sleep(requestDelay);

                    } catch (Exception apiException) {
                        analysisTargetPcBangData.setGeocodingStatus(
                                "좌표변환 실패: API 오류 - " + apiException.getMessage());
                        log.warn("카카오맵 API 호출 실패: {} - {}", addressInfo.getCleanedAddress(), apiException.getMessage());
                        apiFailedCount++;

                        // API 에러 시 더 긴 대기
                        Thread.sleep(requestDelay * 5);
                    }
                } else {
                    // 주소 정제 실패 또는 주소 없음
                    analysisTargetPcBangData.setGeocodingStatus(addressInfo.getStatusMessage());
                    log.debug("주소 사용 불가: {}", addressInfo.getStatusMessage());
                    apiFailedCount++;
                }

                // 분석용 테이블에 데이터 저장
                analysisPcBangRepository.save(analysisTargetPcBangData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} (구코드: {}, 좌표: {}, {})",
                        originalPcBangData.getBusinessName(),
                        originalPcBangData.getDistrictCode(),
                        analysisTargetPcBangData.getLatitude(),
                        analysisTargetPcBangData.getLongitude());

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - PC방: {} (ID: {}), 오류: {}",
                        originalPcBangData.getBusinessName(), originalPcBangData.getId(),
                        dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("PC방 위치 데이터 분석용 테이블 생성 작업 완료");
        log.info("  - 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("  - 좌표 변환: 성공 {} 개, 실패 {} 개", apiSuccessCount, apiFailedCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== PC방 위치 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 PC방 엔티티를 분석용 엔티티로 변환
     *
     * 7개 핵심 필드를 복사하고, 좌표는 초기값으로 설정한다. (API 호출로 나중에 업데이트)
     * null 값은 적절한 기본값으로 변환 처리한다.
     *
     * @param originalPcBangData 원본 PC방 엔티티
     * @return 분석용 PC방 엔티티
     */
    private AnalysisPcBangStatistics convertToAnalysisEntity(PcBangs originalPcBangData) {
        return AnalysisPcBangStatistics.builder()
                // 기본 정보
                .districtCode(handleNullString(originalPcBangData.getDistrictCode()))           // 구 코드
                .managementNumber(handleNullString(originalPcBangData.getManagementNumber()))   // 관리번호
                .businessStatusName(handleNullString(originalPcBangData.getBusinessStatusName())) // 영업상태명

                // 주소 정보
                .jibunAddress(handleNullString(originalPcBangData.getJibunAddress()))           // 지번주소
                .roadAddress(handleNullString(originalPcBangData.getRoadAddress()))             // 도로명주소

                // 업소 정보
                .businessName(handleNullString(originalPcBangData.getBusinessName()))           // 업소명

                // 좌표 정보 (초기값, API 호출로 업데이트 예정)
                .latitude(BigDecimal.ZERO)                                                      // 위도 (카카오맵 API)
                .longitude(BigDecimal.ZERO)                                                     // 경도 (카카오맵 API)

                // 좌표변환 상태 (초기값, API 호출 결과로 업데이트 예정)
                .geocodingStatus("좌표변환 대기중")                                                 // 좌표변환 상태 메시지
                .build();
    }

    /**
     * 카카오맵 API 호출에 사용할 주소 정제 및 결정
     * 1순위: 도로명주소 → 성공 시 바로 반환, 실패 시 2순위 시도
     * 2순위: 지번주소 → 도로명주소 실패한 경우에만 시도
     * 주소에서 괄호, 층수, 호수 등 API가 처리 못하는 부분 제거
     *
     * @param originalPcBangData 원본 PC방 데이터
     * @return AddressInfo 객체 (정제된 주소, 사용된 주소 타입, 상태 메시지)
     */
    private AddressInfo determineAndCleanAddress(PcBangs originalPcBangData) {
        String roadAddress = originalPcBangData.getRoadAddress();
        String jibunAddress = originalPcBangData.getJibunAddress();

        // 1순위: 도로명주소 체크 및 정제
        if (roadAddress != null && !roadAddress.trim().isEmpty() && !roadAddress.equals("데이터없음")) {
            String cleanedAddress = cleanAddressForAPI(roadAddress);
            if (cleanedAddress != null) {
                return new AddressInfo(cleanedAddress, "도로명주소", "도로명주소 사용");
            }
        }

        // 2순위: 지번주소 체크 및 정제 (도로명주소가 없거나 정제 실패한 경우에만)
        if (jibunAddress != null && !jibunAddress.trim().isEmpty() && !jibunAddress.equals("데이터없음")) {
            String cleanedAddress = cleanAddressForAPI(jibunAddress);
            if (cleanedAddress != null) {
                return new AddressInfo(cleanedAddress, "지번주소", "지번주소 사용(도로명주소 없음 또는 정제 불가)");
            }
        }

        // 둘 다 사용 불가
        return new AddressInfo(null, "없음", "도로명주소/지번주소 모두 없음 또는 정제 불가");
    }

    /**
     * 카카오맵 API가 처리할 수 있도록 주소 정제
     * - 괄호 및 괄호 안 내용 제거: (건물명), (층수), (호수) 등
     * - 층/호 정보 제거: 1층, 2호, B1층 등
     * - 기타 불필요한 정보 제거
     * - 여러 형태로 변환하여 API 호출 시도
     *
     * @param rawAddress 원본 주소
     * @return 정제된 주소 (null이면 사용 불가)
     */
    private String cleanAddressForAPI(String rawAddress) {
        if (rawAddress == null || rawAddress.trim().isEmpty()) {
            return null;
        }

        String cleaned = rawAddress.trim();

        // 괄호 및 괄호 안 내용 제거
        cleaned = cleaned.replaceAll("\\([^\\)]*\\)", "");
        cleaned = cleaned.replaceAll("\\)[^\\(]*", ""); // 남은 ) 제거

        // 층/호 정보 제거 (숫자+층, 숫자+호, B+숫자+층 등)
        cleaned = cleaned.replaceAll("\\s*[B0-9]+층\\s*", " ");
        cleaned = cleaned.replaceAll("\\s*[0-9]+호\\s*", " ");
        cleaned = cleaned.replaceAll("\\s*[B0-9]+F\\s*", " ");

        // 기타 상세 위치 정보 제거
        cleaned = cleaned.replaceAll("\\s*지하\\s*", " ");
        cleaned = cleaned.replaceAll("\\s*옥상\\s*", " ");
        cleaned = cleaned.replaceAll("\\s*,\\s*", " ");

        // "서울특별시" → "서울"로 단순화
        cleaned = cleaned.replace("서울특별시", "서울");

        // 다중 공백을 단일 공백으로 변환
        cleaned = cleaned.replaceAll("\\s+", " ");
        cleaned = cleaned.trim();

        // 최소 길이 체크 (너무 짧으면 사용 불가)
        if (cleaned.length() < 5) {
            return null;
        }

        return cleaned;
    }

    /**
     * 주소를 여러 형태로 변환하여 API 호출 시도
     * 1차: 정제된 전체 주소 (도로명주소 우선)
     * 2차: 더 단순한 형태 (시/도 제거)
     * 3차: 도로명만 추출
     * 4차: 지번주소로 재시도 (도로명 검색이 모두 실패한 경우)
     */
    private KakaoAddressApiClient.AddressSearchResponse tryMultipleAddressFormats(String baseAddress, PcBangs originalData) {
        // 1차 시도: 정제된 전체 주소
        log.debug("1차 주소 검색 시도: {}", baseAddress);
        KakaoAddressApiClient.AddressSearchResponse response = kakaoAddressApiClient.searchAddress(baseAddress);

        if (response.getDocuments() != null && !response.getDocuments().isEmpty()) {
            log.debug("1차 시도 성공: {} 개 결과", response.getDocuments().size());
            return response;
        }

        // API 호출 간격
        try { Thread.sleep(requestDelay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // 2차 시도: "서울" 제거한 형태
        String simplified = baseAddress.replace("서울", "").trim();
        if (!simplified.equals(baseAddress) && simplified.length() >= 5) {
            log.debug("2차 주소 검색 시도: {}", simplified);
            response = kakaoAddressApiClient.searchAddress(simplified);

            if (response.getDocuments() != null && !response.getDocuments().isEmpty()) {
                log.debug("2차 시도 성공: {} 개 결과", response.getDocuments().size());
                return response;
            }

            try { Thread.sleep(requestDelay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // 3차 시도: 도로명과 번호만 추출
        String roadOnly = extractRoadNameAndNumber(baseAddress);
        if (roadOnly != null && !roadOnly.equals(baseAddress) && !roadOnly.equals(simplified)) {
            log.debug("3차 주소 검색 시도 (도로명만): {}", roadOnly);
            response = kakaoAddressApiClient.searchAddress(roadOnly);

            if (response.getDocuments() != null && !response.getDocuments().isEmpty()) {
                log.debug("3차 시도 성공: {} 개 결과", response.getDocuments().size());
                return response;
            }

            try { Thread.sleep(requestDelay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // 4차 시도: 지번주소로 재시도 (모든 도로명 시도가 실패한 경우)
        String jibunAddress = originalData.getJibunAddress();
        if (jibunAddress != null && !jibunAddress.trim().isEmpty() && !jibunAddress.equals("데이터없음")) {
            String cleanedJibun = cleanAddressForAPI(jibunAddress);
            if (cleanedJibun != null && !cleanedJibun.equals(baseAddress)) {
                log.debug("4차 주소 검색 시도 (지번주소): {}", cleanedJibun);
                response = kakaoAddressApiClient.searchAddress(cleanedJibun);

                if (response.getDocuments() != null && !response.getDocuments().isEmpty()) {
                    log.debug("4차 시도 성공 (지번주소): {} 개 결과", response.getDocuments().size());
                    return response;
                }

                try { Thread.sleep(requestDelay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

                // 5차 시도: 지번주소에서 "서울" 제거
                String simplifiedJibun = cleanedJibun.replace("서울", "").trim();
                if (!simplifiedJibun.equals(cleanedJibun) && simplifiedJibun.length() >= 5) {
                    log.debug("5차 주소 검색 시도 (지번주소 단순화): {}", simplifiedJibun);
                    response = kakaoAddressApiClient.searchAddress(simplifiedJibun);

                    if (response.getDocuments() != null && !response.getDocuments().isEmpty()) {
                        log.debug("5차 시도 성공 (지번주소 단순화): {} 개 결과", response.getDocuments().size());
                        return response;
                    }

                    try { Thread.sleep(requestDelay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }

                // 6차 시도: 지번주소에서 동과 번지만 추출
                String jibunOnly = extractJibunDongAndNumber(cleanedJibun);
                if (jibunOnly != null && !jibunOnly.equals(cleanedJibun) && !jibunOnly.equals(simplifiedJibun)) {
                    log.debug("6차 주소 검색 시도 (지번 동+번지만): {}", jibunOnly);
                    response = kakaoAddressApiClient.searchAddress(jibunOnly);

                    if (response.getDocuments() != null && !response.getDocuments().isEmpty()) {
                        log.debug("6차 시도 성공 (지번 동+번지만): {} 개 결과", response.getDocuments().size());
                        return response;
                    }
                }
            }
        }

        log.debug("모든 주소 형태 시도 실패");
        return new KakaoAddressApiClient.AddressSearchResponse();
    }

    /**
     * 도로명과 번호만 추출 (예: "종로구 율곡로 245" → "율곡로 245")
     */
    private String extractRoadNameAndNumber(String address) {
        // "구 도로명 번호" 패턴 추출
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(".*구\\s+([가-힣\\w]+로\\d*[가-길]*\\s+\\d+[\\-\\d]*)");
        java.util.regex.Matcher matcher = pattern.matcher(address);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    /**
     * 지번주소에서 동과 번지만 추출 (예: "종로구 창신동 15-23" → "창신동 15-23")
     */
    private String extractJibunDongAndNumber(String address) {
        // "구 동명 번지" 패턴 추출
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(".*구\\s+([가-힣]+동\\s+\\d+[\\-\\d]*)");
        java.util.regex.Matcher matcher = pattern.matcher(address);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return null;
    }

    /**
     * 주소 정보를 담는 내부 클래스
     */
    private static class AddressInfo {
        private final String cleanedAddress;
        private final String addressType;
        private final String statusMessage;

        public AddressInfo(String cleanedAddress, String addressType, String statusMessage) {
            this.cleanedAddress = cleanedAddress;
            this.addressType = addressType;
            this.statusMessage = statusMessage;
        }

        public String getCleanedAddress() { return cleanedAddress; }
        public String getAddressType() { return addressType; }
        public String getStatusMessage() { return statusMessage; }
    }

    /**
     * 문자열 null 값 처리 - null이면 "데이터없음"으로 변환
     *
     * @param value 원본 문자열 값
     * @return null이면 "데이터없음", 아니면 원본 값
     */
    private String handleNullString(String value) {
        return value != null ? value : "데이터없음";
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 구별 PC방 밀도 순위 상위 5개 로깅
     * - 영업상태별 분포 로깅
     * - 좌표 변환 성공률 확인
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisPcBangRepository.count();
            log.info("최종 분석용 PC방 데이터 저장 완료: {} 개 업소", finalAnalysisDataCount);

            // 구별 PC방 밀도 순위 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> districtPcBangDensityRankingList = analysisPcBangRepository.findDistrictPcBangDensityRanking();
            log.info("구코드별 PC방 밀도 순위 (상위 5개):");

            districtPcBangDensityRankingList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String districtCode = (String) rankingRow[0];         // 구 코드
                        Long totalPcBangCount = (Long) rankingRow[1];         // 총 PC방 개수
                        log.info("  {} : {} 개 업소", districtCode, totalPcBangCount);
                    });

            // 서울시 구별 PC방 밀도 순위 조회 및 로깅
            List<Object[]> seoulDistrictPcBangDensityRankingList = analysisPcBangRepository.findSeoulDistrictPcBangDensityRanking();
            log.info("서울시 구별 PC방 밀도 순위 (상위 5개구):");

            seoulDistrictPcBangDensityRankingList.stream()
                    .limit(5)
                    .forEach(districtRow -> {
                        String districtName = (String) districtRow[0];        // 구 이름
                        Number pcBangCount = (Number) districtRow[1];         // PC방 개수
                        log.info("  {} : {} 개 업소", districtName, pcBangCount);
                    });

            // 영업상태별 분포 조회 및 로깅
            List<Object[]> businessStatusDistributionList = analysisPcBangRepository.findBusinessStatusDistribution();
            log.info("영업상태별 PC방 분포:");

            businessStatusDistributionList.forEach(statusRow -> {
                String statusName = (String) statusRow[0];                // 영업상태명
                Long statusCount = (Long) statusRow[1];                   // 개수
                log.info("  {} : {} 개 업소", statusName, statusCount);
            });

            // 좌표 변환 성공률 확인
            long totalCount = analysisPcBangRepository.count();
            long successfulGeocodedCount = analysisPcBangRepository.countSuccessfulGeocodedData();

            log.info("좌표 변환 결과 요약:");
            log.info("  - 전체 데이터: {} 개", totalCount);
            log.info("  - 좌표 변환 성공: {} 개", successfulGeocodedCount);
            log.info("  - 좌표 변환 실패: {} 개", totalCount - successfulGeocodedCount);
            log.info("  - 성공률: {:.1f}%", totalCount > 0 ? (double)successfulGeocodedCount / totalCount * 100 : 0.0);

            // 좌표변환 상태별 분포 조회 및 로깅
            List<Object[]> geocodingStatusDistributionList = analysisPcBangRepository.findGeocodingStatusDistribution();
            log.info("좌표변환 상태별 분포:");

            geocodingStatusDistributionList.forEach(statusRow -> {
                String geocodingStatus = (String) statusRow[0];           // 좌표변환 상태
                Long statusCount = (Long) statusRow[1];                   // 개수
                log.info("  {} : {} 개 업소", geocodingStatus, statusCount);
            });

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}
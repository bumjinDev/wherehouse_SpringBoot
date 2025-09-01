package com.WhereHouse.AnalysisData.streetlight.processor;

import com.WhereHouse.AnalysisData.streetlight.entity.AnalysisStreetlightStatistics;
import com.WhereHouse.AnalysisData.streetlight.repository.AnalysisStreetlightRepository;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.StreetLightRaw.Entity.StreetlightRawData;
import com.WhereHouse.AnalysisStaticData.StreetLightRaw.Repository.StreetlightRawDataRepository;
// KakaoMap API 클라이언트 import
import com.WhereHouse.AnalysisData.streetlight.client.KakaoAddressApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 가로등 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 가로등 원본 테이블에서 데이터를 조회하여
 * KakaoMap API로 주소 정보를 보강한 후
 * 분석 전용 ANALYSIS_STREETLIGHT_STATISTICS 테이블로 저장하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 가로등 데이터 조회 및 검증
 * - KakaoMap API 역지오코딩을 통한 주소 정보 획득
 * - 분석용 테이블 데이터 품질 검증
 * - 구별 가로등 개수 통계 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StreetlightDataProcessor {

    // 원본 가로등 테이블 접근을 위한 Repository
    private final StreetlightRawDataRepository originalStreetlightRepository;

    // 분석용 가로등 테이블 접근을 위한 Repository
    private final AnalysisStreetlightRepository analysisStreetlightRepository;

    // KakaoMap API 클라이언트
    private final KakaoAddressApiClient kakaoAddressApiClient;

    /**
     * 가로등 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 가로등 데이터 조회 및 검증
     * 3. KakaoMap API 역지오코딩으로 주소 정보 획득
     * 4. 데이터 변환 및 분석용 테이블 저장
     * 5. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisStreetlightData() {
        log.info("=== 가로등 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisStreetlightRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 가로등 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 가로등 데이터 조회 및 검증
        List<StreetlightRawData> originalStreetlightDataList = originalStreetlightRepository.findAll();
        if (originalStreetlightDataList.isEmpty()) {
            log.warn("원본 가로등 데이터가 존재하지 않습니다. 먼저 StreetlightRawDataLoader를 통해 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 가로등 데이터 {} 개 발견", originalStreetlightDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;  // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;      // 변환 실패한 데이터 개수
        int apiCallCount = 0;               // API 호출 횟수

        for (StreetlightRawData originalStreetlightData : originalStreetlightDataList) {
            try {
                // 진행률 출력 (100개마다)
                if ((successfulConversionCount + failedConversionCount) % 100 == 0) {
                    double progress = ((double)(successfulConversionCount + failedConversionCount) / originalStreetlightDataList.size()) * 100;
                    log.info("진행률: {:.1f}% ({}/{})", progress,
                            successfulConversionCount + failedConversionCount, originalStreetlightDataList.size());
                }

                // 원본 데이터를 분석용 엔티티로 변환 (KakaoMap API 호출 포함)
                AnalysisStreetlightStatistics analysisTargetStreetlightData = convertToAnalysisEntity(originalStreetlightData);
                apiCallCount++;

                // 분석용 테이블에 데이터 저장
                analysisStreetlightRepository.save(analysisTargetStreetlightData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} (구: {}, 동: {})",
                        originalStreetlightData.getManagementNumber(),
                        analysisTargetStreetlightData.getDistrictName(),
                        analysisTargetStreetlightData.getDongName());

                // API 호출 제한 대응 (100ms 대기)
                Thread.sleep(100);

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 관리번호: {}, 오류: {}",
                        originalStreetlightData.getManagementNumber(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("가로등 데이터 분석용 테이블 생성 작업 완료 - 성공: {} 개, 실패: {} 개, API 호출: {} 회",
                successfulConversionCount, failedConversionCount, apiCallCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 가로등 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 가로등 데이터를 KakaoMap API로 주소 정보를 보강하여 분석용 엔티티로 변환
     *
     * @param originalStreetlightData 원본 가로등 데이터 엔티티
     * @return 주소 정보가 보강된 분석용 가로등 엔티티
     * @throws Exception KakaoMap API 호출 실패 시
     */
    private AnalysisStreetlightStatistics convertToAnalysisEntity(StreetlightRawData originalStreetlightData) throws Exception {

        // 기본 정보 설정
        String managementNumber = originalStreetlightData.getManagementNumber();
        Double latitude = originalStreetlightData.getLatitude());
        Double longitude = originalStreetlightData.getLongitude());

        // 주소 정보 초기화
        String districtName = "주소정보없음";
        String dongName = "주소정보없음";
        String roadAddress = "주소정보없음";
        String jibunAddress = "주소정보없음";

        // KakaoMap API 역지오코딩 호출하여 주소 정보 획득
        try {
            if (latitude != null && longitude != null &&
                    latitude != 0 && longitude != 0) {

                // 좌표를 문자열로 변환
                String latitudeStr = latitude.toString();
                String longitudeStr = longitude.toString();

                // KakaoMap API 호출
                KakaoAddressApiClient.CoordinateToAddressResponse response =
                        kakaoAddressApiClient.coordinateToAddress(longitudeStr, latitudeStr);

                if (response.getDocuments() != null && !response.getDocuments().isEmpty()) {
                    KakaoAddressApiClient.CoordinateToAddressResponse.Document document = response.getDocuments().get(0);

                    // 도로명 주소 정보 추출
                    if (document.getRoadAddress() != null) {
                        KakaoAddressApiClient.CoordinateToAddressResponse.RoadAddress roadAddr = document.getRoadAddress();
                        districtName = roadAddr.getRegion2DepthName(); // 구
                        dongName = roadAddr.getRegion3DepthName();     // 동
                        roadAddress = roadAddr.getAddressName();       // 전체 도로명 주소
                    }

                    // 지번 주소 정보 추출 (도로명 주소가 없을 경우 대안)
                    if (document.getAddress() != null) {
                        KakaoAddressApiClient.CoordinateToAddressResponse.Address addr = document.getAddress();
                        if ("주소정보없음".equals(districtName)) {
                            districtName = addr.getRegion2DepthName(); // 구
                            dongName = addr.getRegion3DepthName();     // 동
                        }
                        jibunAddress = addr.getAddressName();         // 전체 지번 주소
                    }
                }
            }
        } catch (Exception apiException) {
            log.warn("KakaoMap API 호출 실패 - 관리번호: {}, 좌표: ({}, {}), 오류: {}",
                    managementNumber, latitude, longitude, apiException.getMessage());
            // API 호출이 실패해도 기본 정보는 저장하도록 처리
        }

        return AnalysisStreetlightStatistics.builder()
                .managementNumber(managementNumber)
                .districtName(districtName)
                .dongName(dongName)
                .roadAddress(roadAddress)
                .jibunAddress(jibunAddress)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 구별 가로등 개수 상위 5개 로깅
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisStreetlightRepository.count();
            log.info("최종 분석용 가로등 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 구별 가로등 개수 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> districtStreetlightCountList = analysisStreetlightRepository.findStreetlightCountByDistrict();
            log.info("서울시 구별 가로등 개수 순위 (상위 5개구):");

            districtStreetlightCountList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String districtName = (String) rankingRow[0];    // 구 이름
                        Long streetlightCount = (Long) rankingRow[1];    // 가로등 개수
                        log.info("  {} : {} 개", districtName, streetlightCount);
                    });

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}
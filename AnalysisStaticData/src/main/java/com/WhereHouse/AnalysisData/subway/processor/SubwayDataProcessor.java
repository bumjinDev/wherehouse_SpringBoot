package com.WhereHouse.AnalysisData.subway.processor;

import com.WhereHouse.AnalysisData.subway.entity.AnalysisSubwayStation;
import com.WhereHouse.AnalysisData.subway.repository.AnalysisSubwayRepository;
import com.WhereHouse.AnalysisData.subway.service.SubwayCoordinateService;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.SubwayStation.Entity.SubwayStation;
import com.WhereHouse.AnalysisStaticData.SubwayStation.Repository.SubwayStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 지하철역 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 SUBWAY_STATION 테이블에서 데이터를 조회하여
 * 분석 전용 ANALYSIS_SUBWAY_STATION 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 지하철역 통계 데이터 조회 및 검증
 * - 지정된 필드만 복사 (역명, 전화번호, 주소 정보)
 * - Kakao Local API를 통한 위도, 경도 좌표 계산 및 추가
 * - 분석용 테이블 데이터 품질 검증
 * - 호선별 및 지역별 분포 로깅
 *
 * @author Safety Analysis System
 * @since 1.1
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubwayDataProcessor {

    // 원본 지하철역 테이블 접근을 위한 Repository
    private final SubwayStationRepository originalSubwayRepository;

    // 분석용 지하철역 테이블 접근을 위한 Repository
    private final AnalysisSubwayRepository analysisSubwayRepository;

    // Kakao API 좌표 계산을 위한 서비스
    private final SubwayCoordinateService coordinateService;

    /**
     * 지하철역 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 지하철역 데이터 조회 및 검증
     * 3. 데이터 변환 및 Kakao API 좌표 계산 후 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisSubwayData() {
        log.info("=== 지하철역 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisSubwayRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 지하철역 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 지하철역 데이터 조회 및 검증
        List<SubwayStation> originalSubwayDataList = originalSubwayRepository.findAll();
        if (originalSubwayDataList.isEmpty()) {
            log.warn("원본 지하철역 데이터가 존재하지 않습니다. 먼저 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 지하철역 데이터 {} 개 발견", originalSubwayDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;      // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;          // 변환 실패한 데이터 개수
        int coordinateCalculationSuccessCount = 0; // 좌표 계산 성공 개수
        int coordinateCalculationFailedCount = 0;  // 좌표 계산 실패 개수

        // 처리 진행률 추적
        int processedCount = 0;
        int totalCount = originalSubwayDataList.size();
        int logInterval = Math.max(1, totalCount / 10); // 10% 간격으로 로그 출력

        for (SubwayStation originalSubwayData : originalSubwayDataList) {
            processedCount++;

            try {
                // 원본 데이터를 분석용 엔티티로 변환 (지정된 필드만)
                AnalysisSubwayStation analysisTargetSubwayData = convertToAnalysisEntity(originalSubwayData);

                // Kakao API를 통한 좌표 계산 및 설정
                Double[] coordinates = calculateCoordinatesForSubway(originalSubwayData);
                if (coordinates != null) {
                    analysisTargetSubwayData.setLatitude(coordinates[0]);
                    analysisTargetSubwayData.setLongitude(coordinates[1]);
                    coordinateCalculationSuccessCount++;
                } else {
                    coordinateCalculationFailedCount++;
                }

                // 분석용 테이블에 데이터 저장
                analysisSubwayRepository.save(analysisTargetSubwayData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} (주소: {}, 좌표: {}, {})",
                        originalSubwayData.getStationNameKor(),
                        originalSubwayData.getRoadAddress(),
                        coordinates != null ? coordinates[0] : "없음",
                        coordinates != null ? coordinates[1] : "없음");

                // 진행률 로그 (10% 간격)
                if (processedCount % logInterval == 0 || processedCount == totalCount) {
                    double progressPercentage = (double) processedCount / totalCount * 100;
                    log.info("진행률: {:.1f}% 완료 ({}/{})", progressPercentage, processedCount, totalCount);
                }

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 역명: {}, 오류: {}",
                        originalSubwayData.getStationNameKor(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("지하철역 데이터 분석용 테이블 생성 작업 완료");
        log.info("- 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("- 좌표 계산: 성공 {} 개, 실패 {} 개", coordinateCalculationSuccessCount, coordinateCalculationFailedCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 지하철역 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 지하철역 엔티티를 분석용 엔티티로 변환
     *
     * 지정된 필드만 복사한다.
     * 좌표 정보는 별도 메서드에서 계산하여 설정한다.
     *
     * @param originalSubwayData 원본 지하철역 엔티티
     * @return 분석용 지하철역 엔티티
     */
    private AnalysisSubwayStation convertToAnalysisEntity(SubwayStation originalSubwayData) {
        return AnalysisSubwayStation.builder()
                // 지정된 4개 필드만 복사
                .stationNameKor(originalSubwayData.getStationNameKor())                 // 역명 (한글)
                .stationPhone(originalSubwayData.getStationPhone())                     // 역전화번호
                .roadAddress(originalSubwayData.getRoadAddress())                       // 도로명주소
                .jibunAddress(originalSubwayData.getJibunAddress())                     // 지번주소

                // 좌표 정보는 별도 설정 (초기값 null)
                .latitude(null)
                .longitude(null)
                .build();
    }

    /**
     * 지하철역 주소 정보 기반 Kakao API 좌표 계산
     *
     * 도로명주소를 우선으로 하고, 없는 경우 지번주소를 활용하여 좌표를 계산한다.
     *
     * @param subwayData 원본 지하철역 데이터
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] calculateCoordinatesForSubway(SubwayStation subwayData) {
        try {
            // 1순위: 도로명주소 기반 좌표 계산
            if (subwayData.getRoadAddress() != null && !subwayData.getRoadAddress().trim().isEmpty()) {
                Double[] coordinates = coordinateService.calculateCoordinatesFromRoadAddress(subwayData.getRoadAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            // 2순위: 지번주소 기반 좌표 계산
            if (subwayData.getJibunAddress() != null && !subwayData.getJibunAddress().trim().isEmpty()) {
                Double[] coordinates = coordinateService.calculateCoordinatesFromAddress(subwayData.getJibunAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            log.debug("좌표 계산 실패 - 역명: {}, 주소 정보 부족", subwayData.getStationNameKor());
            return null;

        } catch (Exception coordinateCalculationException) {
            log.error("좌표 계산 중 오류 발생 - 역명: {}, 오류: {}",
                    subwayData.getStationNameKor(), coordinateCalculationException.getMessage());
            return null;
        }
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 좌표 정보 완성도 확인
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisSubwayRepository.count();
            log.info("최종 분석용 지하철역 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 좌표 정보 완성도 확인
            long coordinateCompleteCount = analysisSubwayRepository.findAllWithCoordinates().size();
            long coordinateMissingCount = analysisSubwayRepository.countMissingCoordinates();

            log.info("좌표 정보 완성도:");
            log.info("  좌표 보유: {} 개 ({:.1f}%)", coordinateCompleteCount,
                    (double) coordinateCompleteCount / finalAnalysisDataCount * 100);
            log.info("  좌표 누락: {} 개 ({:.1f}%)", coordinateMissingCount,
                    (double) coordinateMissingCount / finalAnalysisDataCount * 100);

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}
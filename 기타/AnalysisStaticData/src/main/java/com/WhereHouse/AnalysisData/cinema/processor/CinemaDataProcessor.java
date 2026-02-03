package com.WhereHouse.AnalysisData.cinema.processor;

import com.WhereHouse.AnalysisData.cinema.entity.AnalysisCinemaStatistics;
import com.WhereHouse.AnalysisData.cinema.repository.AnalysisCinemaRepository;
import com.WhereHouse.AnalysisData.cinema.service.CinemaCoordinateService;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.Cinema.Entity.CulturalSportsBusiness;
import com.WhereHouse.AnalysisStaticData.Cinema.Repository.CulturalSportsBusinessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 영화관 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 CULTURAL_SPORTS_BUSINESS 테이블에서 데이터를 조회하여
 * 분석 전용 ANALYSIS_CINEMA_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 문화체육업 통계 데이터 조회 및 검증
 * - 지정된 7개 필드만 복사
 * - Kakao Local API를 통한 위도, 경도 좌표 계산 및 추가
 * - 분석용 테이블 데이터 품질 검증
 * - 영업상태별 및 업종별 분포 로깅
 *
 * @author Safety Analysis System
 * @since 1.1
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CinemaDataProcessor {

    // 원본 문화체육업 통계 테이블 접근을 위한 Repository
    private final CulturalSportsBusinessRepository originalCinemaRepository;

    // 분석용 영화관 통계 테이블 접근을 위한 Repository
    private final AnalysisCinemaRepository analysisCinemaRepository;

    // Kakao API 좌표 계산을 위한 서비스
    private final CinemaCoordinateService coordinateService;

    /**
     * 영화관 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 영화관 데이터 조회 및 검증
     * 3. 데이터 변환 및 Kakao API 좌표 계산 후 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisCinemaData() {
        log.info("=== 영화관 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisCinemaRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 영화관 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 문화체육업 통계 데이터 조회 및 영화관 필터링
        List<CulturalSportsBusiness> originalCinemaDataList = originalCinemaRepository.findAll();

        if (originalCinemaDataList.isEmpty()) {
            log.warn("원본 영화관 데이터가 존재하지 않습니다. 먼저 CinemaDataLoader를 통해 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 영화관 통계 데이터 {} 개 발견", originalCinemaDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;      // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;          // 변환 실패한 데이터 개수
        int coordinateCalculationSuccessCount = 0; // 좌표 계산 성공 개수
        int coordinateCalculationFailedCount = 0;  // 좌표 계산 실패 개수

        // 처리 진행률 추적
        int processedCount = 0;
        int totalCount = originalCinemaDataList.size();
        int logInterval = Math.max(1, totalCount / 10); // 10% 간격으로 로그 출력

        for (CulturalSportsBusiness originalCinemaData : originalCinemaDataList) {
            processedCount++;

            try {
                // 원본 데이터를 분석용 엔티티로 변환 (지정된 7개 필드만)
                AnalysisCinemaStatistics analysisTargetCinemaData = convertToAnalysisEntity(originalCinemaData);

                // Kakao API를 통한 좌표 계산 및 설정
                Double[] coordinates = calculateCoordinatesForCinema(originalCinemaData);
                if (coordinates != null) {
                    analysisTargetCinemaData.setLatitude(coordinates[0]);
                    analysisTargetCinemaData.setLongitude(coordinates[1]);
                    coordinateCalculationSuccessCount++;
                } else {
                    coordinateCalculationFailedCount++;
                }

                // 분석용 테이블에 데이터 저장
                analysisCinemaRepository.save(analysisTargetCinemaData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} (상태: {}, 업종: {}, 좌표: {}, {})",
                        originalCinemaData.getBusinessName(),
                        originalCinemaData.getBusinessStatusName(),
                        originalCinemaData.getCultureSportsTypeName(),
                        coordinates != null ? coordinates[0] : "없음",
                        coordinates != null ? coordinates[1] : "없음");

                // 진행률 로그 (10% 간격)
                if (processedCount % logInterval == 0 || processedCount == totalCount) {
                    double progressPercentage = (double) processedCount / totalCount * 100;
                    log.info("진행률: {:.1f}% 완료 ({}/{})", progressPercentage, processedCount, totalCount);
                }

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 영화관: {}, 오류: {}",
                        originalCinemaData.getBusinessName(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("영화관 데이터 분석용 테이블 생성 작업 완료");
        log.info("- 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("- 좌표 계산: 성공 {} 개, 실패 {} 개", coordinateCalculationSuccessCount, coordinateCalculationFailedCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 영화관 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 문화체육업 엔티티를 분석용 영화관 엔티티로 변환
     *
     * 지정된 7개 필드만 복사한다.
     * 좌표 정보는 별도 메서드에서 계산하여 설정한다.
     *
     * @param originalCinemaData 원본 문화체육업 엔티티
     * @return 분석용 영화관 통계 엔티티
     */
    private AnalysisCinemaStatistics convertToAnalysisEntity(CulturalSportsBusiness originalCinemaData) {
        return AnalysisCinemaStatistics.builder()
                // 지정된 7개 필드만 복사
                .businessStatusName(originalCinemaData.getBusinessStatusName())             // 영업상태명
                .phoneNumber(originalCinemaData.getPhoneNumber())                           // 전화번호
                .jibunAddress(originalCinemaData.getJibunAddress())                         // 지번주소
                .roadAddress(originalCinemaData.getRoadAddress())                           // 도로명주소
                .businessName(originalCinemaData.getBusinessName())                         // 사업장명
                .cultureSportsTypeName(originalCinemaData.getCultureSportsTypeName())       // 문화체육업종명
                .buildingUseName(originalCinemaData.getBuildingUseName())                   // 건물용도명

                // 좌표 정보는 별도 설정 (초기값 null)
                .latitude(null)
                .longitude(null)
                .build();
    }

    /**
     * 영화관 주소 정보 기반 Kakao API 좌표 계산
     *
     * 도로명주소를 우선으로 하고, 없는 경우 지번주소를 활용하여 좌표를 계산한다.
     *
     * @param cinemaData 원본 영화관 데이터
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] calculateCoordinatesForCinema(CulturalSportsBusiness cinemaData) {
        try {
            // 1순위: 도로명주소 기반 좌표 계산
            if (cinemaData.getRoadAddress() != null && !cinemaData.getRoadAddress().trim().isEmpty()) {
                Double[] coordinates = coordinateService.calculateCoordinatesFromRoadAddress(cinemaData.getRoadAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            // 2순위: 지번주소 기반 좌표 계산
            if (cinemaData.getJibunAddress() != null && !cinemaData.getJibunAddress().trim().isEmpty()) {
                Double[] coordinates = coordinateService.calculateCoordinatesFromJibunAddress(cinemaData.getJibunAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            log.debug("좌표 계산 실패 - 영화관: {}, 주소 정보 부족", cinemaData.getBusinessName());
            return null;

        } catch (Exception coordinateCalculationException) {
            log.error("좌표 계산 중 오류 발생 - 영화관: {}, 오류: {}",
                    cinemaData.getBusinessName(), coordinateCalculationException.getMessage());
            return null;
        }
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 영업상태별 분포 상위 5개 로깅
     * - 업종별 분포 로깅
     * - 좌표 정보 완성도 확인
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisCinemaRepository.count();
            log.info("최종 분석용 영화관 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 영업상태별 분포 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> cinemaCountByStatusList = analysisCinemaRepository.findCinemaCountByBusinessStatus();
            log.info("영업상태별 분포 (상위 5개):");

            cinemaCountByStatusList.stream()
                    .limit(5)
                    .forEach(statusRow -> {
                        String businessStatus = (String) statusRow[0];       // 영업상태명
                        Long statusCount = (Long) statusRow[1];              // 해당 상태 수
                        log.info("  {} : {} 개", businessStatus, statusCount);
                    });

            // 업종별 분포 조회 및 로깅
            List<Object[]> cinemaCountByTypeList = analysisCinemaRepository.findCinemaCountByCultureSportsType();
            log.info("업종별 분포:");

            cinemaCountByTypeList.forEach(typeRow -> {
                String cultureSportsType = (String) typeRow[0];         // 업종명
                Long typeCount = (Long) typeRow[1];                      // 해당 업종 수
                log.info("  {} : {} 개", cultureSportsType, typeCount);
            });

            // 좌표 정보 완성도 확인
            long coordinateCompleteCount = analysisCinemaRepository.findAllWithCoordinates().size();
            long coordinateMissingCount = analysisCinemaRepository.countMissingCoordinates();

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
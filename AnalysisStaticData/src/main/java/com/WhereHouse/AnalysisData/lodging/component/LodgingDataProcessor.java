package com.WhereHouse.AnalysisData.lodging.component;

import com.WhereHouse.AnalysisData.lodging.entity.AnalysisLodgingStatistics;
import com.WhereHouse.AnalysisData.lodging.repository.AnalysisLodgingRepository;
import com.WhereHouse.AnalysisData.lodging.service.LodgingCoordinateService;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.Lodgment.Entity.LodgingBusiness;
import com.WhereHouse.AnalysisStaticData.Lodgment.Repository.LodgingBusinessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 숙박업 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 LODGING_BUSINESS 테이블에서 데이터를 조회하여
 * 분석 전용 ANALYSIS_LODGING_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 숙박업 통계 데이터 조회 및 검증
 * - 지정된 9개 필드만 복사
 * - 부정확한 기존 좌표를 무시하고 주소 기반 위도, 경도 재계산
 * - 분석용 테이블 데이터 품질 검증
 * - 영업상태별 및 업종별 분포 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LodgingDataProcessor {

    // 원본 숙박업 테이블 접근을 위한 Repository
    private final LodgingBusinessRepository originalLodgingRepository;

    // 분석용 숙박업 통계 테이블 접근을 위한 Repository
    private final AnalysisLodgingRepository analysisLodgingRepository;

    // 좌표 계산을 위한 서비스
    private final LodgingCoordinateService coordinateService;

    /**
     * 숙박업 데이터 분석용 테이블 생성 메인 프로세스
     */
    @Transactional
    public void processAnalysisLodgingData() {
        log.info("=== 숙박업 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 존재 여부 확인
        long existingAnalysisDataCount = analysisLodgingRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 숙박업 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 숙박업 통계 데이터 조회 및 검증
        List<LodgingBusiness> originalLodgingDataList = originalLodgingRepository.findAll();
        if (originalLodgingDataList.isEmpty()) {
            log.warn("원본 숙박업 통계 데이터가 존재하지 않습니다. 먼저 LodgingDataLoader를 통해 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 숙박업 통계 데이터 {} 개 발견", originalLodgingDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;      // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;          // 변환 실패한 데이터 개수
        int coordinateCalculationSuccessCount = 0; // 좌표 계산 성공 개수
        int coordinateCalculationFailedCount = 0;  // 좌표 계산 실패 개수

        for (LodgingBusiness originalLodgingData : originalLodgingDataList) {
            try {
                // 원본 데이터를 분석용 엔티티로 변환
                AnalysisLodgingStatistics analysisTargetLodgingData = convertToAnalysisEntity(originalLodgingData);

                // 좌표 재계산 및 설정 (기존 COORD_X, COORD_Y 무시)
                Double[] coordinates = recalculateCoordinatesForLodging(originalLodgingData);
                if (coordinates != null) {
                    analysisTargetLodgingData.setLatitude(coordinates[0]);
                    analysisTargetLodgingData.setLongitude(coordinates[1]);
                    coordinateCalculationSuccessCount++;
                } else {
                    coordinateCalculationFailedCount++;
                }

                // 분석용 테이블에 데이터 저장
                analysisLodgingRepository.save(analysisTargetLodgingData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} (상태: {}, 업종: {}, 좌표: {}, {})",
                        originalLodgingData.getBusinessName(),
                        originalLodgingData.getBusinessStatusName(),
                        originalLodgingData.getBusinessTypeName(),
                        coordinates != null ? coordinates[0] : "없음",
                        coordinates != null ? coordinates[1] : "없음");

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 숙박업소: {}, 오류: {}",
                        originalLodgingData.getBusinessName(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("숙박업 데이터 분석용 테이블 생성 작업 완료");
        log.info("- 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("- 좌표 재계산: 성공 {} 개, 실패 {} 개", coordinateCalculationSuccessCount, coordinateCalculationFailedCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 숙박업 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 숙박업 엔티티를 분석용 엔티티로 변환
     */
    private AnalysisLodgingStatistics convertToAnalysisEntity(LodgingBusiness originalLodgingData) {
        return AnalysisLodgingStatistics.builder()
                // 지정된 9개 필드만 복사
                .buildingOwnershipType(originalLodgingData.getBuildingOwnershipType())       // 건물소유구분
                .businessName(originalLodgingData.getBusinessName())                         // 사업장명
                .businessStatusName(originalLodgingData.getBusinessStatusName())             // 영업상태명
                .businessTypeName(originalLodgingData.getBusinessTypeName())                 // 업태구분명
                .detailStatusName(originalLodgingData.getDetailStatusName())                 // 상세상태명
                .fullAddress(originalLodgingData.getFullAddress())                           // 주소
                .hygieneBusinessType(originalLodgingData.getHygieneBusinessType())           // 위생업종
                .roadAddress(originalLodgingData.getRoadAddress())                           // 도로명주소
                .serviceName(originalLodgingData.getServiceName())                           // 서비스명

                // 좌표 정보는 별도 설정 (초기값 null)
                .latitude(null)
                .longitude(null)
                .build();
    }

    /**
     * 숙박업 주소 정보 기반 좌표 재계산
     */
    private Double[] recalculateCoordinatesForLodging(LodgingBusiness lodgingData) {
        try {
            // 1순위: 도로명주소 기반 좌표 계산
            if (lodgingData.getRoadAddress() != null && !lodgingData.getRoadAddress().trim().isEmpty()) {
                Double[] coordinates = coordinateService.calculateCoordinatesFromRoadAddress(lodgingData.getRoadAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            // 2순위: 지번주소 기반 좌표 계산
            if (lodgingData.getFullAddress() != null && !lodgingData.getFullAddress().trim().isEmpty()) {
                Double[] coordinates = coordinateService.calculateCoordinatesFromFullAddress(lodgingData.getFullAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            log.debug("좌표 재계산 실패 - 숙박업소: {}, 주소 정보 부족", lodgingData.getBusinessName());
            return null;

        } catch (Exception coordinateCalculationException) {
            log.error("좌표 재계산 중 오류 발생 - 숙박업소: {}, 오류: {}",
                    lodgingData.getBusinessName(), coordinateCalculationException.getMessage());
            return null;
        }
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisLodgingRepository.count();
            log.info("최종 분석용 숙박업 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 영업상태별 분포 조회 및 로깅
            List<Object[]> lodgingCountByStatusList = analysisLodgingRepository.findLodgingCountByBusinessStatus();
            log.info("영업상태별 분포 (상위 5개):");

            lodgingCountByStatusList.stream()
                    .limit(5)
                    .forEach(statusRow -> {
                        String businessStatus = (String) statusRow[0];       // 영업상태명
                        Long statusCount = (Long) statusRow[1];              // 해당 상태 수
                        log.info("  {} : {} 개", businessStatus, statusCount);
                    });

            // 업종별 분포 조회 및 로깅
            List<Object[]> lodgingCountByTypeList = analysisLodgingRepository.findLodgingCountByBusinessType();
            log.info("업종별 분포:");

            lodgingCountByTypeList.forEach(typeRow -> {
                String businessType = (String) typeRow[0];           // 업종명
                Long typeCount = (Long) typeRow[1];                  // 해당 업종 수
                log.info("  {} : {} 개", businessType, typeCount);
            });

            // 좌표 정보 완성도 확인
            long coordinateCompleteCount = analysisLodgingRepository.findAllWithCoordinates().size();
            long coordinateMissingCount = analysisLodgingRepository.countMissingCoordinates();

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
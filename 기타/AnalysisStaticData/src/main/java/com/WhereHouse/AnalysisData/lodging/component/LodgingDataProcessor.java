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
 * 숙박업 데이터 분석용 테이블 생성 처리 컴포넌트 (서울 지역 필터링)
 *
 * 기존 LODGING_BUSINESS 테이블에서 서울 지역 데이터만 조회하여
 * 분석 전용 ANALYSIS_LODGING_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 숙박업 통계 데이터 중 서울 지역만 조회 및 검증
 * - 지정된 10개 필드만 복사
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
public class LodgingDataProcessor {

    // 원본 숙박업 통계 테이블 접근을 위한 Repository
    private final LodgingBusinessRepository originalLodgingRepository;

    // 분석용 숙박업 통계 테이블 접근을 위한 Repository
    private final AnalysisLodgingRepository analysisLodgingRepository;

    // Kakao API 좌표 계산을 위한 서비스
    private final LodgingCoordinateService coordinateService;

    /**
     * 숙박업 데이터 분석용 테이블 생성 메인 프로세스 (서울 지역 한정)
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 숙박업 데이터 중 서울 지역만 조회 및 검증
     * 3. 데이터 변환 및 Kakao API 좌표 계산 후 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisLodgingData() {
        log.info("=== 숙박업 데이터 분석용 테이블 생성 작업 시작 (서울 지역 한정) ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisLodgingRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 숙박업 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 숙박업 통계 데이터 중 서울 지역만 조회 및 검증
        List<LodgingBusiness> originalLodgingDataList = getSeoulLodgingData();
        if (originalLodgingDataList.isEmpty()) {
            log.warn("서울 지역 숙박업 통계 데이터가 존재하지 않습니다. 먼저 LodgingDataLoader를 통해 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("서울 지역 숙박업 통계 데이터 {} 개 발견", originalLodgingDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;      // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;          // 변환 실패한 데이터 개수
        int coordinateCalculationSuccessCount = 0; // 좌표 계산 성공 개수
        int coordinateCalculationFailedCount = 0;  // 좌표 계산 실패 개수

        // 처리 진행률 추적
        int processedCount = 0;
        int totalCount = originalLodgingDataList.size();
        int logInterval = Math.max(1, totalCount / 10); // 10% 간격으로 로그 출력

        for (LodgingBusiness originalLodgingData : originalLodgingDataList) {
            processedCount++;

            try {
                // 원본 데이터를 분석용 엔티티로 변환 (지정된 10개 필드만)
                AnalysisLodgingStatistics analysisTargetLodgingData = convertToAnalysisEntity(originalLodgingData);

                // Kakao API를 통한 좌표 계산 및 설정
                Double[] coordinates = calculateCoordinatesForLodging(originalLodgingData);
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

                // 진행률 로그 (10% 간격)
                if (processedCount % logInterval == 0 || processedCount == totalCount) {
                    double progressPercentage = (double) processedCount / totalCount * 100;
                    log.info("진행률: {:.1f}% 완료 ({}/{})", progressPercentage, processedCount, totalCount);
                }

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 숙박업소: {}, 오류: {}",
                        originalLodgingData.getBusinessName(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("숙박업 데이터 분석용 테이블 생성 작업 완료 (서울 지역 한정)");
        log.info("- 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("- 좌표 계산: 성공 {} 개, 실패 {} 개", coordinateCalculationSuccessCount, coordinateCalculationFailedCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 숙박업 데이터 분석용 테이블 생성 작업 종료 (서울 지역 한정) ===");
    }

    /**
     * 서울 지역 숙박업 데이터만 필터링하여 조회
     *
     * fullAddress 또는 roadAddress에 "서울"이 포함된 데이터만 반환한다.
     *
     * @return 서울 지역 숙박업 데이터 리스트
     */
    private List<LodgingBusiness> getSeoulLodgingData() {
        log.info("서울 지역 숙박업 데이터 필터링 시작...");

        // 전체 데이터 조회
        List<LodgingBusiness> allLodgingData = originalLodgingRepository.findAll();
        log.info("전체 숙박업 데이터 {} 개 중 서울 지역 데이터 필터링 중...", allLodgingData.size());

        // 서울 지역 데이터만 필터링
        List<LodgingBusiness> seoulLodgingData = allLodgingData.stream()
                .filter(this::isSeoulAddress)
                .toList();

        log.info("서울 지역 필터링 완료: {} 개 → {} 개 ({:.1f}%)",
                allLodgingData.size(),
                seoulLodgingData.size(),
                (double) seoulLodgingData.size() / allLodgingData.size() * 100);

        return seoulLodgingData;
    }

    /**
     * 주소가 서울 지역인지 확인
     *
     * @param lodgingBusiness 숙박업 데이터
     * @return 서울 지역 여부
     */
    private boolean isSeoulAddress(LodgingBusiness lodgingBusiness) {
        // 도로명주소 우선 확인
        if (lodgingBusiness.getRoadAddress() != null &&
                !lodgingBusiness.getRoadAddress().trim().isEmpty()) {
            String roadAddress = lodgingBusiness.getRoadAddress().trim();
            if (roadAddress.contains("서울") || roadAddress.contains("서울시") ||
                    roadAddress.contains("서울특별시")) {
                return true;
            }
        }

        // 지번주소 확인
        if (lodgingBusiness.getFullAddress() != null &&
                !lodgingBusiness.getFullAddress().trim().isEmpty()) {
            String fullAddress = lodgingBusiness.getFullAddress().trim();
            if (fullAddress.contains("서울") || fullAddress.contains("서울시") ||
                    fullAddress.contains("서울특별시")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 원본 숙박업 엔티티를 분석용 엔티티로 변환
     *
     * 지정된 10개 필드만 복사한다.
     * 좌표 정보는 별도 메서드에서 계산하여 설정한다.
     *
     * @param originalLodgingData 원본 숙박업 엔티티
     * @return 분석용 숙박업 통계 엔티티
     */
    private AnalysisLodgingStatistics convertToAnalysisEntity(LodgingBusiness originalLodgingData) {
        return AnalysisLodgingStatistics.builder()
                // 지정된 10개 필드만 복사
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
     * 숙박업 주소 정보 기반 Kakao API 좌표 계산
     *
     * 도로명주소를 우선으로 하고, 없는 경우 지번주소를 활용하여 좌표를 계산한다.
     *
     * @param lodgingData 원본 숙박업 데이터
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] calculateCoordinatesForLodging(LodgingBusiness lodgingData) {
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
                Double[] coordinates = coordinateService.calculateCoordinatesFromAddress(lodgingData.getFullAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            log.debug("좌표 계산 실패 - 숙박업소: {}, 주소 정보 부족", lodgingData.getBusinessName());
            return null;

        } catch (Exception coordinateCalculationException) {
            log.error("좌표 계산 중 오류 발생 - 숙박업소: {}, 오류: {}",
                    lodgingData.getBusinessName(), coordinateCalculationException.getMessage());
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
            long finalAnalysisDataCount = analysisLodgingRepository.count();
            log.info("최종 분석용 숙박업 데이터 저장 완료 (서울 지역): {} 개", finalAnalysisDataCount);

            // 영업상태별 분포 조회 및 로깅 (피어슨 상관분석 검증용)
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
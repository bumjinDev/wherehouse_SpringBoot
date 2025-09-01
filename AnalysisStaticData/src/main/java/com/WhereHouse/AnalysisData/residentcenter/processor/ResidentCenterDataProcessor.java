package com.WhereHouse.AnalysisData.residentcenter.processor;

import com.WhereHouse.AnalysisData.residentcenter.entity.AnalysisResidentCenterStatistics;
import com.WhereHouse.AnalysisData.residentcenter.repository.AnalysisResidentCenterRepository;
import com.WhereHouse.AnalysisData.residentcenter.service.ResidentCenterCoordinateService;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.CommunityCenterSave.Entity.ResidentCenter;
import com.WhereHouse.AnalysisStaticData.CommunityCenterSave.Repository.ResidentCenterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 서울시 주민센터 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 RESIDENT_CENTER 테이블에서 데이터를 조회하여
 * 분석 전용 ANALYSIS_RESIDENT_CENTER_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 서울시 주민센터 통계 데이터 조회 및 검증
 * - 지정된 4개 필드만 복사 (sido, sigungu, eupmeondong, address)
 * - 주소 기반 위도, 경도 좌표 계산 및 추가
 * - 분석용 테이블 데이터 품질 검증
 * - 구별 주민센터 수 및 동별 분포 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResidentCenterDataProcessor {

    // 원본 서울시 주민센터 테이블 접근을 위한 Repository
    private final ResidentCenterRepository originalResidentCenterRepository;

    // 분석용 서울시 주민센터 통계 테이블 접근을 위한 Repository
    private final AnalysisResidentCenterRepository analysisResidentCenterRepository;

    // 좌표 계산을 위한 서비스
    private final ResidentCenterCoordinateService coordinateService;

    /**
     * 서울시 주민센터 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 서울시 주민센터 데이터 조회 및 검증
     * 3. 데이터 변환 및 좌표 계산 후 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisResidentCenterData() {
        log.info("=== 서울시 주민센터 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisResidentCenterRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 서울시 주민센터 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 서울시 주민센터 통계 데이터 조회 및 검증
        List<ResidentCenter> originalResidentCenterDataList = originalResidentCenterRepository.findAll();
        if (originalResidentCenterDataList.isEmpty()) {
            log.warn("원본 서울시 주민센터 통계 데이터가 존재하지 않습니다. 먼저 ResidentCenterDataLoader를 통해 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 서울시 주민센터 통계 데이터 {} 개 발견", originalResidentCenterDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;      // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;          // 변환 실패한 데이터 개수
        int coordinateCalculationSuccessCount = 0; // 좌표 계산 성공 개수
        int coordinateCalculationFailedCount = 0;  // 좌표 계산 실패 개수

        for (ResidentCenter originalResidentCenterData : originalResidentCenterDataList) {
            try {
                // 원본 데이터를 분석용 엔티티로 변환
                AnalysisResidentCenterStatistics analysisTargetResidentCenterData = convertToAnalysisEntity(originalResidentCenterData);

                // 좌표 계산 및 설정
                Double[] coordinates = calculateCoordinatesForResidentCenter(originalResidentCenterData);
                if (coordinates != null) {
                    analysisTargetResidentCenterData.setLatitude(coordinates[0]);
                    analysisTargetResidentCenterData.setLongitude(coordinates[1]);
                    coordinateCalculationSuccessCount++;
                } else {
                    coordinateCalculationFailedCount++;
                }

                // 분석용 테이블에 데이터 저장
                analysisResidentCenterRepository.save(analysisTargetResidentCenterData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} {} (좌표: {}, {})",
                        originalResidentCenterData.getSigungu(),
                        originalResidentCenterData.getEupmeondong(),
                        coordinates != null ? coordinates[0] : "없음",
                        coordinates != null ? coordinates[1] : "없음");

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 주민센터: {} {}, 오류: {}",
                        originalResidentCenterData.getSigungu(),
                        originalResidentCenterData.getEupmeondong(),
                        dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("서울시 주민센터 데이터 분석용 테이블 생성 작업 완료");
        log.info("- 데이터 변환: 성공 {} 개, 실패 {} 개", successfulConversionCount, failedConversionCount);
        log.info("- 좌표 계산: 성공 {} 개, 실패 {} 개", coordinateCalculationSuccessCount, coordinateCalculationFailedCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 서울시 주민센터 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 서울시 주민센터 엔티티를 분석용 엔티티로 변환
     *
     * 지정된 4개 필드만 복사한다.
     * 좌표 정보는 별도 메서드에서 계산하여 설정한다.
     *
     * @param originalResidentCenterData 원본 서울시 주민센터 엔티티
     * @return 분석용 서울시 주민센터 통계 엔티티
     */
    private AnalysisResidentCenterStatistics convertToAnalysisEntity(ResidentCenter originalResidentCenterData) {
        return AnalysisResidentCenterStatistics.builder()
                // 지정된 4개 필드만 복사
                .sido(originalResidentCenterData.getSido())                           // 시도
                .sigungu(originalResidentCenterData.getSigungu())                     // 시군구
                .eupmeondong(originalResidentCenterData.getEupmeondong())             // 읍면동
                .address(originalResidentCenterData.getAddress())                     // 주소

                // 좌표 정보는 별도 설정 (초기값 null)
                .latitude(null)
                .longitude(null)
                .build();
    }

    /**
     * 서울시 주민센터 주소 정보 기반 좌표 계산
     *
     * 주소를 활용하여 좌표를 계산한다.
     *
     * @param residentCenterData 원본 서울시 주민센터 데이터
     * @return 위도, 경도 배열 [latitude, longitude] 또는 null
     */
    private Double[] calculateCoordinatesForResidentCenter(ResidentCenter residentCenterData) {
        try {
            // 주소 기반 좌표 계산
            if (residentCenterData.getAddress() != null && !residentCenterData.getAddress().trim().isEmpty()) {
                Double[] coordinates = coordinateService.calculateCoordinatesFromAddress(residentCenterData.getAddress());
                if (coordinates != null) {
                    return coordinates;
                }
            }

            log.debug("좌표 계산 실패 - 주민센터: {} {}, 주소 정보 부족",
                    residentCenterData.getSigungu(), residentCenterData.getEupmeondong());
            return null;

        } catch (Exception coordinateCalculationException) {
            log.error("좌표 계산 중 오류 발생 - 주민센터: {} {}, 오류: {}",
                    residentCenterData.getSigungu(),
                    residentCenterData.getEupmeondong(),
                    coordinateCalculationException.getMessage());
            return null;
        }
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 구별 주민센터 수 상위 5개 로깅
     * - 동별 주민센터 분포 로깅
     * - 좌표 정보 완성도 확인
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisResidentCenterRepository.count();
            log.info("최종 분석용 서울시 주민센터 데이터 저장 완료: {} 개", finalAnalysisDataCount);

            // 구별 주민센터 수 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> residentCenterCountBySigunguList = analysisResidentCenterRepository.findResidentCenterCountBySigungu();
            log.info("구별 주민센터 수 순위 (상위 5개 구):");

            residentCenterCountBySigunguList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String sigungu = (String) rankingRow[0];         // 시군구명
                        Long centerCount = (Long) rankingRow[1];         // 주민센터 수
                        log.info("  {} : {} 개", sigungu, centerCount);
                    });

            // 동별 주민센터 분포 조회 및 로깅
            List<Object[]> residentCenterCountByEupmeondongList = analysisResidentCenterRepository.findResidentCenterCountByEupmeondong();
            log.info("동별 주민센터 분포 (상위 5개):");

            residentCenterCountByEupmeondongList.stream()
                    .limit(5)
                    .forEach(dongRow -> {
                        String eupmeondong = (String) dongRow[0];        // 읍면동명
                        Long dongCount = (Long) dongRow[1];              // 해당 동 주민센터 수
                        log.info("  {} : {} 개", eupmeondong, dongCount);
                    });

            // 좌표 정보 완성도 확인
            long coordinateCompleteCount = analysisResidentCenterRepository.findAllWithCoordinates().size();
            long coordinateMissingCount = analysisResidentCenterRepository.countMissingCoordinates();

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
package com.WhereHouse.AnalysisData.crime.processor;

import com.WhereHouse.AnalysisData.crime.entity.AnalysisCrimeStatistics;
import com.WhereHouse.AnalysisData.crime.repository.AnalysisCrimeRepository;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.CriminalInfoSave.entity.CrimeStatistics;
import com.WhereHouse.AnalysisStaticData.CriminalInfoSave.repository.CrimeStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 범죄 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 CRIME_STATISTICS 테이블에서 데이터를 조회하여
 * 분석 전용 ANALYSIS_CRIME_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 범죄 통계 데이터 조회 및 검증
 * - CREATED_AT 필드 제외한 모든 범죄 통계 필드 복사
 * - 분석용 테이블 데이터 품질 검증
 * - 구별 범죄 발생 순위 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CrimeDataProcessor {

    // 원본 범죄 통계 테이블 접근을 위한 Repository
    private final CrimeStatisticsRepository originalCrimeRepository;

    // 분석용 범죄 통계 테이블 접근을 위한 Repository
    private final AnalysisCrimeRepository analysisCrimeRepository;

    /**
     * 범죄 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 범죄 데이터 조회 및 검증
     * 3. 데이터 변환 및 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisCrimeData() {
        log.info("=== 범죄 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisCrimeRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 범죄 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 범죄 통계 데이터 조회 및 검증
        List<CrimeStatistics> originalCrimeDataList = originalCrimeRepository.findAll();
        if (originalCrimeDataList.isEmpty()) {
            log.warn("원본 범죄 통계 데이터가 존재하지 않습니다. 먼저 CrimeDataLoader를 통해 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 범죄 통계 데이터 {} 개 구 발견", originalCrimeDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;  // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;      // 변환 실패한 데이터 개수

        for (CrimeStatistics originalCrimeData : originalCrimeDataList) {
            try {
                // 원본 데이터를 분석용 엔티티로 변환 (CREATED_AT 필드 제외)
                AnalysisCrimeStatistics analysisTargetCrimeData = convertToAnalysisEntity(originalCrimeData);

                // 분석용 테이블에 데이터 저장
                analysisCrimeRepository.save(analysisTargetCrimeData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} 구 (총 범죄 발생: {} 건)",
                        originalCrimeData.getDistrictName(), originalCrimeData.getTotalOccurrence());

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 구명: {}, 오류: {}",
                        originalCrimeData.getDistrictName(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("범죄 데이터 분석용 테이블 생성 작업 완료 - 성공: {} 개, 실패: {} 개",
                successfulConversionCount, failedConversionCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 범죄 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 범죄 통계 엔티티를 분석용 엔티티로 변환
     *
     * CREATED_AT 필드를 제외한 모든 범죄 통계 필드를 복사한다.
     *
     * @param originalCrimeData 원본 범죄 통계 엔티티
     * @return 분석용 범죄 통계 엔티티
     */
    private AnalysisCrimeStatistics convertToAnalysisEntity(CrimeStatistics originalCrimeData) {
        return AnalysisCrimeStatistics.builder()
                // 기본 정보
                .districtName(originalCrimeData.getDistrictName())         // 자치구명
                .year(originalCrimeData.getYear())                         // 통계 연도

                // 전체 범죄 통계
                .totalOccurrence(originalCrimeData.getTotalOccurrence())   // 총 범죄 발생 건수
                .totalArrest(originalCrimeData.getTotalArrest())           // 총 범죄 검거 건수

                // 살인 범죄 통계
                .murderOccurrence(originalCrimeData.getMurderOccurrence()) // 살인 발생 건수
                .murderArrest(originalCrimeData.getMurderArrest())         // 살인 검거 건수

                // 강도 범죄 통계
                .robberyOccurrence(originalCrimeData.getRobberyOccurrence()) // 강도 발생 건수
                .robberyArrest(originalCrimeData.getRobberyArrest())         // 강도 검거 건수

                // 성범죄 통계
                .sexualCrimeOccurrence(originalCrimeData.getSexualCrimeOccurrence()) // 성범죄 발생 건수
                .sexualCrimeArrest(originalCrimeData.getSexualCrimeArrest())         // 성범죄 검거 건수

                // 절도 범죄 통계
                .theftOccurrence(originalCrimeData.getTheftOccurrence())   // 절도 발생 건수
                .theftArrest(originalCrimeData.getTheftArrest())           // 절도 검거 건수

                // 폭력 범죄 통계
                .violenceOccurrence(originalCrimeData.getViolenceOccurrence()) // 폭력 발생 건수
                .violenceArrest(originalCrimeData.getViolenceArrest())         // 폭력 검거 건수
                .build();
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 구별 범죄 발생 순위 상위 5개 로깅
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisCrimeRepository.count();
            log.info("최종 분석용 범죄 데이터 저장 완료: {} 개 구", finalAnalysisDataCount);

            // 구별 범죄 발생 순위 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> districtCrimeRankingList = analysisCrimeRepository.findDistrictCrimeRanking();
            log.info("서울시 구별 범죄 발생 순위 (상위 5개구):");

            districtCrimeRankingList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String districtName = (String) rankingRow[0];      // 구 이름
                        Integer totalCrimeCount = (Integer) rankingRow[1]; // 총 범죄 발생 건수
                        log.info("  {} : {} 건", districtName, totalCrimeCount);
                    });

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}
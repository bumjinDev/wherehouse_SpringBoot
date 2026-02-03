package com.WhereHouse.AnalysisData.population.processsor;

import com.WhereHouse.AnalysisData.population.entity.AnalysisPopulationDensity;
import com.WhereHouse.AnalysisData.population.repository.AnalysisPopulationDensityRepository;
// 1차 파일의 원본 데이터 접근을 위한 import
import com.WhereHouse.AnalysisStaticData.Population.entity.PopulationDensity;
import com.WhereHouse.AnalysisStaticData.Population.repository.PopulationDensityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 인구밀도 데이터 분석용 테이블 생성 처리 컴포넌트 (2차 작업 - 1차 파일 연동)
 *
 * 1차 파일에서 생성된 POPULATION_DENSITY 테이블의 데이터를 조회하여
 * 분석 전용 ANALYSIS_POPULATION_DENSITY 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 1차 파일에서 로딩된 원본 인구밀도 통계 데이터 조회 및 검증
 * - CREATED_AT 필드 제외한 모든 인구밀도 통계 필드 복사
 * - 1차 파일의 DISTRICT_NAME 필드를 그대로 활용
 * - 분석용 테이블 데이터 품질 검증
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisPopulationDataProcessor {

    // 1차 파일의 원본 인구밀도 통계 테이블 접근을 위한 Repository
    private final PopulationDensityRepository originalPopulationRepository;

    // 분석용 인구밀도 통계 테이블 접근을 위한 Repository
    private final AnalysisPopulationDensityRepository analysisPopulationRepository;

    /**
     * 인구밀도 데이터 분석용 테이블 생성 메인 프로세스 (2차 작업 - 1차 파일 연동)
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 1차 파일에서 로딩된 원본 인구밀도 데이터 조회 및 검증
     * 3. 데이터 변환 및 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisPopulationData() {
        log.info("=== 인구밀도 데이터 분석용 테이블 생성 작업 시작 (2차 작업 - 1차 파일 연동) ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisPopulationRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 인구밀도 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 1차 파일에서 로딩된 원본 인구밀도 통계 데이터 조회 및 검증
        List<PopulationDensity> originalPopulationDataList = originalPopulationRepository.findAll();
        if (originalPopulationDataList.isEmpty()) {
            log.warn("원본 인구밀도 통계 데이터가 존재하지 않습니다. 먼저 1차 파일의 PopulationDensityDataLoader를 통해 데이터를 로드해주세요.");
            return;
        }

        log.info("1차 파일에서 로딩된 원본 인구밀도 통계 데이터 {} 개 구 발견", originalPopulationDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;  // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;      // 변환 실패한 데이터 개수

        for (PopulationDensity originalPopulationData : originalPopulationDataList) {
            try {
                // 원본 데이터를 분석용 엔티티로 변환 (1차 파일 구조 기반)
                AnalysisPopulationDensity analysisTargetPopulationData = convertToAnalysisEntity(originalPopulationData);

                // 분석용 테이블에 데이터 저장
                analysisPopulationRepository.save(analysisTargetPopulationData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} 구 (인구밀도: {} 명/㎢)",
                        originalPopulationData.getDistrictName(), originalPopulationData.getPopulationDensity());

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 구명: {}, 오류: {}",
                        originalPopulationData.getDistrictName(), dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("인구밀도 데이터 분석용 테이블 생성 작업 완료 - 성공: {} 개, 실패: {} 개",
                successfulConversionCount, failedConversionCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 인구밀도 데이터 분석용 테이블 생성 작업 종료 (2차 작업 - 1차 파일 연동) ===");
    }

    /**
     * 1차 파일의 원본 인구밀도 통계 엔티티를 분석용 엔티티로 변환
     *
     * CREATED_AT 필드를 제외한 모든 인구밀도 통계 필드를 복사한다.
     * 1차 파일의 DISTRICT_NAME 필드를 그대로 활용한다.
     *
     * @param originalPopulationData 1차 파일의 원본 인구밀도 통계 엔티티
     * @return 분석용 인구밀도 통계 엔티티
     */
    private AnalysisPopulationDensity convertToAnalysisEntity(PopulationDensity originalPopulationData) {
        return AnalysisPopulationDensity.builder()
                // 1차 파일의 DISTRICT_NAME 필드 그대로 활용
                .districtName(originalPopulationData.getDistrictName())       // 자치구명
                .year(originalPopulationData.getYear())                       // 통계 연도

                // 인구밀도 통계 (1차 파일 구조와 동일)
                .populationCount(originalPopulationData.getPopulationCount()) // 인구 수
                .areaSize(originalPopulationData.getAreaSize())               // 면적 (㎢)
                .populationDensity(originalPopulationData.getPopulationDensity()) // 인구밀도 (명/㎢)
                .build();
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 구별 인구밀도 순위 상위 5개 로깅
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisPopulationRepository.count();
            log.info("최종 분석용 인구밀도 데이터 저장 완료: {} 개 구", finalAnalysisDataCount);

            // 구별 인구밀도 순위 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> districtDensityRankingList = analysisPopulationRepository.findDistrictDensityRanking();
            log.info("서울시 구별 인구밀도 순위 (상위 5개구):");

            districtDensityRankingList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String districtName = (String) rankingRow[0];           // 구 이름
                        Object densityObj = rankingRow[1];                      // 인구밀도
                        log.info("  {} : {} 명/㎢", districtName, densityObj);
                    });

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}
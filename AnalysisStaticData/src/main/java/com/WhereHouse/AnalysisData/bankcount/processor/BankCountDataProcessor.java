package com.WhereHouse.AnalysisData.bankcount.processor;

import com.WhereHouse.AnalysisData.bankcount.entity.AnalysisBankCountStatistics;
import com.WhereHouse.AnalysisData.bankcount.repository.AnalysisBankCountRepository;
// 원본 데이터 접근을 위한 기존 패키지 import
import com.WhereHouse.AnalysisStaticData.FinancialInstitutionSave.entity.FinancialInstitution;
import com.WhereHouse.AnalysisStaticData.FinancialInstitutionSave.Repository.FinancialInstitutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 은행 지점 개수 데이터 분석용 테이블 생성 처리 컴포넌트
 *
 * 기존 FINANCIAL_INSTITUTION 테이블에서 데이터를 조회하여
 * 분석 전용 ANALYSIS_BANKCOUNT_STATISTICS 테이블로 복사하는 작업을 수행한다.
 *
 * 주요 기능:
 * - 원본 은행 지점 개수 데이터 조회 및 검증
 * - CREATED_AT 필드 제외한 모든 은행 지점 개수 필드 복사
 * - 분석용 테이블 데이터 품질 검증
 * - 구별 은행 밀도 순위 로깅
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BankCountDataProcessor {

    // 원본 은행 지점 개수 테이블 접근을 위한 Repository
    private final FinancialInstitutionRepository originalFinancialRepository;

    // 분석용 은행 지점 개수 테이블 접근을 위한 Repository
    private final AnalysisBankCountRepository analysisBankCountRepository;

    /**
     * 은행 지점 개수 데이터 분석용 테이블 생성 메인 프로세스
     *
     * 작업 순서:
     * 1. 기존 분석용 데이터 존재 여부 확인
     * 2. 원본 금융기관 데이터 조회 및 검증
     * 3. 데이터 변환 및 분석용 테이블 저장
     * 4. 데이터 품질 검증 및 결과 로깅
     */
    @Transactional
    public void processAnalysisBankCountData() {
        log.info("=== 은행 지점 개수 데이터 분석용 테이블 생성 작업 시작 ===");

        // Step 1: 기존 분석용 데이터 중복 처리 방지를 위한 존재 여부 확인
        long existingAnalysisDataCount = analysisBankCountRepository.count();
        if (existingAnalysisDataCount > 0) {
            log.info("분석용 은행 지점 개수 데이터가 이미 존재합니다 (총 {} 개). 작업을 스킵합니다.", existingAnalysisDataCount);
            return;
        }

        // Step 2: 원본 은행 지점 개수 데이터 조회 및 검증
        List<FinancialInstitution> originalFinancialDataList = originalFinancialRepository.findAll();
        if (originalFinancialDataList.isEmpty()) {
            log.warn("원본 은행 지점 개수 데이터가 존재하지 않습니다. 먼저 FinancialInstitutionDataLoader를 통해 CSV 데이터를 로드해주세요.");
            return;
        }

        log.info("원본 은행 지점 개수 데이터 {} 개 동 발견", originalFinancialDataList.size());

        // Step 3: 데이터 변환 및 저장 작업 수행
        int successfulConversionCount = 0;  // 성공적으로 변환된 데이터 개수
        int failedConversionCount = 0;      // 변환 실패한 데이터 개수

        for (FinancialInstitution originalFinancialData : originalFinancialDataList) {
            try {
                // 원본 데이터를 분석용 엔티티로 변환 (CREATED_AT 필드 제외)
                AnalysisBankCountStatistics analysisTargetBankCountData = convertToAnalysisEntity(originalFinancialData);

                // 분석용 테이블에 데이터 저장
                analysisBankCountRepository.save(analysisTargetBankCountData);
                successfulConversionCount++;

                log.debug("분석용 데이터 생성 완료: {} {} (총 은행 수: {} 개)",
                        originalFinancialData.getSigungu(), originalFinancialData.getDong(),
                        calculateTotalBankCount(originalFinancialData));

            } catch (Exception dataConversionException) {
                log.error("분석용 데이터 생성 실패 - 구/동: {} {}, 오류: {}",
                        originalFinancialData.getSigungu(), originalFinancialData.getDong(),
                        dataConversionException.getMessage());
                failedConversionCount++;
            }
        }

        // Step 4: 변환 작업 결과 로깅
        log.info("은행 지점 개수 데이터 분석용 테이블 생성 작업 완료 - 성공: {} 개, 실패: {} 개",
                successfulConversionCount, failedConversionCount);

        // Step 5: 최종 데이터 검증 및 품질 확인
        performFinalDataValidation();

        log.info("=== 은행 지점 개수 데이터 분석용 테이블 생성 작업 종료 ===");
    }

    /**
     * 원본 은행 지점 개수 엔티티를 분석용 엔티티로 변환
     *
     * CREATED_AT 필드를 제외한 모든 은행 지점 개수 필드를 복사한다.
     *
     * @param originalFinancialData 원본 은행 지점 개수 엔티티
     * @return 분석용 은행 지점 개수 엔티티
     */
    private AnalysisBankCountStatistics convertToAnalysisEntity(FinancialInstitution originalFinancialData) {
        return AnalysisBankCountStatistics.builder()
                // 기본 정보
                .sigungu(originalFinancialData.getSigungu())               // 시군구명
                .dong(originalFinancialData.getDong())                     // 동명

                // 각 은행별 지점 수
                .wooriBankCount(originalFinancialData.getWooriBankCount()) // 우리은행 지점 수
                .scBankCount(originalFinancialData.getScBankCount())       // SC제일은행 지점 수
                .kbBankCount(originalFinancialData.getKbBankCount())       // KB국민은행 지점 수
                .shinhanBankCount(originalFinancialData.getShinhanBankCount()) // 신한은행 지점 수
                .citiBankCount(originalFinancialData.getCitiBankCount())   // 한국씨티은행 지점 수
                .hanaBankCount(originalFinancialData.getHanaBankCount())   // KEB하나은행 지점 수
                .ibkBankCount(originalFinancialData.getIbkBankCount())     // IBK기업은행 지점 수
                .nhBankCount(originalFinancialData.getNhBankCount())       // NH농협은행 지점 수
                .suhyupBankCount(originalFinancialData.getSuhyupBankCount()) // 수협은행 지점 수
                .kdbBankCount(originalFinancialData.getKdbBankCount())     // KDB산업은행 지점 수
                .eximBankCount(originalFinancialData.getEximBankCount())   // 한국수출입은행 지점 수
                .foreignBankCount(originalFinancialData.getForeignBankCount()) // 외국은행 지점 수
                .build();
    }

    /**
     * 개별 지역의 총 은행 지점 수 계산
     *
     * @param financialData 금융기관 분포 데이터
     * @return 총 은행 지점 수
     */
    private int calculateTotalBankCount(FinancialInstitution financialData) {
        return (financialData.getWooriBankCount() != null ? financialData.getWooriBankCount() : 0) +
                (financialData.getScBankCount() != null ? financialData.getScBankCount() : 0) +
                (financialData.getKbBankCount() != null ? financialData.getKbBankCount() : 0) +
                (financialData.getShinhanBankCount() != null ? financialData.getShinhanBankCount() : 0) +
                (financialData.getCitiBankCount() != null ? financialData.getCitiBankCount() : 0) +
                (financialData.getHanaBankCount() != null ? financialData.getHanaBankCount() : 0) +
                (financialData.getIbkBankCount() != null ? financialData.getIbkBankCount() : 0) +
                (financialData.getNhBankCount() != null ? financialData.getNhBankCount() : 0) +
                (financialData.getSuhyupBankCount() != null ? financialData.getSuhyupBankCount() : 0) +
                (financialData.getKdbBankCount() != null ? financialData.getKdbBankCount() : 0) +
                (financialData.getEximBankCount() != null ? financialData.getEximBankCount() : 0) +
                (financialData.getForeignBankCount() != null ? financialData.getForeignBankCount() : 0);
    }

    /**
     * 분석용 데이터의 최종 검증 및 품질 확인
     *
     * 작업 내용:
     * - 전체 데이터 개수 확인
     * - 구별 은행 밀도 순위 상위 5개 로깅
     * - 데이터 검증 과정에서 발생하는 오류 처리
     */
    private void performFinalDataValidation() {
        try {
            // 최종 저장된 분석용 데이터 개수 확인
            long finalAnalysisDataCount = analysisBankCountRepository.count();
            log.info("최종 분석용 은행 지점 개수 데이터 저장 완료: {} 개 동", finalAnalysisDataCount);

            // 구별 은행 밀도 순위 조회 및 로깅 (피어슨 상관분석 검증용)
            List<Object[]> districtBankDensityRankingList = analysisBankCountRepository.findDistrictBankDensityRanking();
            log.info("서울시 구별 은행 밀도 순위 (상위 5개구):"););

            districtBankDensityRankingList.stream()
                    .limit(5)
                    .forEach(rankingRow -> {
                        String districtName = (String) rankingRow[0];         // 구 이름
                        Long totalBankCount = (Long) rankingRow[1];           // 총 은행 지점 수
                        log.info("  {} : {} 개 지점", districtName, totalBankCount);
                    });

        } catch (Exception dataValidationException) {
            log.error("분석용 데이터 검증 과정에서 오류가 발생했습니다: {}",
                    dataValidationException.getMessage(), dataValidationException);
        }
    }
}
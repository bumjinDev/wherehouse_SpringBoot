package com.wherehouse.AnalysisData.main;

import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;
import org.apache.commons.math3.distribution.FDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 안전성 점수 산출을 위한 다중회귀분석 서비스
 *
 * 8개 주요 환경 변수와 범죄율 간의 다중선형회귀모델 구축
 * - 종속변수: 자치구별 범죄율 (인구 10만명당)
 * - 독립변수: 경찰시설, 노래연습장, 교육시설, 대형마트, 숙박업, 유흥주점, 지하철역, 인구밀도
 *
 * @version 1.0
 * @since 2025.09.11
 */
@Component
@Slf4j
public class MultipleRegressionAnalysis {

    // 통계적 유의성 검증 기준
    private static final double SIGNIFICANCE_LEVEL = 0.05;

    // 독립변수명 배열 (순서 중요!)
    private static final String[] VARIABLE_NAMES = {
            "경찰시설", "노래연습장", "교육시설", "대형마트",
            "숙박업", "유흥주점", "지하철역", "인구밀도"
    };

    /**
     * 다중회귀분석 수행 메인 메서드
     *
     * @param districtCrime 자치구별 범죄율 (종속변수)
     * @param policeData 경찰시설 데이터
     * @param karaokeData 노래연습장 데이터
     * @param schoolData 교육시설 데이터
     * @param martData 대형마트 데이터
     * @param lodgingData 숙박업 데이터
     * @param entertainmentData 유흥주점 데이터
     * @param subwayData 지하철역 데이터
     * @param populationData 인구밀도 데이터
     * @return 회귀분석 결과 객체
     */
    public RegressionAnalysisResult performMultipleRegression(
            LinkedHashMap<String, Double> districtCrime,
            LinkedHashMap<String, Double> policeData,
            LinkedHashMap<String, Double> karaokeData,
            LinkedHashMap<String, Double> schoolData,
            LinkedHashMap<String, Double> martData,
            LinkedHashMap<String, Double> lodgingData,
            LinkedHashMap<String, Double> entertainmentData,
            LinkedHashMap<String, Double> subwayData,
            LinkedHashMap<String, Double> populationData) {

        log.info("==================================================");
        log.info("=== 다중회귀분석 시작: 8개 변수 vs 범죄율 ===");
        log.info("==================================================");

        try {
            // 1. 데이터 준비 및 검증
            validateDataConsistency(districtCrime, policeData, karaokeData, schoolData,
                    martData, lodgingData, entertainmentData, subwayData, populationData);

            // 2. 종속변수 배열 생성 (범죄율)
            double[] yArray = districtCrime.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray();

            // 3. 독립변수 행렬 생성 (25개 자치구 × 8개 변수)
            double[][] xMatrix = buildIndependentVariableMatrix(
                    policeData, karaokeData, schoolData, martData,
                    lodgingData, entertainmentData, subwayData, populationData);

            // 4. OLS 다중회귀분석 수행 (Apache Commons Math 3.x 방식)
            OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
            regression.newSampleData(yArray, xMatrix);

            // 5. 회귀분석 결과 추출
            double[] coefficients = regression.estimateRegressionParameters();
            double[] standardErrors = regression.estimateRegressionParametersStandardErrors();
            double rSquared = regression.calculateRSquared();
            double adjustedRSquared = regression.calculateAdjustedRSquared();
            double mse = regression.estimateErrorVariance();

            // 6. 결과 분석 및 통계량 계산
            RegressionAnalysisResult analysisResult = analyzeRegressionResults(
                    coefficients, standardErrors, rSquared, adjustedRSquared, mse, yArray);

            // 7. 상세 결과 출력
            printDetailedResults(analysisResult);

            // 8. 모델 진단
            performModelDiagnostics(analysisResult);

            log.info("==================================================");
            log.info("=== 다중회귀분석 완료 ===");
            log.info("==================================================");

            return analysisResult;

        } catch (Exception e) {
            log.error("다중회귀분석 수행 중 오류 발생", e);
            throw new RuntimeException("다중회귀분석 실패", e);
        }
    }

    /**
     * 데이터 일관성 검증
     */
    private void validateDataConsistency(LinkedHashMap<String, Double>... dataMaps) {
        Set<String> districts = dataMaps[0].keySet();
        int expectedSize = districts.size();

        for (int i = 1; i < dataMaps.length; i++) {
            if (dataMaps[i].size() != expectedSize) {
                throw new IllegalArgumentException(
                        String.format("데이터 크기 불일치: 기준=%d, 변수%d=%d",
                                expectedSize, i, dataMaps[i].size()));
            }

            if (!dataMaps[i].keySet().equals(districts)) {
                throw new IllegalArgumentException("자치구 목록이 일치하지 않음");
            }
        }

        log.info("데이터 검증 완료: {}개 자치구, {}개 변수", expectedSize, VARIABLE_NAMES.length);
    }

    /**
     * 독립변수 행렬 구성
     */
    private double[][] buildIndependentVariableMatrix(
            LinkedHashMap<String, Double> policeData,
            LinkedHashMap<String, Double> karaokeData,
            LinkedHashMap<String, Double> schoolData,
            LinkedHashMap<String, Double> martData,
            LinkedHashMap<String, Double> lodgingData,
            LinkedHashMap<String, Double> entertainmentData,
            LinkedHashMap<String, Double> subwayData,
            LinkedHashMap<String, Double> populationData) {

        int numObservations = policeData.size();
        int numVariables = VARIABLE_NAMES.length;
        double[][] matrix = new double[numObservations][numVariables];

        // LinkedHashMap의 순서를 유지하면서 행렬 구성
        List<String> districts = new ArrayList<>(policeData.keySet());

        for (int i = 0; i < numObservations; i++) {
            String district = districts.get(i);

            matrix[i][0] = policeData.get(district);      // 경찰시설
            matrix[i][1] = karaokeData.get(district);     // 노래연습장
            matrix[i][2] = schoolData.get(district);      // 교육시설
            matrix[i][3] = martData.get(district);        // 대형마트
            matrix[i][4] = lodgingData.get(district);     // 숙박업
            matrix[i][5] = entertainmentData.get(district); // 유흥주점
            matrix[i][6] = subwayData.get(district);      // 지하철역
            matrix[i][7] = populationData.get(district);  // 인구밀도
        }

        log.info("독립변수 행렬 구성 완료: {}×{}", numObservations, numVariables);
        return matrix;
    }

    /**
     * 회귀분석 결과 분석 및 통계량 계산
     */
    private RegressionAnalysisResult analyzeRegressionResults(
            double[] coefficients, double[] standardErrors,
            double rSquared, double adjustedRSquared, double mse, double[] yArray) {

        int n = yArray.length; // 표본 크기 (25)
        int p = VARIABLE_NAMES.length; // 독립변수 개수 (8)

        // t-통계량 및 p-value 계산
        double[] tStats = new double[coefficients.length];
        double[] pValues = new double[coefficients.length];
        boolean[] isSignificant = new boolean[coefficients.length];

        TDistribution tDist = new TDistribution(n - p - 1);

        for (int i = 0; i < coefficients.length; i++) {
            tStats[i] = coefficients[i] / standardErrors[i];
            pValues[i] = 2.0 * (1.0 - tDist.cumulativeProbability(Math.abs(tStats[i])));
            isSignificant[i] = pValues[i] < SIGNIFICANCE_LEVEL;
        }

        // F-통계량 계산 (모델 전체 유의성 검정)
        double sst = calculateTotalSumOfSquares(yArray);
        double ssr = rSquared * sst;
        double sse = sst - ssr;

        double fStatistic = (ssr / p) / (sse / (n - p - 1));
        FDistribution fDist = new FDistribution(p, n - p - 1);
        double fPValue = 1.0 - fDist.cumulativeProbability(fStatistic);
        boolean modelSignificant = fPValue < SIGNIFICANCE_LEVEL;

        return new RegressionAnalysisResult(
                coefficients, standardErrors, tStats, pValues, isSignificant,
                rSquared, adjustedRSquared, mse, fStatistic, fPValue, modelSignificant,
                n, p
        );
    }

    /**
     * 총제곱합(TSS) 계산
     */
    private double calculateTotalSumOfSquares(double[] yArray) {
        double mean = Arrays.stream(yArray).average().orElse(0.0);
        return Arrays.stream(yArray)
                .map(y -> Math.pow(y - mean, 2))
                .sum();
    }

    /**
     * 상세 분석 결과 출력
     */
    private void printDetailedResults(RegressionAnalysisResult result) {
        System.out.println("\n=== 다중회귀분석 결과 요약 ===");

        // 모델 적합도
        System.out.printf("R² (결정계수): %.4f (%.1f%% 설명력)%n",
                result.rSquared, result.rSquared * 100);
        System.out.printf("수정된 R²: %.4f%n", result.adjustedRSquared);
        System.out.printf("MSE (평균제곱오차): %.4f%n", result.mse);
        System.out.printf("RMSE (표준오차): %.4f%n", Math.sqrt(result.mse));

        // 모델 전체 유의성 (F-검정)
        System.out.printf("%nF-검정 결과:%n");
        System.out.printf("F-통계량: %.4f%n", result.fStatistic);
        System.out.printf("p-value: %.6f%n", result.fPValue);
        System.out.printf("모델 유의성: %s%n", result.modelSignificant ? "유의함" : "비유의");

        // 회귀계수 상세 분석
        System.out.printf("%n=== 회귀계수 분석 ===");
        System.out.printf("%n%-12s %10s %10s %10s %10s %8s%n",
                "변수", "계수", "표준오차", "t-값", "p-value", "유의성");
        System.out.println("─".repeat(70));

        // 절편
        System.out.printf("%-12s %10.4f %10.4f %10.4f %10.6f %8s%n",
                "절편", result.coefficients[0], result.standardErrors[0],
                result.tStats[0], result.pValues[0],
                result.isSignificant[0] ? "***" : "");

        // 각 독립변수
        for (int i = 0; i < VARIABLE_NAMES.length; i++) {
            int idx = i + 1; // 절편 다음부터
            String significance = getSignificanceLevel(result.pValues[idx]);

            System.out.printf("%-12s %10.4f %10.4f %10.4f %10.6f %8s%n",
                    VARIABLE_NAMES[i], result.coefficients[idx],
                    result.standardErrors[idx], result.tStats[idx],
                    result.pValues[idx], significance);
        }

        System.out.println("유의수준: *** p<0.001, ** p<0.01, * p<0.05");
    }

    /**
     * 유의수준 표시 문자열 반환
     */
    private String getSignificanceLevel(double pValue) {
        if (pValue < 0.001) return "***";
        if (pValue < 0.01) return "**";
        if (pValue < 0.05) return "*";
        return "";
    }

    /**
     * 모델 진단 수행
     */
    private void performModelDiagnostics(RegressionAnalysisResult result) {
        System.out.printf("%n=== 모델 진단 ===");

        // 모델 품질 평가
        if (result.rSquared >= 0.7) {
            System.out.printf("%n✓ 높은 설명력: R²=%.3f (우수한 모델)%n", result.rSquared);
        } else if (result.rSquared >= 0.5) {
            System.out.printf("%n△ 중간 설명력: R²=%.3f (보통 모델)%n", result.rSquared);
        } else {
            System.out.printf("%n✗ 낮은 설명력: R²=%.3f (모델 개선 필요)%n", result.rSquared);
        }

        // 유의미한 변수 식별
        System.out.printf("%n유의미한 독립변수:%n");
        int significantCount = 0;

        for (int i = 0; i < VARIABLE_NAMES.length; i++) {
            int idx = i + 1; // 절편 제외
            if (result.isSignificant[idx]) {
                significantCount++;
                String effect = result.coefficients[idx] > 0 ? "범죄율 증가" : "범죄율 감소";
                System.out.printf("  • %s: 계수=%.4f (%s 효과)%n",
                        VARIABLE_NAMES[i], result.coefficients[idx], effect);
            }
        }

        if (significantCount == 0) {
            System.out.println("  • 통계적으로 유의미한 변수 없음");
        }

        // 권장사항
        System.out.printf("%n=== 분석 권장사항 ===");
        if (!result.modelSignificant) {
            System.out.printf("%n⚠ 모델 전체가 통계적으로 유의하지 않음");
            System.out.printf("%n  - 변수 선택 재검토 필요");
            System.out.printf("%n  - 비선형 관계 가능성 검토");
        }

        if (result.rSquared < 0.5) {
            System.out.printf("%n⚠ 낮은 설명력 - 추가 변수 고려 필요");
            System.out.printf("%n  - 누락된 중요 변수 있을 가능성");
            System.out.printf("%n  - 상호작용 효과 검토");
        }

        if (significantCount < 3) {
            System.out.printf("%n⚠ 유의미한 변수가 적음");
            System.out.printf("%n  - 다중공선성 문제 가능성");
            System.out.printf("%n  - 변수 변환 고려");
        }
    }

    /**
     * 다중회귀분석 결과를 담는 데이터 클래스
     */
    public static class RegressionAnalysisResult {
        public final double[] coefficients;        // 회귀계수
        public final double[] standardErrors;      // 표준오차
        public final double[] tStats;             // t-통계량
        public final double[] pValues;            // p-value
        public final boolean[] isSignificant;     // 유의성 여부

        public final double rSquared;             // 결정계수
        public final double adjustedRSquared;     // 수정된 결정계수
        public final double mse;                  // 평균제곱오차

        public final double fStatistic;           // F-통계량
        public final double fPValue;              // F-검정 p-value
        public final boolean modelSignificant;    // 모델 전체 유의성

        public final int sampleSize;              // 표본 크기
        public final int numVariables;            // 독립변수 개수

        public RegressionAnalysisResult(double[] coefficients, double[] standardErrors,
                                        double[] tStats, double[] pValues, boolean[] isSignificant,
                                        double rSquared, double adjustedRSquared, double mse,
                                        double fStatistic, double fPValue, boolean modelSignificant,
                                        int sampleSize, int numVariables) {
            this.coefficients = coefficients;
            this.standardErrors = standardErrors;
            this.tStats = tStats;
            this.pValues = pValues;
            this.isSignificant = isSignificant;
            this.rSquared = rSquared;
            this.adjustedRSquared = adjustedRSquared;
            this.mse = mse;
            this.fStatistic = fStatistic;
            this.fPValue = fPValue;
            this.modelSignificant = modelSignificant;
            this.sampleSize = sampleSize;
            this.numVariables = numVariables;
        }

        /**
         * 안전성 점수 계산을 위한 가중치 반환
         *
         * @return 유의미한 변수들의 정규화된 가중치 맵
         */
        public Map<String, Double> calculateWeights() {
            Map<String, Double> weights = new HashMap<>();
            double totalWeight = 0.0;

            // 유의미한 변수들의 절댓값 계수 합계 계산
            for (int i = 0; i < VARIABLE_NAMES.length; i++) {
                int idx = i + 1; // 절편 제외
                if (isSignificant[idx]) {
                    double weight = Math.abs(coefficients[idx]);
                    weights.put(VARIABLE_NAMES[i], weight);
                    totalWeight += weight;
                }
            }

            // 정규화 (합계 100%) - effectively final 변수 사용
            final double finalTotalWeight = totalWeight;
            if (finalTotalWeight > 0) {
                weights.replaceAll((k, v) -> (v / finalTotalWeight) * 100);
            }

            return weights;
        }
    }
}
package com.wherehouse.AnalysisData.main;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.distribution.TDistribution;
import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * 안전성 점수 산출을 위한 통계 분석 및 시각화 서비스
 *
 * 명세서 4.5절 피어슨 상관계수 계산 단계를 구현
 * 19개 환경 변수와 범죄율 간의 상관관계를 순차적으로 분석
 *
 * @version 2.0
 * @since 2025.09.09
 */
@Component
public class PearsonCorrelationCoefficient {

    // 통계적 유의성 검증 기준 (명세서 4.6절)
    private static final double SIGNIFICANCE_LEVEL = 0.05;
    private static final double MIN_CORRELATION_THRESHOLD = 0.3;

    // 다중 검정 보정 (Bonferroni correction for 19 variables)
    private static final double BONFERRONI_ADJUSTED_ALPHA = SIGNIFICANCE_LEVEL / 19.0; // ≈ 0.0026

    // 분석 결과 저장용 리스트
    private final List<CorrelationResult> allCorrelationResults = new ArrayList<>();

    /**
     * 범죄율과 환경 변수 간 피어슨 상관분석 및 산점도 시각화 수행 (범용 메서드)
     *
     * @param variableName 환경 변수명 (예: "CCTV 인프라", "유흥주점")
     * @param districtCrime 지역구별 인구 10만명당 범죄율 데이터 (정렬된 LinkedHashMap)
     * @param environmentData 지역구별 환경 변수 데이터 (정렬된 LinkedHashMap)
     * @param filePrefix 저장할 이미지 파일명 접두사 (예: "cctv", "entertainment")
     * @throws IOException 파일 저장 실패 시 발생
     *
     * @apiNote 25개 서울시 자치구 데이터를 기반으로 상관분석 수행
     * @see PearsonsCorrelation
     */
    public void analyzeCrimeCorrelation(String variableName,
                                        LinkedHashMap<String, Double> districtCrime,
                                        LinkedHashMap<String, Double> environmentData,
                                        String filePrefix) throws IOException {

        System.out.println(""); // 구분선
        System.out.printf("=== [%s] vs 범죄율 상관분석 시작 ===%n", variableName);

        // 입력 데이터를 List로 변환 (LinkedHashMap 순서 유지)
        List<Double> envData = new ArrayList<>(environmentData.values());
        List<Double> crimeData = new ArrayList<>(districtCrime.values());

        // 상관분석 수행 및 결과 저장
        CorrelationResult result = performCorrelationAnalysis(envData, crimeData, variableName);

        // 전체 결과에 추가 (나중에 종합 분석용)
        allCorrelationResults.add(result);

        // 콘솔에 분석 결과 출력
        logAnalysisResult(result);

        // 산점도 차트 생성 및 파일 저장
        generateScatterPlot(envData, crimeData, result, filePrefix);

        System.out.printf("=== [%s] 분석 완료 ===%n", variableName);
    }

    /**
     * 모든 환경 변수의 상관분석 결과를 종합하여 최종 가중치 산출
     * 명세서 4.7절 최종 가중치 산출 단계 구현
     *
     * @return 환경 변수별 최종 가중치 맵 (변수명 -> 가중치)
     */
    public Map<String, Double> calculateFinalWeights() {
        System.out.println("");
        System.out.println("==================================================");
        System.out.println("=== 최종 가중치 산출 (명세서 4.7절) ===");
        System.out.println("==================================================");

        Map<String, Double> finalWeights = new HashMap<>();
        List<CorrelationResult> significantResults = new ArrayList<>();

        // 4.6절: 통계적 유의성 검증을 통과한 변수들만 필터링
        for (CorrelationResult result : allCorrelationResults) {
            if (result.isStatisticallySignificant && Math.abs(result.correlationCoefficient) >= MIN_CORRELATION_THRESHOLD) {
                significantResults.add(result);
                System.out.printf("✓ [유의함] %-15s: r=%.3f, p=%.4f%n",
                        result.variableName, result.correlationCoefficient, result.pValue);
            } else {
                System.out.printf("✗ [제외됨] %-15s: r=%.3f, p=%.4f (기준 미달)%n",
                        result.variableName, result.correlationCoefficient, result.pValue);
            }
        }

        if (significantResults.isEmpty()) {
            System.out.println("경고: 통계적으로 유의미한 변수가 없습니다.");
            return finalWeights;
        }

        System.out.println("\n--- 가중치 계산 과정 ---");

        // 기본 가중치 계산: |r| × 100
        double totalBasicWeight = 0.0;
        Map<String, Double> basicWeights = new HashMap<>();

        for (CorrelationResult result : significantResults) {
            double basicWeight = Math.abs(result.correlationCoefficient) * 100;
            basicWeights.put(result.variableName, basicWeight);
            totalBasicWeight += basicWeight;
            System.out.printf("기본 가중치 - %s: %.2f%n", result.variableName, basicWeight);
        }

        // 정규화된 가중치 계산: (기본 가중치 / 전체 합) × 100
        System.out.println("\n--- 정규화된 최종 가중치 ---");
        double totalFinalWeight = 0.0;

        for (Map.Entry<String, Double> entry : basicWeights.entrySet()) {
            double normalizedWeight = (entry.getValue() / totalBasicWeight) * 100;
            finalWeights.put(entry.getKey(), normalizedWeight);
            totalFinalWeight += normalizedWeight;

            // 해당 변수의 상관계수 방향 표시
            CorrelationResult result = significantResults.stream()
                    .filter(r -> r.variableName.equals(entry.getKey()))
                    .findFirst().orElse(null);

            String direction = result != null && result.correlationCoefficient > 0 ? "위험↑" : "안전↑";

            System.out.printf("최종 가중치 - %-15s: %6.2f%% (%s, r=%.3f)%n",
                    entry.getKey(), normalizedWeight, direction,
                    result != null ? result.correlationCoefficient : 0.0);
        }

        System.out.printf("\n총 가중치 합계: %.1f%% (검증)%n", totalFinalWeight);
        System.out.printf("유의미한 변수 개수: %d개 / 전체 %d개%n",
                significantResults.size(), allCorrelationResults.size());

        return finalWeights;
    }

    /**
     * 피어슨 상관계수 계산 및 p-value 산출
     *
     * @param xData 독립변수 데이터 (환경 변수)
     * @param yData 종속변수 데이터 (범죄율)
     * @param variableName 환경 변수명
     * @return CorrelationResult 상관분석 결과 객체
     *
     * @implNote t-검정을 통한 유의성 검증 (α=0.05, 양측검정)
     * @formula t = r × √((n-2)/(1-r²)), p-value = 2 × P(T > |t|)
     */
    private CorrelationResult performCorrelationAnalysis(List<Double> xData, List<Double> yData, String variableName) {
        // Apache Commons Math 라이브러리 활용
        PearsonsCorrelation correlation = new PearsonsCorrelation();

        // List를 double[] 배열로 변환 (라이브러리 요구사항)
        double[] xArray = xData.stream().mapToDouble(Double::doubleValue).toArray();
        double[] yArray = yData.stream().mapToDouble(Double::doubleValue).toArray();

        // 피어슨 상관계수 계산 (-1 ≤ r ≤ 1)
        double r = correlation.correlation(xArray, yArray);

        int n = xArray.length; // 표본 크기 (25개 자치구)

        // t-통계량 계산 (상관계수의 유의성 검정용)
        double t = r * Math.sqrt((n - 2) / (1 - Math.pow(r, 2)));

        // p-value 계산 (양측 검정)
        TDistribution tDist = new TDistribution(n - 2);
        double pValue = 2.0 * (1.0 - tDist.cumulativeProbability(Math.abs(t)));

        // 유의성 판단 (원본 α=0.05 및 Bonferroni 보정)
        boolean isSignificant = pValue < SIGNIFICANCE_LEVEL;
        boolean isBonferroniSignificant = pValue < BONFERRONI_ADJUSTED_ALPHA;

        return new CorrelationResult(r, pValue, n, isSignificant, isBonferroniSignificant, variableName);
    }

    /**
     * 상관분석 결과를 콘솔에 출력
     *
     * @param result 분석 결과 객체
     *
     * @implNote 통계적 수치와 비즈니스 해석을 함께 제공
     */
    private void logAnalysisResult(CorrelationResult result) {
        System.out.printf("상관계수 (r): %+.4f%n", result.correlationCoefficient);
        System.out.printf("p-value: %.6f%n", result.pValue);
        System.out.printf("표본 크기: %d개 자치구%n", result.sampleSize);
        System.out.printf("유의성 (α=0.05): %s%n", result.isSignificant ? "유의함" : "비유의");
        System.out.printf("Bonferroni 보정 (α=0.0026): %s%n", result.isBonferroniSignificant ? "유의함" : "비유의");
        System.out.printf("상관관계: %s, %s%n",
                getDirectionInterpretation(result.correlationCoefficient),
                getStrengthInterpretation(Math.abs(result.correlationCoefficient)));

        // 비즈니스 관점 해석 추가
        if (result.isSignificant) {
            if (result.correlationCoefficient < -0.3) {
                System.out.printf("→ %s 증가 시 범죄율 감소 효과 확인 (안전 요소)%n", result.variableName);
            } else if (result.correlationCoefficient > 0.3) {
                System.out.printf("→ %s 증가 시 범죄율 증가 경향 (위험 요소)%n", result.variableName);
            } else {
                System.out.printf("→ %s와 범죄율 간 약한 상관관계%n", result.variableName);
            }
        } else {
            System.out.printf("→ %s와 범죄율 간 통계적으로 의미있는 관계 없음%n", result.variableName);
        }
    }

    /**
     * 상관분석 결과를 반영한 산점도 차트 생성 및 저장
     *
     * @param xData X축 데이터 (환경 변수)
     * @param yData Y축 데이터 (범죄율)
     * @param result 상관분석 결과
     * @param filePrefix 파일명 접두사
     * @throws IOException 파일 저장 실패 시
     *
     * @implNote 유의성에 따른 색상 구분: 유의시 파란색/빨간색, 비유의시 회색
     */
    private void generateScatterPlot(List<Double> xData, List<Double> yData, CorrelationResult result, String filePrefix) throws IOException {
        // XChart 빌더 패턴으로 차트 생성
        XYChart chart = new XYChartBuilder()
                .width(800)
                .height(600)
                .title(String.format("%s-범죄율 상관관계 (r=%+.3f, p=%.4f)",
                        result.variableName, result.correlationCoefficient, result.pValue))
                .xAxisTitle(result.variableName + " 개수")
                .yAxisTitle("인구 10만명당 범죄율")
                .build();

        // 차트 스타일 설정
        chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(8);

        // 데이터 시리즈 추가 및 색상 설정
        String seriesName = String.format("자치구 데이터 (%s)",
                result.isSignificant ? "유의함" : "비유의");
        XYSeries series = chart.addSeries(seriesName, xData, yData);

        // 유의성과 상관관계 방향에 따른 색상 구분
        if (result.isSignificant) {
            series.setMarkerColor(result.correlationCoefficient < 0 ?
                    java.awt.Color.BLUE : java.awt.Color.RED);
        } else {
            series.setMarkerColor(java.awt.Color.GRAY);
        }

        // PNG 파일로 저장
        String filename = String.format("./scatterImg/analysis_%s.png", filePrefix);
        BitmapEncoder.saveBitmap(chart, filename, BitmapEncoder.BitmapFormat.PNG);
        System.out.printf("산점도 저장 완료: %s%n", filename);
    }

    /**
     * 상관계수 부호에 따른 방향성 해석
     *
     * @param r 피어슨 상관계수
     * @return 방향성 해석 문자열
     */
    private String getDirectionInterpretation(double r) {
        return r > 0 ? "양의 상관관계" : "음의 상관관계";
    }

    /**
     * 상관계수 절댓값에 따른 강도 분류
     *
     * @param absR 상관계수의 절댓값
     * @return 강도 해석 문자열
     *
     * @reference Cohen, J. (1988). Statistical power analysis for the behavioral sciences
     */
    private String getStrengthInterpretation(double absR) {
        if (absR >= 0.7) return "강한 상관관계";
        if (absR >= 0.5) return "중간 상관관계";
        if (absR >= 0.3) return "약한 상관관계";
        return "매우 약한 상관관계";
    }

    /**
     * 피어슨 상관분석 결과를 담는 불변 데이터 클래스
     *
     * @param correlationCoefficient 피어슨 상관계수 (-1 ≤ r ≤ 1)
     * @param pValue p-value (통계적 유의성 검정용)
     * @param sampleSize 표본 크기
     * @param isSignificant 통계적 유의성 여부 (α=0.05 기준)
     * @param isBonferroniSignificant Bonferroni 보정 유의성 여부
     * @param variableName 환경 변수명
     *
     * @since 2.0
     */
    private static class CorrelationResult {
        final double correlationCoefficient;
        final double pValue;
        final int sampleSize;
        final boolean isSignificant;
        final boolean isBonferroniSignificant;
        final String variableName;
        final boolean isStatisticallySignificant; // 최종 유의성 판단용

        /**
         * 상관분석 결과 객체 생성자
         *
         * @param r 피어슨 상관계수
         * @param p p-value
         * @param n 표본 크기
         * @param significant 유의성 여부 (α=0.05)
         * @param bonferroniSignificant Bonferroni 보정 유의성
         * @param varName 변수명
         */
        CorrelationResult(double r, double p, int n, boolean significant, boolean bonferroniSignificant, String varName) {
            this.correlationCoefficient = r;
            this.pValue = p;
            this.sampleSize = n;
            this.isSignificant = significant;
            this.isBonferroniSignificant = bonferroniSignificant;
            this.variableName = varName;

            // 명세서 4.6절: 다중 검정 보정을 적용한 최종 유의성 판단
            this.isStatisticallySignificant = bonferroniSignificant;
        }
    }
}
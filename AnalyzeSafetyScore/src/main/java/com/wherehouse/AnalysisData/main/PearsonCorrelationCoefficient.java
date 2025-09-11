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
 * @version 2.1
 * @since 2025.09.11
 */
@Component
public class PearsonCorrelationCoefficient {

    // 통계적 유의성 검증 기준 (명세서 4.6절)
    private static final double SIGNIFICANCE_LEVEL = 0.05;
    private static final double MIN_CORRELATION_THRESHOLD = 0.3;

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
     * 모든 환경 변수의 상관분석 결과를 종합하여 출력
     *
     * @return 환경 변수별 상관분석 결과 리스트
     */
    public List<CorrelationResult> getSummaryResults() {
        System.out.println("");
        System.out.println("==================================================");
        System.out.println("=== 상관분석 결과 종합 ===");
        System.out.println("==================================================");

        List<CorrelationResult> significantResults = new ArrayList<>();

        // 통계적 유의성 검증을 통과한 변수들만 필터링
        for (CorrelationResult result : allCorrelationResults) {
            if (result.isSignificant && Math.abs(result.correlationCoefficient) >= MIN_CORRELATION_THRESHOLD) {
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
        } else {
            System.out.printf("\n유의미한 변수 개수: %d개 / 전체 %d개%n",
                    significantResults.size(), allCorrelationResults.size());
        }

        return allCorrelationResults;
    }

    /**
     * 피어슨 상관계수 계산 및 p-value 산출
     *
     * @param xData 독립변수 데이터 (환경 변수)
     * @param yData 종속변수 데이터 (범죄율)
     * @param variableName 환경 변수명
     * @return CorrelationResult 상관분석 결과 객체
     *
     * @implNote Apache Commons Math의 내장 p-value 계산 기능 활용
     */
    private CorrelationResult performCorrelationAnalysis(List<Double> xData, List<Double> yData, String variableName) {
        // List를 double[] 배열로 변환
        double[] xArray = xData.stream().mapToDouble(Double::doubleValue).toArray();
        double[] yArray = yData.stream().mapToDouble(Double::doubleValue).toArray();

        // 2x25 행렬 형태로 데이터 준비 (라이브러리의 getCorrelationPValues() 사용을 위해)
        double[][] data = new double[xArray.length][2];
        for (int i = 0; i < xArray.length; i++) {
            data[i][0] = xArray[i];
            data[i][1] = yArray[i];
        }

        // PearsonsCorrelation 객체 생성 (데이터와 함께)
        PearsonsCorrelation correlation = new PearsonsCorrelation(data);

        // 피어슨 상관계수 추출 (X와 Y 간의 상관계수)
//        double r = correlation.getCorrelationMatrix().getEntry(0, 1);
        double r = correlation.correlation(xArray, yArray);

        // p-value 추출
        double pValue = correlation.getCorrelationPValues().getEntry(0, 1);

        int n = xArray.length; // 표본 크기 (25개 자치구)

        // 유의성 판단 (α=0.05 기준)
        boolean isSignificant = pValue < SIGNIFICANCE_LEVEL;

        return new CorrelationResult(r, pValue, n, isSignificant, variableName);
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
     * @param variableName 환경 변수명
     *
     * @since 2.1
     */
    private static class CorrelationResult {
        final double correlationCoefficient;
        final double pValue;
        final int sampleSize;
        final boolean isSignificant;
        final String variableName;

        /**
         * 상관분석 결과 객체 생성자
         *
         * @param r 피어슨 상관계수
         * @param p p-value
         * @param n 표본 크기
         * @param significant 유의성 여부 (α=0.05)
         * @param varName 변수명
         */
        CorrelationResult(double r, double p, int n, boolean significant, String varName) {
            this.correlationCoefficient = r;
            this.pValue = p;
            this.sampleSize = n;
            this.isSignificant = significant;
            this.variableName = varName;
        }
    }
}
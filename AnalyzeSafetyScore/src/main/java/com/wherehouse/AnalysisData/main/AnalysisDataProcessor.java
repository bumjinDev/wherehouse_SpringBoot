package com.wherehouse.AnalysisData.main;

// 기존 import들은 동일하게 유지
import com.wherehouse.AnalysisData.cctv.processor.CctvDataProcessor;
import com.wherehouse.AnalysisData.cinema.processor.CinemaDataProcessor;
import com.wherehouse.AnalysisData.convenience.processor.ConvenienceStoreDataProcessor;
import com.wherehouse.AnalysisData.crime.processor.CrimeDataProcessor;
import com.wherehouse.AnalysisData.danran.processor.DanranBarDataProcessor;
import com.wherehouse.AnalysisData.entertainment.processor.EntertainmentDataProcessor;
import com.wherehouse.AnalysisData.hospital.processor.HospitalDataProcessor;
import com.wherehouse.AnalysisData.karaoke.processor.KaraokeRoomDataProcessor;
import com.wherehouse.AnalysisData.lodging.processor.LodgingDataProcessor;
import com.wherehouse.AnalysisData.mart.processor.MartDataProcessor;
import com.wherehouse.AnalysisData.pcbang.processor.PcBangDataProcessor;
import com.wherehouse.AnalysisData.police.processor.PoliceFacilityDataProcessor;
import com.wherehouse.AnalysisData.population.processor.PopulationDataProcessor;
import com.wherehouse.AnalysisData.residentcenter.processor.ResidentCenterDataProcessor;
import com.wherehouse.AnalysisData.school.processor.SchoolDataProcessor;
import com.wherehouse.AnalysisData.streetlight.processor.StreetlightDataProcessor;
import com.wherehouse.AnalysisData.subway.processor.SubwayStationDataProcessor;
import com.wherehouse.AnalysisData.university.processor.UniversityDataProcessor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

/**
 * 단순화된 안전성 분석용 데이터 처리 파이프라인
 * - t-값, F-값 등 복잡한 통계량 제거
 * - 핵심 지표(R², 회귀계수, VIF)만 출력
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisDataProcessor implements CommandLineRunner {

    // 의존성 주입은 기존과 동일
    private final CrimeDataProcessor crimeDataProcessor;
    private final PopulationDataProcessor populationDataProcessor;
    private final CctvDataProcessor cctvDataProcessor;
    private final PcBangDataProcessor pcBangDataProcessor;
    private final StreetlightDataProcessor streetlightDataProcessor;
    private final HospitalDataProcessor hospitalDataProcessor;
    private final PoliceFacilityDataProcessor policeFacilityDataProcessor;
    private final KaraokeRoomDataProcessor karaokeRoomDataProcessor;
    private final DanranBarDataProcessor danranBarDataProcessor;
    private final SchoolDataProcessor schoolDataProcessor;
    private final MartDataProcessor martDataProcessor;
    private final ResidentCenterDataProcessor residentCenterDataProcessor;
    private final LodgingDataProcessor lodgingDataProcessor;
    private final CinemaDataProcessor cinemaDataProcessor;
    private final EntertainmentDataProcessor entertainmentDataProcessor;
    private final SubwayStationDataProcessor subwayStationDataProcessor;
    private final ConvenienceStoreDataProcessor convenienceStoreDataProcessor;
    private final UniversityDataProcessor universityDataProcessor;

    private final PearsonCorrelationCoefficient pearsonCorrelationCoefficient;
    private final MultipleRegressionAnalysis multipleRegressionAnalysis;

    Map<String, Map<String, Long>> analysisDataSet = new HashMap<>();

    @Override
    public void run(String... args) throws Exception {

        log.info("==================================================");
        log.info("=== 안전성 분석용 데이터 ETL 프로세스 시작 ===");
        log.info("==================================================");

        long totalStartTime = System.currentTimeMillis();

        try {
            // 데이터 수집 단계 (기존과 동일)
            analysisDataSet.put("district_crime_statistics", executeProcessor("범죄 통계", crimeDataProcessor::getCrimeCountMapByDistrict));
            analysisDataSet.put("district_cctv_infrastructure", executeProcessor("CCTV 인프라", cctvDataProcessor::getCctvCountMapByDistrict));
            analysisDataSet.put("district_cinema_facilities", executeProcessor("영화관 시설", cinemaDataProcessor::getActiveCinemaCountMapByDistrict));
            analysisDataSet.put("district_convenience_stores", executeProcessor("편의점 현황", convenienceStoreDataProcessor::getActiveConvenienceStoreCountMapByDistrict));
            analysisDataSet.put("district_population_density", executeProcessor("인구 밀도", populationDataProcessor::getPopulationCountOnlyMapByDistrict));
            analysisDataSet.put("district_pcbang_facilities", executeProcessor("PC방 시설", pcBangDataProcessor::getPcBangCountMapByDistrict));
            analysisDataSet.put("district_streetlight_coverage", executeProcessor("가로등 인프라", streetlightDataProcessor::getStreetlightCountMapByDistrict));
            analysisDataSet.put("district_medical_facilities", executeProcessor("의료 시설", hospitalDataProcessor::getActiveHospitalCountMapByDistrict));
            analysisDataSet.put("district_police_infrastructure", executeProcessor("치안 인프라", policeFacilityDataProcessor::getPoliceFacilityCountMapByDistrict));
            analysisDataSet.put("district_karaoke_entertainment", executeProcessor("노래연습장", karaokeRoomDataProcessor::getActiveKaraokeRoomCountMapByDistrict));
            analysisDataSet.put("district_bar_nightlife", executeProcessor("단란주점", danranBarDataProcessor::getDanranBarCountMapByDistrict));
            analysisDataSet.put("district_education_facilities", executeProcessor("교육 시설", schoolDataProcessor::getActiveSchoolCountMapByDistrict));
            analysisDataSet.put("district_retail_infrastructure", executeProcessor("대형마트/백화점", martDataProcessor::getMartCountMapByDistrict));
            analysisDataSet.put("district_civic_services", executeProcessor("주민센터", residentCenterDataProcessor::getResidentCenterCountMapByDistrict));
            analysisDataSet.put("district_accommodation_services", executeProcessor("숙박업", lodgingDataProcessor::getLodgingCountMapByDistrict));
            analysisDataSet.put("district_adult_entertainment", executeProcessor("유흥주점", entertainmentDataProcessor::getEntertainmentCountMapByDistrict));
            analysisDataSet.put("district_transit_connectivity", executeProcessor("지하철역", subwayStationDataProcessor::getSubwayStationCountMapByDistrict));
            analysisDataSet.put("district_higher_education", executeProcessor("고등교육기관", universityDataProcessor::getUniversityCountMapByDistrict));

            // 범죄율 계산 (기존과 동일)
            HashMap<String, Double> districtCrime = new HashMap<>();
            getCrimeDataProcessor(districtCrime, analysisDataSet.get("district_population_density"), analysisDataSet.get("district_crime_statistics"));
            LinkedHashMap<String, Double> sortedCrimeData = DataConverter.convertAndSort(districtCrime);

            // 피어슨 상관분석 (기존과 동일)
            log.info("==================================================");
            log.info("=== 피어슨 상관분석 시작: 17개 환경 변수 vs 범죄율 ===");
            log.info("==================================================");

            // 각 변수별 상관분석 수행
            LinkedHashMap<String, Double> sortedCctvData = DataConverter.convertAndSort(analysisDataSet.get("district_cctv_infrastructure"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("CCTV 인프라", sortedCrimeData, sortedCctvData, "cctv");

            LinkedHashMap<String, Double> sortedCinemaData = DataConverter.convertAndSort(analysisDataSet.get("district_cinema_facilities"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("영화관 시설", sortedCrimeData, sortedCinemaData, "cinema");

            LinkedHashMap<String, Double> sortedConvenienceData = DataConverter.convertAndSort(analysisDataSet.get("district_convenience_stores"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("편의점", sortedCrimeData, sortedConvenienceData, "convenience");

            LinkedHashMap<String, Double> sortedPcBangData = DataConverter.convertAndSort(analysisDataSet.get("district_pcbang_facilities"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("PC방", sortedCrimeData, sortedPcBangData, "pcbang");

            LinkedHashMap<String, Double> sortedStreetlightData = DataConverter.convertAndSort(analysisDataSet.get("district_streetlight_coverage"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("가로등", sortedCrimeData, sortedStreetlightData, "streetlight");

            LinkedHashMap<String, Double> sortedHospitalData = DataConverter.convertAndSort(analysisDataSet.get("district_medical_facilities"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("의료 시설", sortedCrimeData, sortedHospitalData, "hospital");

            LinkedHashMap<String, Double> sortedPoliceData = DataConverter.convertAndSort(analysisDataSet.get("district_police_infrastructure"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("경찰 시설", sortedCrimeData, sortedPoliceData, "police");

            LinkedHashMap<String, Double> sortedKaraokeData = DataConverter.convertAndSort(analysisDataSet.get("district_karaoke_entertainment"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("노래연습장", sortedCrimeData, sortedKaraokeData, "karaoke");

            LinkedHashMap<String, Double> sortedBarData = DataConverter.convertAndSort(analysisDataSet.get("district_bar_nightlife"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("단란주점", sortedCrimeData, sortedBarData, "bar");

            LinkedHashMap<String, Double> sortedSchoolData = DataConverter.convertAndSort(analysisDataSet.get("district_education_facilities"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("교육 시설", sortedCrimeData, sortedSchoolData, "school");

            LinkedHashMap<String, Double> sortedMartData = DataConverter.convertAndSort(analysisDataSet.get("district_retail_infrastructure"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("대형마트", sortedCrimeData, sortedMartData, "mart");

            LinkedHashMap<String, Double> sortedResidentCenterData = DataConverter.convertAndSort(analysisDataSet.get("district_civic_services"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("주민센터", sortedCrimeData, sortedResidentCenterData, "residentcenter");

            LinkedHashMap<String, Double> sortedLodgingData = DataConverter.convertAndSort(analysisDataSet.get("district_accommodation_services"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("숙박업", sortedCrimeData, sortedLodgingData, "lodging");

            LinkedHashMap<String, Double> sortedEntertainmentData = DataConverter.convertAndSort(analysisDataSet.get("district_adult_entertainment"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("유흥주점", sortedCrimeData, sortedEntertainmentData, "entertainment");

            LinkedHashMap<String, Double> sortedSubwayData = DataConverter.convertAndSort(analysisDataSet.get("district_transit_connectivity"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("지하철역", sortedCrimeData, sortedSubwayData, "subway");

            LinkedHashMap<String, Double> sortedUniversityData = DataConverter.convertAndSort(analysisDataSet.get("district_higher_education"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("고등교육기관", sortedCrimeData, sortedUniversityData, "university");

            LinkedHashMap<String, Double> sortedPopulationData = DataConverter.convertAndSort(analysisDataSet.get("district_population_density"));
            pearsonCorrelationCoefficient.analyzeCrimeCorrelation("인구 밀도", sortedCrimeData, sortedPopulationData, "population");

            log.info("==================================================");
            log.info("=== 모든 피어슨 상관분석 완료 ===");
            log.info("==================================================");

            // 단순화된 다중회귀분석
            log.info("==================================================");
            log.info("=== 다중회귀분석 시작: 주요 8개 변수 vs 범죄율 ===");
            log.info("==================================================");

            MultipleRegressionAnalysis.RegressionAnalysisResult regressionResult =
                    multipleRegressionAnalysis.performMultipleRegression(
                            sortedCrimeData,              // 종속변수: 범죄율
                            sortedPoliceData,             // 독립변수 1: 경찰시설
                            sortedKaraokeData,            // 독립변수 2: 노래연습장
                            sortedSchoolData,             // 독립변수 3: 교육시설
                            sortedMartData,               // 독립변수 4: 대형마트
                            sortedLodgingData,            // 독립변수 5: 숙박업
                            sortedEntertainmentData,      // 독립변수 6: 유흥주점
                            sortedSubwayData,             // 독립변수 7: 지하철역
                            sortedPopulationData          // 독립변수 8: 인구밀도
                    );

            // 방향성 가중치 산출 및 출력
            Map<String, MultipleRegressionAnalysis.WeightInfo> directionalWeights =
                    regressionResult.calculateDirectionalWeights();

            printDirectionalWeights(directionalWeights, regressionResult);

            log.info("==================================================");
            log.info("=== 안전성 점수 모델 분석 완료 ===");
            log.info("==================================================");

        } catch (Exception e) {
            log.error("!!! 데이터 처리 파이프라인 실행 중 심각한 오류 발생 !!!", e);
            throw e;
        }

        long totalEndTime = System.currentTimeMillis();
        log.info("==================================================");
        log.info("=== 모든 데이터 처리 완료. 총 소요 시간: {}ms", (totalEndTime - totalStartTime));
        log.info("==================================================");
    }

    /**
     * 단순화된 방향성 가중치 출력
     */
    private void printDirectionalWeights(Map<String, MultipleRegressionAnalysis.WeightInfo> directionalWeights,
                                         MultipleRegressionAnalysis.RegressionAnalysisResult result) {

        log.info("==================================================");
        log.info("=== 다중회귀분석 기반 방향성 가중치 산출 결과 ===");
        log.info("==================================================");

        if (!directionalWeights.isEmpty()) {
            System.out.println("\n=== 방향성 고려 변수별 가중치 ===");

            // 위험 요소 출력
            System.out.println("\n[ 위험 요소 (범죄율 증가 효과) ]");
            double totalRiskWeight = 0.0;
            int riskCount = 0;
            for (Map.Entry<String, MultipleRegressionAnalysis.WeightInfo> entry : directionalWeights.entrySet()) {
                if ("위험요소".equals(entry.getValue().type)) {
                    System.out.printf("%-15s: %6.2f%% (계수=%.4f)\n",
                            entry.getKey(), entry.getValue().weight, entry.getValue().originalCoeff);
                    totalRiskWeight += entry.getValue().weight;
                    riskCount++;
                }
            }

            // 안전 요소 출력
            System.out.println("\n[ 안전 요소 (범죄율 감소 효과) ]");
            double totalSafetyWeight = 0.0;
            int safetyCount = 0;
            for (Map.Entry<String, MultipleRegressionAnalysis.WeightInfo> entry : directionalWeights.entrySet()) {
                if ("안전요소".equals(entry.getValue().type)) {
                    System.out.printf("%-15s: %6.2f%% (계수=%.4f)\n",
                            entry.getKey(), entry.getValue().weight, entry.getValue().originalCoeff);
                    totalSafetyWeight += entry.getValue().weight;
                    safetyCount++;
                }
            }

            // 요약 정보
            System.out.printf("\n위험 요소 가중치 합계: %.1f%% (%d개 변수)\n", totalRiskWeight, riskCount);
            System.out.printf("안전 요소 가중치 합계: %.1f%% (%d개 변수)\n", totalSafetyWeight, safetyCount);
            System.out.printf("총 유의미한 변수 개수: %d개\n", directionalWeights.size());

            // 샘플 안전성 점수 계산 예시
            printSafetyScoreExamples(directionalWeights, result);

        } else {
            log.warn("다중회귀분석에서 통계적으로 유의미한 변수가 발견되지 않았습니다.");
        }
    }

    /**
     * 안전성 점수 계산 예시 출력
     */
    private void printSafetyScoreExamples(Map<String, MultipleRegressionAnalysis.WeightInfo> weights,
                                          MultipleRegressionAnalysis.RegressionAnalysisResult result) {

        System.out.println("\n=== 샘플 안전성 점수 계산 예시 ===");

        // 강남구 예시 (유흥주점 많음, 인구밀도 높음)
        Map<String, Double> 강남구예시 = Map.of(
                "유흥주점", 0.8,  // 정규화 값 (0~1)
                "인구밀도", 0.9   // 정규화 값 (0~1)
        );
        double 강남구점수 = result.calculateSafetyScore(강남구예시, weights);
        System.out.printf("강남구 예시 (유흥주점=0.8, 인구밀도=0.9): %.1f점\n", 강남구점수);

        // 도봉구 예시 (유흥주점 적음, 인구밀도 낮음)
        Map<String, Double> 도봉구예시 = Map.of(
                "유흥주점", 0.2,  // 정규화 값 (0~1)
                "인구밀도", 0.3   // 정규화 값 (0~1)
        );
        double 도봉구점수 = result.calculateSafetyScore(도봉구예시, weights);
        System.out.printf("도봉구 예시 (유흥주점=0.2, 인구밀도=0.3): %.1f점\n", 도봉구점수);
    }

    // 기존 헬퍼 메서드들은 동일하게 유지
    private Map<String, Long> executeProcessor(String processorName, Supplier<Map<String, Long>> task) {
        long startTime = System.currentTimeMillis();
        Map<String, Long> dataMap = task.get();
        log.info("[데이터 요약] 총 {}개 자치구 데이터 수집 완료", dataMap.size());
        return dataMap;
    }

    public void getCrimeDataProcessor(HashMap<String, Double> districtCrime, Map<String, Long> district_population_density, Map<String, Long> district_crime_statistics) {
        for (Map.Entry<String, Long> populationSet : district_population_density.entrySet()) {
            String key = populationSet.getKey();
            Long population = populationSet.getValue();
            Long crimeTotalCount = district_crime_statistics.get(key);
            Double crimeRate = (crimeTotalCount.doubleValue() / population.doubleValue()) * 100000.0;
            districtCrime.put(key, crimeRate);

            System.out.println(
                    String.format("[지역구] %-5s, [인구수] %-7d, [범죄 횟수] %-5d, [10만명 당 범죄율] %.2f건",
                            key, population, crimeTotalCount, crimeRate)
            );
        }
    }

    @Data
    @AllArgsConstructor
    private static class DataConverter {
        private static final String[] DISTRICT_ORDER = {
                "강남구", "강동구", "강북구", "강서구", "관악구",
                "광진구", "구로구", "금천구", "노원구", "도봉구",
                "동대문구", "동작구", "마포구", "서대문구", "서초구",
                "성동구", "성북구", "송파구", "양천구", "영등포구",
                "용산구", "은평구", "종로구", "중구", "중랑구"
        };

        public static LinkedHashMap<String, Double> convertAndSort(Map<String, ?> sourceMap) {
            LinkedHashMap<String, Double> convertedMap = new LinkedHashMap<>();

            for (String district : DISTRICT_ORDER) {
                Object value = sourceMap.get(district);
                if (value != null) {
                    Double convertedValue;
                    if (value instanceof Number) {
                        convertedValue = ((Number) value).doubleValue();
                    } else if (value instanceof String) {
                        try {
                            convertedValue = Double.parseDouble((String) value);
                        } catch (NumberFormatException e) {
                            convertedValue = 0.0;
                        }
                    } else {
                        convertedValue = 0.0;
                    }
                    convertedMap.put(district, convertedValue);
                } else {
                    convertedMap.put(district, 0.0);
                }
            }
            return convertedMap;
        }
    }
}

package com.wherehouse.AnalysisData.main;

// 각 데이터 도메인별 Processor 임포트 (향후 생성될 클래스들)
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 안전성 분석용 전체 데이터 처리 파이프라인을 총괄하는 오케스트레이터 컴포넌트.
 * Spring Boot 애플리케이션 시작 시 자동으로 run() 메서드를 실행한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisDataProcessor implements CommandLineRunner {

    // 각 데이터 처리 컴포넌트를 의존성 주입 (final 키워드로 불변성 보장)
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

    @Override
    public void run(String... args) throws Exception {
        log.info("==================================================");
        log.info("=== 안전성 분석용 데이터 ETL 프로세스 시작 ===");
        log.info("==================================================");

        Map<String, Map<String, Long>> analysisDataSet = new HashMap<>();

        long totalStartTime = System.currentTimeMillis();

        try {
            // 각 데이터 처리기를 정해진 순서대로 실행 - 개선된 키 네이밍 적용
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

            // ============================================
            //  analysisDataSet.put("district_financial_services", executeProcessor("금융 서비스", bankCountDataProcessor::getBankCountMapByDistrict)); // 현재 데이터는 중복 값 이므로 제외.
        } catch (Exception e) {
            log.error("!!! 데이터 처리 파이프라인 실행 중 심각한 오류 발생 !!!", e);
            throw e; // 애플리케이션을 중단시켜 문제 인지
        }

        long totalEndTime = System.currentTimeMillis();
        log.info("==================================================");
        log.info("=== 모든 데이터 처리 완료. 총 소요 시간: {}ms", (totalEndTime - totalStartTime));
        log.info("==================================================");
    }

    /**
     * 개별 데이터 처리기를 실행하고 소요 시간을 로깅하는 헬퍼 메서드.
     * @param processorName 처리기 이름 (로깅용)
     * @param task 실행할 작업
     * @return 구별 집계 데이터 맵
     */
    private Map<String, Long> executeProcessor(String processorName, Supplier<Map<String, Long>> task) {
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ [처리 시작] {} 데이터 로딩 중...", String.format("%-35s", processorName));
        log.info("└─────────────────────────────────────────────────────────────────┘");

        long startTime = System.currentTimeMillis();

        Map<String, Long> dataMap = task.get();

        // 데이터 요약 정보 출력
        log.info("[데이터 요약] 총 {}개 자치구 데이터 수집 완료", dataMap.size());

        // 상위 5개 구 데이터 미리보기
        dataMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    String barGraph = generateBarGraph(entry.getValue(), getMaxValue(dataMap));
                    log.info("   > {} : {:,}건 {}",
                            String.format("%-8s", entry.getKey()),
                            entry.getValue(),
                            barGraph);
                });

        if (dataMap.size() > 5) {
            log.info("   ... (총 {}개 자치구 중 상위 5개만 표시)", dataMap.size());
        }

        long endTime = System.currentTimeMillis();
        String processingTime = formatProcessingTime(endTime - startTime);

        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ [처리 완료] {} | {} | {}",
                String.format("%-20s", processorName),
                String.format("%d건", dataMap.size()),
                processingTime);
        log.info("└─────────────────────────────────────────────────────────────────┘");
        log.info("");

        return dataMap;
    }

    /**
     * 처리 시간을 사람이 읽기 쉬운 형태로 포맷팅
     */
    private String formatProcessingTime(long milliseconds) {
        if (milliseconds < 1000) {
            return String.format("%dms", milliseconds);
        } else if (milliseconds < 60000) {
            return String.format("%.2fs", milliseconds / 1000.0);
        } else {
            long minutes = milliseconds / 60000;
            long seconds = (milliseconds % 60000) / 1000;
            return String.format("%dm %ds", minutes, seconds);
        }
    }

    /**
     * 맵에서 최대값 찾기
     */
    private Long getMaxValue(Map<String, Long> dataMap) {
        return dataMap.values().stream()
                .max(Long::compareTo)
                .orElse(1L);
    }

    /**
     * 간단한 바 그래프 생성 (비례적 시각화)
     */
    private String generateBarGraph(Long value, Long maxValue) {
        if (maxValue == 0) return "";

        int barLength = (int) (20 * value / maxValue); // 최대 20칸
        StringBuilder bar = new StringBuilder("|");

        for (int i = 0; i < 20; i++) {
            if (i < barLength) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        bar.append("|");

        return bar.toString();
    }
}
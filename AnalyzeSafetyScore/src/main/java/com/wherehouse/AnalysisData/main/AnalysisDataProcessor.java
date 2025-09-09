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
import com.wherehouse.AnalysisData.school.processor.SchoolDataProcessor;

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

    // 20개의 각 데이터 처리 컴포넌트를 의존성 주입 (final 키워드로 불변성 보장)
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
            // 각 데이터 처리기를 정해진 순서대로 실행
//            analysisDataSet.put("crimeData", executeProcessor("범죄 수 통계", crimeDataProcessor::getCrimeCountMapByDistrict));
//            analysisDataSet.put("cctvData", executeProcessor("CCTV", cctvDataProcessor::getCctvCountMapByDistrict));
//             analysisDataSet.put("cinemaData", executeProcessor("영화관", cinemaDataProcessor::getActiveCinemaCountMapByDistrict));
//            analysisDataSet.put("convenienceStoreData", executeProcessor("편의점_영업수", convenienceStoreDataProcessor::getActiveConvenienceStoreCountMapByDistrict));
//            analysisDataSet.put("populationData", executeProcessor("인구 수", populationDataProcessor::getPopulationCountOnlyMapByDistrict));
//            analysisDataSet.put("pcBangData", executeProcessor("PC방", pcBangDataProcessor::getPcBangCountMapByDistrict));
//            analysisDataSet.put("streetlightData", executeProcessor("가로등", streetlightDataProcessor::getStreetlightCountMapByDistrict));
//            analysisDataSet.put("hospitalData", executeProcessor("병원", hospitalDataProcessor::getActiveHospitalCountMapByDistrict));
//            analysisDataSet.put("policeFacilityData", executeProcessor("경찰시설", policeFacilityDataProcessor::getPoliceFacilityCountMapByDistrict));
//            analysisDataSet.put("karaokeRoomData", executeProcessor("노래연습장", karaokeRoomDataProcessor::getActiveKaraokeRoomCountMapByDistrict));
//            analysisDataSet.put("danranBarData", executeProcessor("단란주점", danranBarDataProcessor::getDanranBarCountMapByDistrict));
//            analysisDataSet.put("school", executeProcessor("초중고학교", schoolDataProcessor::getActiveSchoolCountMapByDistrict));
//            analysisDataSet.put("martData", executeProcessor("대형마트/백화점", martDataProcessor::getMartCountMapByDistrict));
//            analysisDataSet.put("residentCenterData", executeProcessor("주민센터", residentCenterDataProcessor::getResidentCenterCountMapByDistrict));
//            analysisDataSet.put("lodgingData", executeProcessor("숙박업", lodgingDataProcessor::getLodgingCountMapByDistrict));
//            analysisDataSet.put("entertainmentData", executeProcessor("유흥주점", entertainmentDataProcessor::getEntertainmentCountMapByDistrict));
//            analysisDataSet.put("subwayStationData", executeProcessor("지하철역", subwayStationDataProcessor::getSubwayStationCountMapByDistrict));
            analysisDataSet.put("schoolData", executeProcessor("대학교", universityDataProcessor::getUniversityCountMapByDistrict));


            // ============================================
            //  analysisDataSet.put("", executeProcessor("은행 수(집계)", bankCountDataProcessor::getBankCountMapByDistrict)); // 현재 데이터는 중복 값 이므로 제외.
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
     */
    private Map<String, Long> executeProcessor(String processorName, Supplier<Map<String, Long>> task) {
        log.info("--- [시작] {} 데이터 조회 ---", processorName);
        long startTime = System.currentTimeMillis();

        Map<String, Long> dataMap = task.get(); // .run() 대신 .get()을 호출하고 결과를 받음

        System.out.println(dataMap.size() + ", size : ");

        for(String keyGu : dataMap.keySet()) {
            System.out.println("[유형 명] " + processorName + ", [구 이름] " + keyGu + ", value: " + dataMap.get(keyGu));
        }

        System.out.println("==========================");

        long endTime = System.currentTimeMillis();
        log.info("--- [완료] {} 데이터 조회. {}건. 소요 시간: {}ms ---", processorName, dataMap.size(), (endTime - startTime));

        return dataMap; // 조회된 데이터를 반환
    }
}
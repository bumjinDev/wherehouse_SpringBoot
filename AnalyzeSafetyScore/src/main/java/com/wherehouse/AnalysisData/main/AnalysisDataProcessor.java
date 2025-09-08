package com.wherehouse.AnalyzeSafetyScore.main;

// 각 데이터 도메인별 Processor 임포트 (향후 생성될 클래스들)
import com.wherehouse.AnalysisData.bankcount.processor.BankCountDataProcessor;
import com.wherehouse.AnalysisData.banklocation.processor.BankLocationDataProcessor;
import com.wherehouse.AnalysisData.cctv.processor.CctvDataProcessor;
import com.wherehouse.AnalysisData.cinema.processor.CinemaDataProcessor;
import com.wherehouse.AnalysisData.convenience.processor.ConvenienceStoreDataProcessor;
import com.wherehouse.AnalysisData.crime.processor.CrimeDataProcessor;
import com.wherehouse.AnalysisData.danran.processor.DanranDataProcessor;
import com.wherehouse.AnalysisData.entertainment.processor.EntertainmentDataProcessor;
import com.wherehouse.AnalysisData.hospital.processor.HospitalDataProcessor;
import com.wherehouse.AnalysisData.karaoke.processor.KaraokeDataProcessor;
import com.wherehouse.AnalysisData.lodging.processor.LodgingDataProcessor;
import com.wherehouse.AnalysisData.mart.processor.MartDataProcessor;
import com.wherehouse.AnalysisData.pcbang.processor.PcBangDataProcessor;
import com.wherehouse.AnalysisData.police.processor.PoliceDataProcessor;
import com.wherehouse.AnalysisData.population.processor.PopulationDataProcessor;
import com.wherehouse.AnalysisData.residentcenter.processor.ResidentCenterDataProcessor;
import com.wherehouse.AnalysisData.school.processor.SchoolDataProcessor;
import com.wherehouse.AnalysisData.streetlight.processor.StreetlightDataProcessor;
import com.wherehouse.AnalysisData.subway.processor.SubwayDataProcessor;
import com.wherehouse.AnalysisData.university.processor.UniversityDataProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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
    private final BankCountDataProcessor bankCountDataProcessor;
    private final BankLocationDataProcessor bankLocationDataProcessor;
    private final CctvDataProcessor cctvDataProcessor;
    private final PcBangDataProcessor pcBangDataProcessor;
    private final StreetlightDataProcessor streetlightDataProcessor;
    private final HospitalDataProcessor hospitalDataProcessor;
    private final PoliceDataProcessor policeDataProcessor;
    private final KaraokeDataProcessor karaokeDataProcessor;
    private final DanranDataProcessor danranDataProcessor;
    private final UniversityDataProcessor universityDataProcessor;
    private final MartDataProcessor martDataProcessor;
    private final ResidentCenterDataProcessor residentCenterDataProcessor;
    private final LodgingDataProcessor lodgingDataProcessor;
    private final CinemaDataProcessor cinemaDataProcessor;
    private final EntertainmentDataProcessor entertainmentDataProcessor;
    private final SubwayDataProcessor subwayDataProcessor;
    private final SchoolDataProcessor schoolDataProcessor;
    private final ConvenienceStoreDataProcessor convenienceStoreDataProcessor;

    @Override
    public void run(String... args) throws Exception {
        log.info("==================================================");
        log.info("=== 안전성 분석용 데이터 ETL 프로세스 시작 ===");
        log.info("==================================================");

        long totalStartTime = System.currentTimeMillis();

        try {
            // 각 데이터 처리기를 정해진 순서대로 실행
            executeProcessor("범죄 통계", crimeDataProcessor.processAnalysisCrimeData());
            executeProcessor("인구 밀도", populationDataProcessor.processAnalysisPopulationData());
            executeProcessor("은행 수(집계)", bankCountDataProcessor.processAnalysisBankCountData());
            executeProcessor("은행 위치", bankLocationDataProcessor.processAnalysisBankLocationData());
            executeProcessor("CCTV", cctvDataProcessor.processAnalysisCctvData());
            executeProcessor("PC방", pcBangDataProcessor.processAnalysisPcBangData());
            executeProcessor("가로등", streetlightDataProcessor.processAnalysisStreetlightData());
            executeProcessor("병원", hospitalDataProcessor.processAnalysisHospitalData());
            executeProcessor("경찰시설", policeDataProcessor.processAnalysisPoliceData());
            executeProcessor("노래연습장", karaokeDataProcessor.processAnalysisKaraokeData());
            executeProcessor("단란주점", danranDataProcessor.processAnalysisDanranData());
            executeProcessor("대학교", universityDataProcessor.processAnalysisUniversityData());
            executeProcessor("대형마트/백화점", martDataProcessor.processAnalysisMartData());
            executeProcessor("주민센터", residentCenterDataProcessor.processAnalysisResidentCenterData());
            executeProcessor("숙박업", lodgingDataProcessor.processAnalysisLodgingData());
            executeProcessor("영화관", cinemaDataProcessor.processAnalysisCinemaData());
            executeProcessor("유흥주점", entertainmentDataProcessor.processAnalysisEntertainmentData());
            executeProcessor("지하철역", subwayDataProcessor.processAnalysisSubwayData());
            executeProcessor("초중고등학교", schoolDataProcessor.processAnalysisSchoolData());
            executeProcessor("편의점", convenienceStoreDataProcessor.processAnalysisConvenienceStoreData());

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
     * @param processorTask 실행할 작업 (메서드 참조)
     */
    private void executeProcessor(String processorName, Runnable processorTask) {
        log.info("--- [시작] {} 데이터 처리 ---", processorName);
        long startTime = System.currentTimeMillis();

        processorTask.run();

        long endTime = System.currentTimeMillis();
        log.info("--- [완료] {} 데이터 처리. 소요 시간: {}ms ---", processorName, (endTime - startTime));
    }
}
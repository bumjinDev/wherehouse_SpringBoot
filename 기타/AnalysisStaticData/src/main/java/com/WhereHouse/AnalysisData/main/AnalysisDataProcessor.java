package com.WhereHouse.AnalysisData.main;

import com.WhereHouse.AnalysisData.convenience.processor.ConvenienceStoreCoordinateProcessor;
import com.WhereHouse.AnalysisData.hospital.processor.HospitalDataProcessor;
import com.WhereHouse.AnalysisData.police.processor.PoliceDataProcessor;
import com.WhereHouse.AnalysisData.streetlight.processor.StreetlightDataProcessor;
import com.WhereHouse.AnalysisData.subway.processor.SubwayDataProcessor;
import com.WhereHouse.AnalysisStaticData.StreetLightRaw.Road.StreetlightRawDataLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisDataProcessor implements CommandLineRunner {

    private final StreetlightDataProcessor streetlightDataProcessor;
    private final HospitalDataProcessor hospitalDataProcessor;
    private final PoliceDataProcessor policeDataProcessor;
    private final SubwayDataProcessor subwayDataProcessor;

    @Override
    public void run(String... args) throws Exception {

        log.info("=== 안전성 분석용 데이터 처리 시작 ===");

        try {

            subwayDataProcessor.processAnalysisSubwayData();
//            streetlightDataProcessor.processAnalysisStreetlightData();

            // 메인 처리 프로세스 실행
//            hospitalDataProcessor.processHospitalDataForAnalysis();

            // 경찰시설 데이터 처리 (서울시만)
//            policeDataProcessor.processAnalysisPoliceData();

            // 데이터 품질 검증
//            hospitalDataProcessor.validateDataQuality();

        } catch (Exception e) {
            log.error("안전성 분석용 데이터 처리 중 오류 발생", e);
            throw e;
        }

        log.info("=== 안전성 분석용 데이터 처리 완료 ===");
    }
}
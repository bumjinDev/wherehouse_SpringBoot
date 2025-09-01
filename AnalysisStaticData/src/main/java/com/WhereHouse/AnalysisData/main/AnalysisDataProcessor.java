package com.WhereHouse.AnalysisData.main;

import com.WhereHouse.AnalysisData.crime.processor.CrimeDataProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisDataProcessor implements CommandLineRunner {

    private final CrimeDataProcessor crimeDataProcessor;
    // 향후 18개 ERD별 프로세서 추가 예정
    // private final CctvDataProcessor cctvDataProcessor;
    // private final BankDataProcessor bankDataProcessor;
    // ...

    @Override
    public void run(String... args) throws Exception {
        log.info("=== 안전성 분석용 데이터 처리 시작 ===");

        try {
            // 1. 범죄 데이터 처리
            log.info("1. 범죄 데이터 분석용 테이블 생성 시작");
            crimeDataProcessor.processAnalysisCrimeData();

            // 향후 추가될 18개 ERD별 데이터 처리
            // 2. CCTV 데이터 처리
            // log.info("2. CCTV 데이터 분석용 테이블 생성 시작");
            // cctvDataProcessor.processAnalysisCctvData();

            // 3. 은행 데이터 처리
            // log.info("3. 은행 데이터 분석용 테이블 생성 시작");
            // bankDataProcessor.processAnalysisBankData();

            // ... 추가 프로세서들

        } catch (Exception e) {
            log.error("안전성 분석용 데이터 처리 중 오류 발생", e);
            throw e;
        }

        log.info("=== 안전성 분석용 데이터 처리 완료 ===");
    }
}
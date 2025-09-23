package com.WhereHouse.AnalysisStaticData.CommunityCenterSave.Save;

import com.opencsv.CSVReader;
import com.WhereHouse.AnalysisStaticData.CommunityCenterSave.Entity.ResidentCenter;
import com.WhereHouse.AnalysisStaticData.CommunityCenterSave.Repository.ResidentCenterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResidentCenterDataLoader  {    // implements CommandLineRunner

    private final ResidentCenterRepository residentCenterRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.resident-center-data-path}")
    private String csvFilePath;

    //@Override
    @Transactional
    public void run(String... args) {

        if (residentCenterRepository.count() > 0) {
            log.info("주민센터 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);

            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int savedCount = 0;

                // 헤더 스킵 (첫 번째 행)
                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);

                    if (row.length >= 6) {
                        try {
                            // 서울시 데이터만 처리
                            String sido = cleanString(row[1]);
                            if (sido != null && sido.contains("서울")) {

                                ResidentCenter residentCenter = ResidentCenter.builder()
                                        .serialNo(parseInteger(row[0]))
                                        .sido(sido)
                                        .sigungu(cleanString(row[2]))
                                        .eupmeondong(cleanString(row[3]))
                                        .postalCode(cleanString(row[4]))
                                        .address(cleanString(row[5]))
                                        .phoneNumber(row.length > 6 ? cleanString(row[6]) : null)
                                        .build();

                                if (!residentCenterRepository.existsBySerialNo(residentCenter.getSerialNo())) {
                                    residentCenterRepository.save(residentCenter);
                                    savedCount++;
                                }
                            }

                        } catch (Exception e) {
                            log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        }
                    }
                }

                log.info("서울시 주민센터 데이터 로딩 완료: {}개", savedCount);

            }
        } catch (Exception e) {
            log.error("주민센터 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String cleanString(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
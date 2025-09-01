package com.WhereHouse.APITest.CriminalInfoSave.Save;

import com.opencsv.CSVReader;
import com.WhereHouse.APITest.CriminalInfoSave.entity.CrimeStatistics;
import com.WhereHouse.APITest.CriminalInfoSave.repository.CrimeStatisticsRepository;
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
public class CrimeDataLoader {  // implements CommandLineRunner

    private final CrimeStatisticsRepository crimeRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.crime-data-path}")
    private String csvFilePath;

    //@Override
    @Transactional
    public void run(String... args) {
        if (crimeRepository.count() > 0) {
            log.info("데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int savedCount = 0;
                for (int i = 5; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    if (row.length >= 14 && "합계".equals(row[0]) && !row[1].equals("소계")) {
                        CrimeStatistics crime = CrimeStatistics.builder()
                                .districtName(row[1].trim())
                                .year(2023)
                                .totalOccurrence(parseInteger(row[2]))
                                .totalArrest(parseInteger(row[3]))
                                .murderOccurrence(parseInteger(row[4]))
                                .murderArrest(parseInteger(row[5]))
                                .robberyOccurrence(parseInteger(row[6]))
                                .robberyArrest(parseInteger(row[7]))
                                .sexualCrimeOccurrence(parseInteger(row[8]))
                                .sexualCrimeArrest(parseInteger(row[9]))
                                .theftOccurrence(parseInteger(row[10]))
                                .theftArrest(parseInteger(row[11]))
                                .violenceOccurrence(parseInteger(row[12]))
                                .violenceArrest(parseInteger(row[13]))
                                .build();

                        crimeRepository.save(crime);
                        savedCount++;
                    }
                }
                log.info("{} 개 자치구 데이터 저장 완료", savedCount);
            }
        } catch (Exception e) {
            log.error("CSV 로딩 실패: {}", e.getMessage());
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
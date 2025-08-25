package com.WhereHouse.API.Test.APITest.CCTVRaw.Road;

import com.opencsv.CSVReader;
import com.WhereHouse.API.Test.APITest.CCTVRaw.Entity.StreetlightRawData;
import com.WhereHouse.API.Test.APITest.CCTVRaw.Repository.StreetlightRawDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreetlightRawDataLoader implements CommandLineRunner {

    private final StreetlightRawDataRepository streetlightRawDataRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.streetlight-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (streetlightRawDataRepository.count() > 0) {
            log.info("원본 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int totalRows = csvData.size() - 1; // 헤더 제외
                int savedCount = 0;
                int errorCount = 0;

                log.info("가로등 원본 데이터 로딩 시작 - 총 {} 건", totalRows);

                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);

                    // 진행률 출력 (100건마다)
                    if (i % 100 == 0) {
                        double progress = ((double) (i - 1) / totalRows) * 100;
                        log.info("진행률: {:.1f}% ({}/{})", progress, i - 1, totalRows);
                    }

                    if (row.length >= 3) {
                        try {
                            StreetlightRawData streetlightRaw = StreetlightRawData.builder()
                                    .managementNumber(parseString(row[0]))
                                    .latitude(parseBigDecimal(row[1]))
                                    .longitude(parseBigDecimal(row[2]))
                                    .build();

                            streetlightRawDataRepository.save(streetlightRaw);
                            savedCount++;
                        } catch (Exception e) {
                            errorCount++;
                            log.warn("{}번째 행 저장 실패: {}", i, e.getMessage());
                        }
                    } else {
                        errorCount++;
                        log.warn("{}번째 행 컬럼 부족 ({}개 < 3개)", i, row.length);
                    }
                }

                log.info("가로등 원본 데이터 로딩 완료 - 성공: {}건, 실패: {}건, 전체: {}건",
                        savedCount, errorCount, totalRows);
            }
        } catch (Exception e) {
            log.error("CSV 로딩 실패: {}", e.getMessage());
        }
    }

    private String parseString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        return value.trim();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
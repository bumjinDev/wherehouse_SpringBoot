package com.WhereHouse.AnalysisStaticData.CCTVInfoRoad.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.AnalysisStaticData.CCTVInfoRoad.Entity.CctvStatistics;
import com.WhereHouse.AnalysisStaticData.CCTVInfoRoad.Repository.CctvStatisticsRepository;
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
public class CctvDataLoader  {  // implements CommandLineRunner

    private final CctvStatisticsRepository cctvRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.cctv-data-path}")
    private String csvFilePath;

    //@Override
    @Transactional
    public void run(String... args) {
        if (cctvRepository.count() > 0) {
            log.info("데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int savedCount = 0;
                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    if (row.length >= 14) {
                        CctvStatistics cctv = CctvStatistics.builder()
                                .managementAgency(parseString(row[1]))
                                .roadAddress(parseString(row[2]))
                                .jibunAddress(parseString(row[3]))
                                .installPurpose(parseString(row[4]))
                                .cameraCount(parseInteger(row[5]))
                                .cameraPixel(parseInteger(row[6]))
                                .shootingDirection(parseString(row[7]))
                                .storageDays(parseInteger(row[8]))
                                .installDate(parseString(row[9]))
                                .managementPhone(parseString(row[10]))
                                .wgs84Latitude(parseDouble(row[11]))
                                .wgs84Longitude(parseDouble(row[12]))
                                .dataBaseDate(parseString(row[13]))
                                .build();

                        cctvRepository.save(cctv);
                        savedCount++;
                    }
                }
                log.info("{} 개 CCTV 데이터 저장 완료", savedCount);
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

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
package com.WhereHouse.APITest.StreetLightRaw.Road;

import com.opencsv.CSVReader;
import com.WhereHouse.APITest.StreetLightRaw.Entity.StreetlightRawData;
import com.WhereHouse.APITest.StreetLightRaw.Repository.StreetlightRawDataRepository;
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
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreetlightRawDataLoader  {    // implements CommandLineRunner

    private final StreetlightRawDataRepository streetlightRawDataRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.streetlight-data-path}")
    private String csvFilePath;

//    @Override
    @Transactional
    public void run(String... args) {
        if (streetlightRawDataRepository.count() > 0) {
            log.info("가로등 원본 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            log.info("CSV 파일 경로: {}", csvFilePath);
            log.info("CSV 파일 존재 여부: {}", resource.exists());

            if (!resource.exists()) {
                log.error("CSV 파일이 존재하지 않습니다: {}", csvFilePath);
                return;
            }

            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                log.info("총 CSV 행 수: {}", csvData.size());

                if (csvData.size() == 0) {
                    log.warn("CSV 파일이 비어있습니다");
                    return;
                }

                if (csvData.size() > 0) {
                    log.info("첫 번째 행 컬럼 수: {}", csvData.get(0).length);
                    log.info("첫 번째 행 내용: {}", Arrays.toString(csvData.get(0)));
                }

                if (csvData.size() > 1) {
                    log.info("두 번째 행 컬럼 수: {}", csvData.get(1).length);
                    log.info("두 번째 행 내용: {}", Arrays.toString(csvData.get(1)));
                }

                int savedCount = 0;
                int skipCount = 0;

                // 첫 번째 행은 헤더이므로 스킵
                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);

                    if (row.length < 3) {
                        skipCount++;
                        log.debug("행 {}: 컬럼 수 부족 ({}개), 스킵", i, row.length);
                        continue;
                    }

                    try {
                        StreetlightRawData streetlight = StreetlightRawData.builder()
                                .managementNumber(processStringWithLength(row[0], 100, "관리번호"))
                                .latitude(parseDouble(row[1]))
                                .longitude(parseDouble(row[2]))
                                .build();

                        streetlightRawDataRepository.save(streetlight);
                        savedCount++;

                        if (savedCount % 1000 == 0) {
                            log.info("{}개 가로등 데이터 저장 중...", savedCount);
                        }
                    } catch (Exception e) {
                        skipCount++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        if (skipCount <= 5) {
                            log.debug("오류 발생한 행 데이터: {}", Arrays.toString(row));
                        }
                    }
                }

                log.info("=== 가로등 원본 데이터 로딩 완료 ===");
                log.info("총 처리 행 수: {}", csvData.size() - 1);
                log.info("저장 성공: {}개", savedCount);
                log.info("처리 실패: {}개", skipCount);
            }
        } catch (Exception e) {
            log.error("가로등 원본 데이터 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private String processStringWithLength(String value, int maxLength, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        String trimmed = value.trim();

        if (trimmed.length() > maxLength) {
            log.warn("{} 필드 길이 초과 (실제: {}, 최대: {}): {}",
                    fieldName, trimmed.length(), maxLength,
                    trimmed.length() > 30 ? trimmed.substring(0, 30) + "..." : trimmed);
            return trimmed.substring(0, maxLength);
        }

        return trimmed;
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.debug("Double 파싱 실패: {}", value);
            return 0.0;
        }
    }
}
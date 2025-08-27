package com.WhereHouse.APITest.SubwayStation.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.APITest.SubwayStation.Entity.SubwayStation;
import com.WhereHouse.APITest.SubwayStation.Repository.SubwayStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubwayStationDataLoader implements CommandLineRunner {

    private final SubwayStationRepository subwayStationRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.subway-station-path}")
    private String csvFilePath;

    private static final int BATCH_SIZE = 500;

    @Override
    public void run(String... args) {
        long existingCount = subwayStationRepository.count();
        if (existingCount > 0) {
            log.info("지하철역 데이터 이미 존재 ({}개). 로딩 스킵", existingCount);
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                log.info("CSV 파일에서 {} 행 읽음 (헤더 포함)", csvData.size());

                List<SubwayStation> batch = new ArrayList<>();
                int totalSaved = 0;
                int totalErrors = 0;

                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    try {
                        if (row.length >= 6) {
                            SubwayStation station = createSubwayStation(row);
                            batch.add(station);

                            if (batch.size() >= BATCH_SIZE || i == csvData.size() - 1) {
                                int saved = saveBatch(batch);
                                totalSaved += saved;
                                batch.clear();

                                if (totalSaved % (BATCH_SIZE * 2) == 0) {
                                    log.info("진행 상황: {} 개 데이터 저장 완료", totalSaved);
                                }
                            }
                        }
                    } catch (Exception e) {
                        totalErrors++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                    }
                }

                long finalCount = subwayStationRepository.count();
                log.info("로딩 완료 - 처리 시도: {} 개, 최종 DB 저장: {} 개, 오류: {} 개",
                        totalSaved, finalCount, totalErrors);
            }
        } catch (Exception e) {
            log.error("지하철역 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveBatch(List<SubwayStation> batch) {
        try {
            List<SubwayStation> saved = subwayStationRepository.saveAll(batch);
            return saved.size();
        } catch (Exception e) {
            log.error("배치 저장 실패: {}", e.getMessage());

            int savedCount = 0;
            for (SubwayStation station : batch) {
                try {
                    subwayStationRepository.save(station);
                    savedCount++;
                } catch (Exception individualError) {
                    log.warn("개별 저장 실패 - 역코드: {}", station.getStationCode());
                }
            }

            log.info("개별 저장 완료: {} / {} 개", savedCount, batch.size());
            return savedCount;
        }
    }

    private SubwayStation createSubwayStation(String[] row) {
        return SubwayStation.builder()
                .stationCode(parseString(row[0]))
                .stationNameKor(parseString(row[1]))
                .stationNameEng(parseString(row[2]))
                .lineNumber(parseString(row[3]))
                .externalCode(parseString(row[4]))
                .stationNameChn(row.length > 5 ? parseString(row[5]) : null)
                .stationNameJpn(row.length > 6 ? parseString(row[6]) : null)
                .build();
    }

    private String parseString(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return null;
        }
        String trimmed = value.trim();

        if (trimmed.length() > 190) {
            trimmed = trimmed.substring(0, 190);
            log.debug("문자열 길이 제한으로 자름: 원본 길이 {} -> 190", value.length());
        }

        return trimmed;
    }
}
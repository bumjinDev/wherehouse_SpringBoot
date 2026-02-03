package com.WhereHouse.AnalysisStaticData.SubwayStation.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.AnalysisStaticData.SubwayStation.Entity.SubwayStation;
import com.WhereHouse.AnalysisStaticData.SubwayStation.Repository.SubwayStationRepository;
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
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubwayStationDataLoader { // implements CommandLineRunner

    private final SubwayStationRepository subwayStationRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.subway-station-path}")
    private String csvFilePath;

    private static final int BATCH_SIZE = 100;

//    @Override
    public void run(String... args) {
        long existingCount = subwayStationRepository.count();
        if (existingCount > 0) {
            log.info("지하철역 데이터 이미 존재 ({}개). 로딩 스킵", existingCount);
            return;
        }

        log.info("지하철역 데이터 로딩 시작...");
        loadSubwayStationData();
    }

    @Transactional
    public void loadSubwayStationData() {
        try {
            Resource resource = resourceLoader.getResource(csvFilePath);

            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                log.info("CSV 파일에서 {} 행 읽음 (헤더 포함)", csvData.size());

                if (csvData.size() <= 1) {
                    log.warn("CSV 파일에 데이터가 없습니다.");
                    return;
                }

                String[] header = csvData.get(0);
                log.info("CSV 헤더: {}", String.join(", ", header));

                List<SubwayStation> batch = new ArrayList<>();
                int totalSaved = 0;
                int totalErrors = 0;

                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    try {
                        if (isValidRow(row)) {
                            SubwayStation station = createSubwayStationFromCsv(row, i);

                            if (!subwayStationRepository.existsByStationNumberAndLineNumber(
                                    station.getStationNumber(), station.getLineNumber())) {
                                batch.add(station);
                            }

                            if (batch.size() >= BATCH_SIZE) {
                                int saved = saveBatch(batch);
                                totalSaved += saved;
                                batch.clear();
                                log.info("진행 상황: {} 개 데이터 저장 완료", totalSaved);
                            }
                        } else {
                            totalErrors++;
                        }
                    } catch (Exception e) {
                        totalErrors++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                    }
                }

                if (!batch.isEmpty()) {
                    totalSaved += saveBatch(batch);
                }

                long finalCount = subwayStationRepository.count();
                log.info("로딩 완료 - 처리된 행: {}, 최종 DB 저장: {} 개, 오류: {} 개",
                        csvData.size() - 1, finalCount, totalErrors);

            }
        } catch (Exception e) {
            log.error("지하철역 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private boolean isValidRow(String[] row) {
        return row != null && row.length >= 7;
    }

    private SubwayStation createSubwayStationFromCsv(String[] row, int rowIndex) {
        try {
            return SubwayStation.builder()
                    .seqNo(parseInteger(row[0], rowIndex))           // 연번
                    .stationNumber(parseString(row[1]))             // 역번호
                    .lineNumber(parseString(row[2]))                // 호선
                    .stationNameKor(parseString(row[3]))            // 역명
                    .stationPhone(parseString(row[4]))              // 역전화번호
                    .roadAddress(parseString(row[5]))               // 도로명주소
                    .jibunAddress(parseString(row[6]))              // 지번주소
                    .build();
        } catch (Exception e) {
            log.error("행 {} 파싱 중 오류: {}", rowIndex, e.getMessage());
            throw new RuntimeException("CSV 행 파싱 실패", e);
        }
    }

    private int saveBatch(List<SubwayStation> batch) {
        try {
            List<SubwayStation> saved = subwayStationRepository.saveAll(batch);
            return saved.size();
        } catch (Exception e) {
            log.error("배치 저장 실패, 개별 저장 시도: {}", e.getMessage());

            int savedCount = 0;
            for (SubwayStation station : batch) {
                try {
                    subwayStationRepository.save(station);
                    savedCount++;
                } catch (Exception individualError) {
                    log.warn("개별 저장 실패 - 역번호: {}", station.getStationNumber());
                }
            }

            return savedCount;
        }
    }

    private String parseString(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private Integer parseInteger(String value, int rowIndex) {
        try {
            if (value == null || value.trim().isEmpty()) {
                return rowIndex;
            }
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return rowIndex;
        }
    }
}
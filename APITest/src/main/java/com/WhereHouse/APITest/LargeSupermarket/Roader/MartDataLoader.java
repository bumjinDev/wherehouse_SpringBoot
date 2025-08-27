package com.WhereHouse.APITest.LargeSupermarket.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.APITest.LargeSupermarket.Entity.MartStatistics;
import com.WhereHouse.APITest.LargeSupermarket.Repository.MartStatisticsRepository;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MartDataLoader implements CommandLineRunner {

    private final MartStatisticsRepository martRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.mart-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (martRepository.count() > 0) {
            log.info("대형마트/백화점 데이터 이미 존재. 로딩 스킵");
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

                    if (row.length < 26) {
                        skipCount++;
                        log.debug("행 {}: 컬럼 수 부족 ({}개), 스킵", i, row.length);
                        continue;
                    }

                    try {
                        MartStatistics mart = MartStatistics.builder()
                                .localGovtCode(processString(row[0]))
                                .managementNo(processString(row[1]))
                                .licenseDate(parseDate(row[2]))
                                .licenseCancelDate(parseDate(row[3]))
                                .businessStatusCode(processString(row[4]))
                                .businessStatusName(processString(row[5]))
                                .detailStatusCode(processString(row[6]))
                                .detailStatusName(processString(row[7]))
                                .closureDate(parseDate(row[8]))
                                .suspensionStartDate(parseDate(row[9]))
                                .suspensionEndDate(parseDate(row[10]))
                                .reopenDate(parseDate(row[11]))
                                .phoneNumber(processString(row[12]))
                                .locationArea(parseDouble(row[13]))
                                .locationPostalCode(processString(row[14]))
                                .address(processString(row[15]))
                                .roadAddress(processString(row[16]))
                                .roadPostalCode(processString(row[17]))
                                .businessName(processString(row[18]))
                                .lastUpdateDate(parseDate(row[19]))
                                .dataUpdateType(processString(row[20]))
                                .dataUpdateDate(processString(row[21]))
                                .businessTypeName(processString(row[22]))
                                .coordX(parseDouble(row[23]))
                                .coordY(parseDouble(row[24]))
                                .storeTypeName(processString(row[25]))
                                .build();

                        martRepository.save(mart);
                        savedCount++;

                        if (savedCount % 100 == 0) {
                            log.info("{}개 대형마트/백화점 데이터 저장 중...", savedCount);
                        }
                    } catch (Exception e) {
                        skipCount++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        if (skipCount <= 5) {
                            log.debug("오류 발생한 행 데이터: {}", Arrays.toString(row));
                        }
                    }
                }

                log.info("=== 대형마트/백화점 데이터 로딩 완료 ===");
                log.info("총 처리 행 수: {}", csvData.size() - 1);
                log.info("저장 성공: {}개", savedCount);
                log.info("처리 실패: {}개", skipCount);
            }
        } catch (Exception e) {
            log.error("대형마트/백화점 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private String processString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        return value.trim();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "-".equals(dateStr.trim())) {
            return LocalDate.of(1900, 1, 1); // 기본 날짜
        }
        try {
            // YYYY-MM-DD 형식으로 파싱
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            try {
                // YYYY-M-D 형식도 시도
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception ex) {
                log.debug("날짜 파싱 실패: {}", dateStr);
                return LocalDate.of(1900, 1, 1); // 기본 날짜
            }
        }
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
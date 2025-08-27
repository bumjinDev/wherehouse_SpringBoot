package com.WhereHouse.APITest.ElementaryAndMiddleSchool.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.APITest.ElementaryAndMiddleSchool.Entity.SchoolStatistics;
import com.WhereHouse.APITest.ElementaryAndMiddleSchool.Repository.SchoolStatisticsRepository;
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
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchoolDataLoader implements CommandLineRunner {

    private final SchoolStatisticsRepository schoolRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.school-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (schoolRepository.count() > 0) {
            log.info("학교 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int savedCount = 0;
                // 첫 번째 행은 헤더이므로 스킵
                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);
                    if (row.length >= 21) {
                        try {
                            SchoolStatistics school = SchoolStatistics.builder()
                                    .schoolId(row[0].trim())
                                    .schoolName(row[1].trim())
                                    .schoolLevel(row[2].trim())
                                    .establishmentDate(parseDate(row[3]))
                                    .establishmentType(row[4].trim())
                                    .mainBranchType(row[5].trim())
                                    .operationStatus(row[6].trim())
                                    .locationAddress(row[7].trim())
                                    .roadAddress(row[8].trim())
                                    .educationOfficeCode(row[9].trim())
                                    .educationOfficeName(row[10].trim())
                                    .supportOfficeCode(row[11].trim())
                                    .supportOfficeName(row[12].trim())
                                    .createDate(parseDate(row[13]))
                                    .modifyDate(parseDate(row[14]))
                                    .latitude(parseDouble(row[15]))
                                    .longitude(parseDouble(row[16]))
                                    .dataBaseDate(parseDate(row[17]))
                                    .providerCode(row[18].trim())
                                    .providerName(row[19].trim())
                                    .build();

                            schoolRepository.save(school);
                            savedCount++;

                            if (savedCount % 1000 == 0) {
                                log.info("{}개 학교 데이터 저장 중...", savedCount);
                            }
                        } catch (Exception e) {
                            log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        }
                    }
                }
                log.info("총 {}개 학교 데이터 저장 완료", savedCount);
            }
        } catch (Exception e) {
            log.error("학교 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "-".equals(dateStr.trim())) {
            return null;
        }
        try {
            // YYYY-MM-DD 형식으로 파싱
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            try {
                // YYYY-M-D 형식도 시도
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-M-d"));
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
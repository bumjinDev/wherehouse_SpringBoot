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
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchoolDataLoader  {    // implements CommandLineRunner

    private final SchoolStatisticsRepository schoolRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.school-data-path}")
    private String csvFilePath;

//    @Override
    @Transactional
    public void run(String... args) {
        if (schoolRepository.count() > 0) {
            log.info("학교 데이터 이미 존재. 로딩 스킵");
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

                    if (row.length < 20) {
                        skipCount++;
                        log.debug("행 {}: 컬럼 수 부족 ({}개), 스킵", i, row.length);
                        continue;
                    }

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
                                .providerName(row.length > 19 ? row[19].trim() : "")
                                .build();

                        schoolRepository.save(school);
                        savedCount++;

                        if (savedCount % 1000 == 0) {
                            log.info("{}개 학교 데이터 저장 중...", savedCount);
                        }
                    } catch (Exception e) {
                        skipCount++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());
                        if (skipCount <= 5) { // 처음 5개 오류만 상세 로그 출력
                            log.debug("오류 발생한 행 데이터: {}", Arrays.toString(row));
                        }
                    }
                }

                log.info("=== 학교 데이터 로딩 완료 ===");
                log.info("총 처리 행 수: {}", csvData.size() - 1);
                log.info("저장 성공: {}개", savedCount);
                log.info("처리 실패: {}개", skipCount);
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
                log.debug("날짜 파싱 실패: {}", dateStr);
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
            log.debug("Double 파싱 실패: {}", value);
            return null;
        }
    }
}
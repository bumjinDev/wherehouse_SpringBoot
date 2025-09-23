package com.WhereHouse.AnalysisStaticData.EntertainmentBar.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.AnalysisStaticData.EntertainmentBar.Entity.EntertainmentBars;
import com.WhereHouse.AnalysisStaticData.EntertainmentBar.Repository.EntertainmentBarsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntertainmentBarsDataLoader  { // implements CommandLineRunner

    private final EntertainmentBarsRepository entertainmentBarsRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.entertainment-bars-data-path}")
    private String csvFilePath;

//    @Override
    @Transactional
    public void run(String... args) {
        if (entertainmentBarsRepository.count() > 0) {
            log.info("유흥주점 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int totalRows = csvData.size() - 1; // 헤더 제외
                int savedCount = 0;
                int errorCount = 0;

                log.info("유흥주점 데이터 로딩 시작 - 총 {} 건", totalRows);

                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);

                    // 진행률 출력 (100건마다)
                    if (i % 100 == 0) {
                        double progress = ((double) (i - 1) / totalRows) * 100;
                        log.info("진행률: {:.1f}% ({}/{})", progress, i - 1, totalRows);
                    }

                    if (row.length >= 43) {
                        try {
                            EntertainmentBars bar = EntertainmentBars.builder()
                                    .districtCode(parseString(row[0]))
                                    .managementNumber(parseString(row[1]))
                                    .licenseDate(parseDate(row[2]))
                                    .cancelDate(parseDate(row[3]))
                                    .businessStatusCode(parseString(row[4]))
                                    .businessStatusName(parseString(row[5]))
                                    .detailStatusCode(parseString(row[6]))
                                    .detailStatusName(parseString(row[7]))
                                    .closureDate(parseDate(row[8]))
                                    .suspendStartDate(parseDate(row[9]))
                                    .suspendEndDate(parseDate(row[10]))
                                    .reopenDate(parseDate(row[11]))
                                    .phoneNumber(parseString(row[12]))
                                    .areaSize(parseBigDecimal(row[13]))
                                    .postalCode(parseString(row[14]))
                                    .jibunAddress(parseString(row[15]))
                                    .roadAddress(parseString(row[16]))
                                    .roadPostalCode(parseString(row[17]))
                                    .businessName(parseString(row[18]))
                                    .lastModifiedDate(parseDateTime(row[19]))
                                    .dataUpdateType(parseString(row[20]))
                                    .dataUpdateDate(parseDateTime(row[21]))
                                    .businessCategory(parseString(row[22]))
                                    .coordinateX(parseBigDecimal(row[23]))
                                    .coordinateY(parseBigDecimal(row[24]))
                                    .hygieneBusinessType(parseString(row[25]))
                                    .maleEmployees(parseInteger(row[26]))
                                    .femaleEmployees(parseInteger(row[27]))
                                    .surroundingArea(parseString(row[28]))
                                    .gradeCategory(parseString(row[29]))
                                    .waterSupplyType(parseString(row[30]))
                                    .totalEmployees(parseInteger(row[31]))
                                    .headOfficeEmployees(parseInteger(row[32]))
                                    .factoryOfficeEmployees(parseInteger(row[33]))
                                    .factorySalesEmployees(parseInteger(row[34]))
                                    .factoryProductionEmployees(parseInteger(row[35]))
                                    .buildingOwnership(parseString(row[36]))
                                    .guaranteeAmount(parseBigDecimal(row[37]))
                                    .monthlyRent(parseBigDecimal(row[38]))
                                    .multiUseFacility(parseString(row[39]))
                                    .totalFacilitySize(parseBigDecimal(row[40]))
                                    .traditionalDesignationNumber(parseString(row[41]))
                                    .traditionalMainFood(parseString(row[42]))
                                    .homepage(row.length > 43 ? parseString(row[43]) : "데이터없음")
                                    .build();

                            entertainmentBarsRepository.save(bar);
                            savedCount++;
                        } catch (Exception e) {
                            errorCount++;
                            log.warn("{}번째 행 저장 실패: {}", i, e.getMessage());
                        }
                    } else {
                        errorCount++;
                        log.warn("{}번째 행 컬럼 부족 ({}개 < 43개)", i, row.length);
                    }
                }

                log.info("유흥주점 데이터 로딩 완료 - 성공: {}건, 실패: {}건, 전체: {}건",
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

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDate.of(1900, 1, 1); // 기본값: 1900-01-01
        }
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return LocalDate.of(1900, 1, 1); // 파싱 실패시도 기본값
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LocalDateTime.of(1900, 1, 1, 0, 0, 0); // 기본값: 1900-01-01 00:00:00
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return LocalDateTime.of(1900, 1, 1, 0, 0, 0); // 파싱 실패시도 기본값
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
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
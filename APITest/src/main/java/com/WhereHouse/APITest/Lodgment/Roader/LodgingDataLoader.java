package com.WhereHouse.APITest.Lodgment.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.APITest.Lodgment.Entity.LodgingBusiness;
import com.WhereHouse.APITest.Lodgment.Repository.LodgingBusinessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
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
public class LodgingDataLoader implements CommandLineRunner {

    private final LodgingBusinessRepository lodgingRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.lodging-data-path}")
    private String csvFilePath;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    @Transactional
    public void run(String... args) {
        if (lodgingRepository.count() > 0) {
            log.info("숙박업 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int savedCount = 0;
                for (int i = 1; i < csvData.size(); i++) { // 헤더 스킵
                    String[] row = csvData.get(i);
                    if (row.length >= 41) {
                        LodgingBusiness lodging = LodgingBusiness.builder()
                                .serviceName(parseString(row[1]))
                                .serviceId(parseString(row[2]))
                                .localGovCode(parseString(row[3]))
                                .managementNumber(parseString(row[4]))
                                .licenseDate(parseDate(row[5]))
                                .licenseCancelDate(parseDate(row[6]))
                                .businessStatusCode(parseString(row[7]))
                                .businessStatusName(parseString(row[8]))
                                .detailStatusCode(parseString(row[9]))
                                .detailStatusName(parseString(row[10]))
                                .closureDate(parseDate(row[11]))
                                .suspensionStartDate(parseDate(row[12]))
                                .suspensionEndDate(parseDate(row[13]))
                                .reopeningDate(parseDate(row[14]))
                                .locationPhone(parseString(row[15]))
                                .locationArea(parseDecimal(row[16]))
                                .locationPostalCode(parseString(row[17]))
                                .fullAddress(parseString(row[18]))
                                .roadAddress(parseString(row[19]))
                                .roadPostalCode(parseString(row[20]))
                                .businessName(parseString(row[21]))
                                .lastUpdateTime(parseDateTime(row[22]))
                                .dataUpdateType(parseString(row[23]))
                                .dataUpdateDate(parseDateTime(row[24]))
                                .businessTypeName(parseString(row[25]))
                                .coordX(parseDecimal(row[26]))
                                .coordY(parseDecimal(row[27]))
                                .hygieneBusinessType(parseString(row[28]))
                                .buildingGroundFloors(parseInteger(row[29]))
                                .buildingBasementFloors(parseInteger(row[30]))
                                .useStartGroundFloor(parseInteger(row[31]))
                                .useEndGroundFloor(parseInteger(row[32]))
                                .useStartBasementFloor(parseInteger(row[33]))
                                .useEndBasementFloor(parseInteger(row[34]))
                                .koreanRoomCount(parseInteger(row[35]))
                                .westernRoomCount(parseInteger(row[36]))
                                .conditionalPermitReason(parseString(row[37]))
                                .conditionalPermitStart(parseDate(row[38]))
                                .conditionalPermitEnd(parseDate(row[39]))
                                .buildingOwnershipType(parseString(row[40]))
                                .femaleEmployeeCount(row.length > 41 ? parseInteger(row[41]) : 0)
                                .maleEmployeeCount(row.length > 42 ? parseInteger(row[42]) : 0)
                                .multiUseFacilityYn(row.length > 43 ? parseString(row[43]) : "N")
                                .build();

                        lodgingRepository.save(lodging);
                        savedCount++;
                    }
                }
                log.info("{} 개 숙박업 데이터 저장 완료", savedCount);
            }
        } catch (Exception e) {
            log.error("숙박업 CSV 로딩 실패: {}", e.getMessage());
        }
    }

    private String parseString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "데이터없음";
        }
        return value.trim();
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

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return LocalDate.of(2025, 1, 1);
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            return LocalDate.of(2025, 1, 1);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return LocalDateTime.of(2025, 1, 1, 0, 0);
        }
        try {
            return LocalDateTime.parse(value.trim(), DATETIME_FORMATTER);
        } catch (Exception e) {
            return LocalDateTime.of(2025, 1, 1, 0, 0);
        }
    }
}
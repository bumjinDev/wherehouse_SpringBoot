package com.WhereHouse.API.Test.APITest.KaraokeRooms.Road;

import com.opencsv.CSVReader;
import com.WhereHouse.API.Test.APITest.KaraokeRooms.Entity.KaraokeRooms;
import com.WhereHouse.API.Test.APITest.KaraokeRooms.Repository.KaraokeRoomsRepository;
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
public class KaraokeRoomsDataLoader implements CommandLineRunner {

    private final KaraokeRoomsRepository karaokeRoomsRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.karaoke-rooms-data-path}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        if (karaokeRoomsRepository.count() > 0) {
            log.info("노래연습장 데이터 이미 존재. 로딩 스킵");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();

                int totalRows = csvData.size() - 1; // 헤더 제외
                int savedCount = 0;
                int errorCount = 0;

                log.info("노래연습장 데이터 로딩 시작 - 총 {} 건", totalRows);

                for (int i = 1; i < csvData.size(); i++) {
                    String[] row = csvData.get(i);

                    // 진행률 출력 (100건마다)
                    if (i % 100 == 0) {
                        double progress = ((double) (i - 1) / totalRows) * 100;
                        log.info("진행률 : {:.1f}% ({}/{})", progress, i - 1, totalRows);
                    }

                    if (row.length >= 56) {
                        try {
                            KaraokeRooms karaokeRoom = KaraokeRooms.builder()
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
                                    .cultureSportsType(parseString(row[25]))
                                    .cultureBusinessType(parseString(row[26]))
                                    .totalFloors(parseInteger(row[27]))
                                    .surroundingEnvironment(parseString(row[28]))
                                    .productionItems(parseString(row[29]))
                                    .facilityArea(parseBigDecimal(row[30]))
                                    .groundFloors(parseInteger(row[31]))
                                    .basementFloors(parseInteger(row[32]))
                                    .buildingPurpose(parseString(row[33]))
                                    .corridorWidth(parseBigDecimal(row[34]))
                                    .lightingFacilityLux(parseInteger(row[35]))
                                    .karaokeRoomsCount(parseInteger(row[36]))
                                    .youthRoomsCount(parseInteger(row[37]))
                                    .emergencyStairs(parseString(row[38]))
                                    .emergencyExits(parseString(row[39]))
                                    .autoVentilation(parseString(row[40]))
                                    .youthRoomAvailable(parseString(row[41]))
                                    .specialLighting(parseString(row[42]))
                                    .soundproofFacility(parseString(row[43]))
                                    .videoPlayerName(parseString(row[44]))
                                    .lightingFacilityYn(parseString(row[45]))
                                    .soundFacilityYn(parseString(row[46]))
                                    .convenienceFacilityYn(parseString(row[47]))
                                    .fireFacilityYn(parseString(row[48]))
                                    .totalGameMachines(parseInteger(row[49]))
                                    .existingBusinessType(row.length > 50 ? parseString(row[50]) : "데이터없음")
                                    .providedGames(row.length > 51 ? parseString(row[51]) : "데이터없음")
                                    .venueType(row.length > 52 ? parseString(row[52]) : "데이터없음")
                                    .itemName(row.length > 53 ? parseString(row[53]) : "데이터없음")
                                    .firstRegistrationTime(row.length > 54 ? parseString(row[54]) : "데이터없음")
                                    .regionType(row.length > 55 ? parseString(row[55]) : "데이터없음")
                                    .build();

                            karaokeRoomsRepository.save(karaokeRoom);
                            savedCount++;
                        } catch (Exception e) {
                            errorCount++;
                            log.warn("{}번째 행 저장 실패: {}", i, e.getMessage());
                        }
                    } else {
                        errorCount++;
                        log.warn("{}번째 행 컬럼 부족 ({}개 < 56개)", i, row.length);
                    }
                }

                log.info("노래연습장 데이터 로딩 완료 - 성공: {}건, 실패: {}건, 전체: {}건",
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
            // "59:59.0" 같은 시간 형식 처리
            if (value.contains(":") && !value.contains("-")) {
                return LocalDateTime.of(1900, 1, 1, 0, 0, 0);
            }
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
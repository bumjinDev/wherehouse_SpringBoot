package com.WhereHouse.AnalysisStaticData.Lodgment.Roader;

import com.opencsv.CSVReader;
import com.WhereHouse.AnalysisStaticData.Lodgment.Entity.LodgingBusiness;
import com.WhereHouse.AnalysisStaticData.Lodgment.Repository.LodgingBusinessRepository;
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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class LodgingDataLoader  { // implements CommandLineRunner

    private final LodgingBusinessRepository lodgingRepository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.lodging-data-path}")
    private String csvFilePath;

    private static final int BATCH_SIZE = 1000; // 배치 크기
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_FORMATTER_COMPACT = DateTimeFormatter.ofPattern("yyyyMMdd"); // 20210118 형식
    private static final DateTimeFormatter DATETIME_FORMATTER1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATETIME_FORMATTER2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER3 = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm");

//    @Override
    public void run(String... args) {
        long existingCount = lodgingRepository.count();
        if (existingCount > 0) {
            log.info("숙박업 데이터 이미 존재 ({}개). 로딩 스킵", existingCount);
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(csvFilePath);
            try (CSVReader reader = new CSVReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                List<String[]> csvData = reader.readAll();
                log.info("CSV 파일에서 {} 행 읽음 (헤더 포함)", csvData.size());

                List<LodgingBusiness> batch = new ArrayList<>();
                int totalSaved = 0;
                int totalErrors = 0;

                for (int i = 1; i < csvData.size(); i++) { // 헤더 스킵
                    String[] row = csvData.get(i);
                    try {
                        if (row.length >= 41) {
                            LodgingBusiness lodging = createLodgingBusiness(row);
                            batch.add(lodging);

                            // 배치가 찼거나 마지막 행이면 저장
                            if (batch.size() >= BATCH_SIZE || i == csvData.size() - 1) {
                                int saved = saveBatch(batch);
                                totalSaved += saved;
                                batch.clear();

                                if (totalSaved % (BATCH_SIZE * 10) == 0) {
                                    log.info("진행 상황: {} 개 데이터 저장 완료 (현재 DB 카운트: {})",
                                            totalSaved, lodgingRepository.count());
                                }
                            }
                        }
                    } catch (Exception e) {
                        totalErrors++;
                        log.warn("행 {} 처리 중 오류: {}", i, e.getMessage());

                        if (totalErrors > 100) {
                            log.error("오류가 너무 많아서 처리를 중단합니다. 총 오류 수: {}", totalErrors);
                            break;
                        }
                    }
                }

                // 최종 상태 확인
                long finalCount = lodgingRepository.count();
                log.info("로딩 완료 - 처리 시도: {} 개, 최종 DB 저장: {} 개, 오류: {} 개",
                        totalSaved, finalCount, totalErrors);

                if (finalCount == 0) {
                    log.error("경고: DB에 데이터가 하나도 저장되지 않았습니다. 트랜잭션 또는 DB 연결 문제를 확인하세요.");
                }
            }
        } catch (Exception e) {
            log.error("숙박업 CSV 로딩 실패: {}", e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int saveBatch(List<LodgingBusiness> batch) {
        try {
            List<LodgingBusiness> saved = lodgingRepository.saveAll(batch);
            log.debug("배치 저장 성공: {} 개", saved.size());
            return saved.size();
        } catch (Exception e) {
            log.error("배치 저장 실패: {}", e.getMessage());

            // 배치 실패 시 개별 저장 시도
            int savedCount = 0;
            for (LodgingBusiness lodging : batch) {
                try {
                    lodgingRepository.save(lodging);
                    savedCount++;
                } catch (Exception individualError) {
                    log.warn("개별 저장 실패 - 관리번호: {}, 오류: {}",
                            lodging.getManagementNumber(), individualError.getMessage());
                }
            }

            log.info("개별 저장 완료: {} / {} 개", savedCount, batch.size());
            return savedCount;
        }
    }

    private LodgingBusiness createLodgingBusiness(String[] row) {
        return LodgingBusiness.builder()
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
    }

    private String parseString(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return "데이터없음";
        }
        String trimmed = value.trim();

        if (trimmed.length() > 1900) {
            trimmed = trimmed.substring(0, 1900);
            log.debug("문자열 길이 제한으로 자름: 원본 길이 {} -> 1900", value.length());
        }

        return trimmed;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return 0;
        }
        try {
            String cleaned = value.trim().replace(",", "");
            long longValue = Long.parseLong(cleaned);

            if (longValue > Integer.MAX_VALUE || longValue < Integer.MIN_VALUE) {
                log.debug("정수값 오버플로: {}, 0으로 설정", longValue);
                return 0;
            }

            return (int) longValue;
        } catch (NumberFormatException e) {
            log.debug("정수 파싱 실패: '{}', 0으로 설정", value);
            return 0;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return BigDecimal.ZERO;
        }
        try {
            String cleaned = value.trim().replace(",", "");
            BigDecimal result = new BigDecimal(cleaned);

            int precision = result.precision();

            if (precision > 25) {
                log.debug("BigDecimal precision 초과: {} (precision: {}), 반올림 적용", value, precision);
                result = result.setScale(10, BigDecimal.ROUND_HALF_UP);
            }

            return result;
        } catch (NumberFormatException e) {
            log.debug("소수 파싱 실패: '{}', 0으로 설정", value);
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return LocalDate.of(2025, 1, 1);
        }

        String trimmed = value.trim();

        try {
            // yyyy-MM-dd 형식 시도
            return LocalDate.parse(trimmed, DATE_FORMATTER);
        } catch (Exception e1) {
            try {
                // yyyyMMdd 형식 시도 (20210118 같은 형식)
                return LocalDate.parse(trimmed, DATE_FORMATTER_COMPACT);
            } catch (Exception e2) {
                log.debug("날짜 파싱 실패: '{}', 기본값으로 설정", value);
                return LocalDate.of(2025, 1, 1);
            }
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty() || "-".equals(value.trim())) {
            return LocalDateTime.of(2025, 1, 1, 0, 0);
        }

        String trimmed = value.trim();
        DateTimeFormatter[] formatters = {DATETIME_FORMATTER1, DATETIME_FORMATTER2, DATETIME_FORMATTER3};

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(trimmed, formatter);
            } catch (Exception e) {
                // 다음 형식 시도
            }
        }

        log.debug("날짜시간 파싱 실패: '{}', 기본값으로 설정", value);
        return LocalDateTime.of(2025, 1, 1, 0, 0);
    }
}
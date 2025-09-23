package com.WhereHouse.AnalysisStaticData.ConvenienceStore.component;

import com.WhereHouse.AnalysisStaticData.ConvenienceStore.entity.ConvenienceStoreData;
import com.WhereHouse.AnalysisStaticData.ConvenienceStore.reposiotry.ConvenienceStoreDataRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConvenienceStoreExcelLoader  { // implements CommandLineRunner

    private final ConvenienceStoreDataRepository repository;

    @Value("${app.csv.convenience-path}")
    private Resource csvFileResource; // 변수명 변경

    @Value("${convenience-store.excel.header-row:0}")
    private int headerRowIndex;

    @Value("${convenience-store.excel.batch-size:1000}")
    private int batchSize;

    // CSV 파싱용 포맷터들은 그대로 사용
    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    };
    private static final DateTimeFormatter[] DATETIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    };

//    @Override
    @Transactional
    public void run(String... args) {
        log.info("편의점 CSV 데이터 로딩 시작...");
        log.info("파일 경로: {}", csvFileResource.getFilename());

        try {
            long existingCount = repository.count();
            if (existingCount > 0) {
                log.info("기존 데이터 {}개 존재. 전체 삭제 후 새로 로드합니다.", existingCount);
                repository.deleteAllInBatch();
            }

            List<ConvenienceStoreData> dataList = loadCsvData();

            if (dataList.isEmpty()) {
                log.warn("로드할 데이터가 없습니다.");
                return;
            }

            log.info("총 {}개 데이터 로드 완료. 배치 저장 시작...", dataList.size());
            saveDataInBatches(dataList);

            long finalCount = repository.count();
            log.info("편의점 데이터 로딩 완료: {}개 데이터 저장", finalCount);

            printDataStatistics();
        } catch (Exception e) {
            log.error("편의점 CSV 데이터 로딩 실패", e);
            throw new RuntimeException("편의점 데이터 로딩 실패", e);
        }
    }

    // 메서드명을 loadCsvData로 변경하고 내부 로직을 opencsv에 맞게 수정
    private List<ConvenienceStoreData> loadCsvData() throws IOException, CsvValidationException {
        List<ConvenienceStoreData> dataList = new ArrayList<>();

        // InputStream을 UTF-8 인코딩으로 읽기 위한 InputStreamReader
        try (InputStream is = csvFileResource.getInputStream();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {

            // 헤더 행 스킵
            for (int i = 0; i <= headerRowIndex; i++) {
                csvReader.readNext();
            }

            String[] line;
            int rowNumber = headerRowIndex + 1;
            while ((line = csvReader.readNext()) != null) {
                rowNumber++;
                try {
                    ConvenienceStoreData data = mapRowToEntity(line);
                    dataList.add(data);
                } catch (Exception e) {
                    log.warn("행 {} 처리 실패: {}", rowNumber, e.getMessage());
                }
            }
        }
        return dataList;
    }

    // 파라미터를 String 배열로 받도록 수정
    private ConvenienceStoreData mapRowToEntity(String[] row) {
        return ConvenienceStoreData.builder()
                .openLocalGovCode(getStringValue(row, 0))
                .managementNumber(getStringValue(row, 1))
                .licenseDate(getDateValue(row, 2))
                .licenseCancelDate(getDateValue(row, 3))
                .businessStatusCode(getStringValue(row, 4))
                .businessStatusName(getStringValue(row, 5))
                .detailedStatusCode(getStringValue(row, 6))
                .detailedStatusName(getStringValue(row, 7))
                .closureDate(getDateValue(row, 8))
                .suspensionStartDate(getDateValue(row, 9))
                .suspensionEndDate(getDateValue(row, 10))
                .reopeningDate(getDateValue(row, 11))
                .phoneNumber(getStringValue(row, 12))
                .locationArea(getBigDecimalValue(row, 13))
                .locationPostalCode(getStringValue(row, 14))
                .lotAddress(getStringValue(row, 15))
                .roadAddress(getStringValue(row, 16))
                .roadPostalCode(getStringValue(row, 17))
                .businessName(getStringValue(row, 18))
                .lastModifiedDate(getDateTimeValue(row, 19))
                .dataUpdateType(getStringValue(row, 20))
                .dataUpdateTime(getStringValue(row, 21))
                .businessTypeName(getStringValue(row, 22))
                .coordinateX(getBigDecimalValue(row, 23))
                .coordinateY(getBigDecimalValue(row, 24))
                .salesArea(getBigDecimalValue(row, 25))
                .build();
    }

    // 헬퍼 메서드들도 String 배열을 받도록 수정
    private String getStringValue(String[] row, int index) {
        if (index >= row.length || row[index] == null || row[index].trim().isEmpty()) {
            return null;
        }
        return row[index].trim();
    }

    private LocalDate getDateValue(String[] row, int index) {
        String dateStr = getStringValue(row, index);
        if (dateStr == null) return null;
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) { /* 다음 포맷 시도 */ }
        }
        return null;
    }

    private LocalDateTime getDateTimeValue(String[] row, int index) {
        String dateTimeStr = getStringValue(row, index);
        if (dateTimeStr == null) return null;
        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException e) { /* 다음 포맷 시도 */ }
        }
        return null;
    }

    private BigDecimal getBigDecimalValue(String[] row, int index) {
        String valueStr = getStringValue(row, index);
        if (valueStr == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(valueStr);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private void saveDataInBatches(List<ConvenienceStoreData> dataList) {
        int totalSize = dataList.size();
        for (int i = 0; i < totalSize; i += batchSize) {
            int end = Math.min(i + batchSize, totalSize);
            List<ConvenienceStoreData> batch = dataList.subList(i, end);
            repository.saveAll(batch);
            log.info("배치 저장 완료: {} / {}", end, totalSize);
        }
    }

    private void printDataStatistics() {
        log.info("========== 데이터 로드 통계 ==========");
        List<Object[]> stats = repository.countByBusinessStatus(); // 이 부분은 JpaRepository에 의존하므로 변경 없음
        if (stats.isEmpty()) {
            log.info("통계 데이터가 없습니다.");
        } else {
            stats.forEach(row -> log.info("영업상태: {}, 개수: {}", row[0], row[1]));
        }
        log.info("======================================");
    }
}
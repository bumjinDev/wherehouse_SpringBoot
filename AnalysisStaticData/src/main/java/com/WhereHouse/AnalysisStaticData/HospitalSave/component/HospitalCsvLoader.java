package com.WhereHouse.AnalysisStaticData.HospitalSave.component;

import com.WhereHouse.AnalysisStaticData.HospitalSave.entity.HospitalData;
import com.WhereHouse.AnalysisStaticData.HospitalSave.repository.HospitalDataRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HospitalCsvLoader  {   // implements CommandLineRunner

    private final HospitalDataRepository repository;

    @Value("${app.csv.hospital-path}")
    private Resource csvFileResource;

    @Value("${app.csv.header-row:1}")
    private int headerRowIndex;

    @Value("${app.csv.batch-size:1000}")
    private int batchSize;

    private static final DateTimeFormatter[] DATE_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    };
    private static final DateTimeFormatter[] DATETIME_FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    };

//    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("병원 CSV 데이터 로딩 시작...");
        log.info("파일 경로: {}", csvFileResource.getFilename());

        if (repository.count() > 0) {
            log.info("기존 데이터 존재. 전체 삭제 후 새로 로드합니다.");
            repository.deleteAllInBatch();
        }

        List<HospitalData> dataList = loadCsvData();

        if (!dataList.isEmpty()) {
            log.info("총 {}개 데이터 로드 완료. 배치 저장 시작...", dataList.size());
            saveDataInBatches(dataList);
            log.info("병원 데이터 로딩 완료: {}개 데이터 저장", repository.count());
            printDataStatistics();
        } else {
            log.warn("로드할 데이터가 없습니다.");
        }
    }

    private List<HospitalData> loadCsvData() throws IOException, CsvValidationException {
        List<HospitalData> dataList = new ArrayList<>();
        try (InputStream is = csvFileResource.getInputStream();
             InputStreamReader reader = new InputStreamReader(is, "EUC-KR");
             CSVReader csvReader = new CSVReader(reader)) {

            for (int i = 0; i < headerRowIndex; i++) {
                csvReader.readNext();
            }

            String[] line;
            int rowNumber = headerRowIndex;
            while ((line = csvReader.readNext()) != null) {
                rowNumber++;
                try {
                    dataList.add(mapRowToEntity(line));
                } catch (Exception e) {
                    log.error("행 {} 처리 중 오류 발생. 데이터: {}", rowNumber, Arrays.toString(line), e);
                }
            }
        }
        return dataList;
    }

    private HospitalData mapRowToEntity(String[] row) {


        return HospitalData.builder()
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
                .build();
    }

    private String getStringValue(String[] row, int index) {

        if (index >= row.length || row[index] == null || row[index].trim().isEmpty()) return null;
        String val = row[index].trim();
        return row[index].trim();
    }

    private BigDecimal getBigDecimalValue(String[] row, int index) {
        String val = getStringValue(row, index);
        if (val == null) return null;
        try {
            return new BigDecimal(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate getDateValue(String[] row, int index) {
        String val = getStringValue(row, index);
        if (val == null) return null;
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(val, formatter);
            } catch (DateTimeParseException e) { /* continue */ }
        }
        return null;
    }

    private LocalDateTime getDateTimeValue(String[] row, int index) {
        String val = getStringValue(row, index);
        if (val == null) return null;
        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(val, formatter);
            } catch (DateTimeParseException e) { /* continue */ }
        }
        return null;
    }

    private void saveDataInBatches(List<HospitalData> dataList) {
        for (int i = 0; i < dataList.size(); i += batchSize) {
            int end = Math.min(i + batchSize, dataList.size());
            repository.saveAll(dataList.subList(i, end));
        }
    }

    private void printDataStatistics() {
        log.info("========== 데이터 로드 통계 ==========");
        repository.countByBusinessStatus().forEach(row ->
                log.info("영업상태: {}, 개수: {}", row[0], row[1])
        );
        log.info("======================================");
    }
}
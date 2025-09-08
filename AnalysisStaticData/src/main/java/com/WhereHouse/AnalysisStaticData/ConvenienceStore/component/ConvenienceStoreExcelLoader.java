package com.WhereHouse.AnalysisStaticData.ConvenienceStore.component;

import com.WhereHouse.AnalysisStaticData.ConvenienceStore.entity.ConvenienceStoreData;
import com.WhereHouse.AnalysisStaticData.ConvenienceStore.reposiotry.ConvenienceStoreDataRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConvenienceStoreExcelLoader implements CommandLineRunner {

    private final ConvenienceStoreDataRepository repository;

    @Value("${convenience-store.excel.file-path}")
    @NotBlank(message = "엑셀 파일 경로는 필수입니다")
    private String excelFilePath;

    @Value("${convenience-store.excel.sheet-index:0}")
    private int sheetIndex;

    @Value("${convenience-store.excel.header-row:0}")
    private int headerRowIndex;

    @Value("${convenience-store.excel.batch-size:1000}")
    private int batchSize;

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

    @Override
    @Transactional
    public void run(String... args) {
        log.info("편의점 엑셀 데이터 로딩 시작...");
        log.info("파일 경로: {}", excelFilePath);

        try {
            // 기존 데이터 확인
            long existingCount = repository.count();
            if (existingCount > 0) {
                log.info("기존 데이터 {}개 존재. 전체 삭제 후 새로 로드합니다.", existingCount);
                repository.deleteAllInBatch(); // 대량 삭제 시 더 효율적
            }

            List<ConvenienceStoreData> dataList = loadExcelData();

            if (dataList.isEmpty()) {
                log.warn("로드할 데이터가 없습니다.");
                return;
            }

            log.info("총 {}개 데이터 로드 완료. 배치 저장 시작...", dataList.size());
            saveDataInBatches(dataList);

            // 최종 확인
            long finalCount = repository.count();
            log.info("편의점 데이터 로딩 완료: {}개 데이터 저장", finalCount);

            // 간단한 통계 출력
            printDataStatistics();

        } catch (Exception e) {
            log.error("편의점 엑셀 데이터 로딩 실패", e);
            throw new RuntimeException("편의점 데이터 로딩 실패", e);
        }
    }

    private List<ConvenienceStoreData> loadExcelData() throws IOException {
        List<ConvenienceStoreData> dataList = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(excelFilePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(sheetIndex);
            Iterator<Row> rowIterator = sheet.iterator();

            // 헤더 행 스킵
            for (int i = 0; i <= headerRowIndex && rowIterator.hasNext(); i++) {
                rowIterator.next();
            }

            int rowNumber = headerRowIndex + 1;
            int successCount = 0;
            int errorCount = 0;

            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                rowNumber++;

                try {
                    ConvenienceStoreData data = mapRowToEntity(row);
                    if (data != null) {
                        dataList.add(data);
                        successCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.warn("행 {} 처리 실패: {}", rowNumber, e.getMessage());
                    if (errorCount > 100) { // 너무 많은 오류 방지
                        log.error("오류가 너무 많이 발생했습니다. 데이터 형식을 확인해주세요.");
                        break;
                    }
                }
            }

            log.info("엑셀 읽기 완료: 성공 {}, 오류 {}", successCount, errorCount);
        }

        return dataList;
    }

    private ConvenienceStoreData mapRowToEntity(Row row) {
        if (isEmptyRow(row)) {
            return null;
        }

        return ConvenienceStoreData.builder()
                .openLocalGovCode(getStringValue(row, 0))       // 개방자치단체코드
                .managementNumber(getStringValue(row, 1))       // 관리번호
                .licenseDate(getDateValue(row, 2))              // 인허가일자
                .licenseCancelDate(getDateValue(row, 3))        // 인허가취소일자
                .businessStatusCode(getStringValue(row, 4))     // 영업상태코드
                .businessStatusName(getStringValue(row, 5))     // 영업상태명
                .detailedStatusCode(getStringValue(row, 6))     // 상세영업상태코드
                .detailedStatusName(getStringValue(row, 7))     // 상세영업상태명
                .closureDate(getDateValue(row, 8))              // 폐업일자
                .suspensionStartDate(getDateValue(row, 9))      // 휴업시작일자
                .suspensionEndDate(getDateValue(row, 10))       // 휴업종료일자
                .reopeningDate(getDateValue(row, 11))           // 재개업일자
                .phoneNumber(getStringValue(row, 12))           // 전화번호
                .locationArea(getBigDecimalValue(row, 13))      // 소재지면적
                .locationPostalCode(getStringValue(row, 14))    // 소재지우편번호
                .lotAddress(getStringValue(row, 15))            // 지번주소
                .roadAddress(getStringValue(row, 16))           // 도로명주소
                .roadPostalCode(getStringValue(row, 17))        // 도로명우편번호
                .businessName(getStringValue(row, 18))          // 사업장명
                .lastModifiedDate(getDateTimeValue(row, 19))    // 최종수정일자
                .dataUpdateType(getStringValue(row, 20))        // 데이터갱신구분
                .dataUpdateTime(getStringValue(row, 21))        // 데이터갱신일자
                .businessTypeName(getStringValue(row, 22))      // 업태구분명
                .coordinateX(getBigDecimalValue(row, 23))       // 좌표정보(X)
                .coordinateY(getBigDecimalValue(row, 24))       // 좌표정보(Y)
                .salesArea(getBigDecimalValue(row, 25))         // 판매점영업면적
                .build();
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK)
                return false;
        }
        return true;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // 날짜 형식일 경우 예외 처리
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
                // DataFormatter를 사용하여 Excel에 표시된 그대로 값을 읽어옴 (e.g., '010' -> '010')
                DataFormatter formatter = new DataFormatter();
                return formatter.formatCellValue(cell);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
            default:
                return "";
        }
    }

    private String getStringValue(Row row, int columnIndex) {
        Cell cell = row.getCell(columnIndex);
        return getCellValue(cell).trim();
    }

    private LocalDate getDateValue(Row row, int columnIndex) {
        String dateStr = getStringValue(row, columnIndex);
        if (dateStr.isEmpty()) {
            return null; // 날짜는 의미 없는 값으로 채우기보다 null이 명확함
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(dateStr, formatter);
            } catch (DateTimeParseException e) { /* 다음 포맷 시도 */ }
        }
        log.debug("날짜 파싱 실패: {}", dateStr);
        return null;
    }

    private LocalDateTime getDateTimeValue(Row row, int columnIndex) {
        String dateTimeStr = getStringValue(row, columnIndex);
        if (dateTimeStr.isEmpty()) {
            return null; // 날짜/시간도 null이 명확함
        }
        for (DateTimeFormatter formatter : DATETIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException e) { /* 다음 포맷 시도 */ }
        }
        // LocalDateTime 파싱 실패 시 LocalDate로 시도 후 자정으로 변환
        LocalDate date = getDateValue(row, columnIndex);
        return date != null ? date.atStartOfDay() : null;
    }

    private BigDecimal getBigDecimalValue(Row row, int columnIndex) {
        String value = getStringValue(row, columnIndex);
        if (value.isEmpty()) {
            return BigDecimal.ZERO; // 요구사항: null 대신 0으로 처리
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.debug("BigDecimal 파싱 실패: '{}'. 0으로 대체합니다.", value);
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
        List<Object[]> stats = repository.countByBusinessStatus();
        if (stats.isEmpty()) {
            log.info("통계 데이터가 없습니다.");
        } else {
            stats.forEach(row -> log.info("영업상태: {}, 개수: {}", row[0], row[1]));
        }
        log.info("======================================");
    }
}
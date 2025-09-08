package com.WhereHouse.AnalysisStaticData.Population.Loader;

import com.WhereHouse.AnalysisStaticData.Population.entity.AnalysisPopulationDensity;
import com.WhereHouse.AnalysisStaticData.Population.repository.AnalysisPopulationDensityRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PopulationDensityDataLoader implements CommandLineRunner {

    private final AnalysisPopulationDensityRepository repository;
    private final ResourceLoader resourceLoader;

    @Value("${app.csv.population-density-path:classpath:data/인구밀도_202509011.csv}")
    private String csvFilePath;

    @Override
    @Transactional
    public void run(String... args) {
        long existingCount = repository.count();
        if (existingCount > 0) {
            log.info("서울시 인구밀도 데이터 이미 존재 ({}개). 로딩 스킵", existingCount);
            return;
        }

        try {
            List<AnalysisPopulationDensity> dataList = loadFromCsv();

            if (!dataList.isEmpty()) {
                List<AnalysisPopulationDensity> savedList = repository.saveAll(dataList);
                log.info("서울시 인구밀도 데이터 로딩 완료 - 총 {} 건 저장", savedList.size());
                logDataSummary(savedList);
            } else {
                log.warn("로딩할 데이터가 없습니다.");
            }

        } catch (Exception e) {
            log.error("서울시 인구밀도 데이터 로딩 실패: {}", e.getMessage(), e);
            throw new RuntimeException("데이터 로딩 중 오류 발생", e);
        }
    }

    private List<AnalysisPopulationDensity> loadFromCsv() throws IOException, CsvException {
        List<AnalysisPopulationDensity> dataList = new ArrayList<>();

        Resource resource = resourceLoader.getResource(csvFilePath);
        if (!resource.exists()) {
            throw new IOException("CSV 파일을 찾을 수 없습니다: " + csvFilePath);
        }

        log.info("CSV 파일 로딩 시작: {}", csvFilePath);

        try (CSVReader csvReader = new CSVReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            List<String[]> records = csvReader.readAll();

            if (records.size() < 2) {
                log.warn("CSV 파일에 데이터가 부족합니다. 최소 2행 필요 (헤더 + 데이터)");
                return dataList;
            }

            // 첫 번째 행: "동별(1)",동별(2),동별(3),2024,2024,2024
            // 두 번째 행: "동별(1)",동별(2),동별(3),인구 (명),면적 (㎢),인구밀도 (명/㎢)
            // 세 번째 행부터 실제 데이터

            log.info("CSV 헤더 1행: {}", Arrays.toString(records.get(0)));
            log.info("CSV 헤더 2행: {}", Arrays.toString(records.get(1)));

            int processedCount = 0;
            int errorCount = 0;
            int skippedCount = 0;

            // 3번째 행부터 데이터 처리 (인덱스 2부터)
            for (int i = 2; i < records.size(); i++) {
                String[] record = records.get(i);

                try {
                    AnalysisPopulationDensity data = parseCsvRecord(record, i + 1);
                    if (data != null) {
                        // 중복 체크
                        if (!repository.existsByDistrictNameAndYear(data.getDistrictName(), data.getYear())) {
                            dataList.add(data);
                            processedCount++;
                            log.debug("데이터 추가: {} - 인구: {}, 면적: {}, 밀도: {}",
                                    data.getDistrictName(),
                                    data.getPopulationCount(),
                                    data.getAreaSize(),
                                    data.getPopulationDensity());
                        } else {
                            skippedCount++;
                            log.debug("중복 데이터 스킵: {}", data.getDistrictName());
                        }
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    log.warn("CSV 레코드 파싱 실패 - 행 {}: {}, 오류: {}",
                            i + 1, Arrays.toString(record), e.getMessage());
                }
            }

            log.info("CSV 파싱 완료 - 처리: {}건, 스킵: {}건, 오류: {}건, 총 데이터: {}건",
                    processedCount, skippedCount, errorCount, dataList.size());
        }

        return dataList;
    }

    private AnalysisPopulationDensity parseCsvRecord(String[] record, int lineNumber) {
        if (record.length < 6) {
            log.debug("CSV 레코드 길이 부족 - 행 {}: {} (최소 6개 필요)", lineNumber, record.length);
            return null;
        }

        try {
            // 실제 CSV 구조에 맞춘 파싱
            String level1 = cleanString(record[0]);        // "합계"
            String districtName = cleanString(record[1]);  // 구 이름
            String level3 = cleanString(record[2]);        // "소계"

            // '소계'인 경우 필터링 (서울시 전체 합계는 제외)
            if ("소계".equals(districtName) || !StringUtils.hasText(districtName)) {
                log.debug("행 {}: '소계' 또는 빈 구 이름 필터링", lineNumber);
                return null;
            }

            // 데이터 파싱
            Long populationCount = parseLong(record[3]);      // 인구수
            BigDecimal areaSize = parseBigDecimal(record[4]); // 면적
            BigDecimal populationDensity = parseBigDecimal(record[5]); // 인구밀도

            // 유효성 검사
            if (populationCount == null || populationCount <= 0) {
                log.warn("행 {}: 유효하지 않은 인구수: {}", lineNumber, record[3]);
                return null;
            }

            if (areaSize == null || areaSize.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("행 {}: 유효하지 않은 면적: {}", lineNumber, record[4]);
                return null;
            }

            AnalysisPopulationDensity entity = AnalysisPopulationDensity.builder()
                    .districtName(districtName)
                    .year(2024)
                    .populationCount(populationCount)
                    .areaSize(areaSize)
                    .populationDensity(populationDensity)
                    .build();

            // 인구밀도 재계산 및 검증
            entity.calculateDensity();

            return entity;

        } catch (Exception e) {
            log.error("CSV 레코드 파싱 중 오류 발생 - 행 {}: {}", lineNumber, Arrays.toString(record), e);
            throw new RuntimeException("레코드 파싱 실패", e);
        }
    }

    private String cleanString(String value) {
        if (value == null) return null;
        // 따옴표 제거 및 공백 정리
        return value.replace("\"", "").trim();
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            // 숫자에서 콤마 및 공백 제거
            String cleanValue = cleanString(value).replaceAll("[,\\s]", "");
            return Long.parseLong(cleanValue);
        } catch (NumberFormatException e) {
            log.debug("Long 파싱 실패: {}", value);
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            // 숫자에서 콤마 및 공백 제거
            String cleanValue = cleanString(value).replaceAll("[,\\s]", "");
            return new BigDecimal(cleanValue);
        } catch (NumberFormatException e) {
            log.debug("BigDecimal 파싱 실패: {}", value);
            return null;
        }
    }

    private void logDataSummary(List<AnalysisPopulationDensity> dataList) {
        if (dataList.isEmpty()) {
            return;
        }

        long totalPopulation = dataList.stream()
                .mapToLong(AnalysisPopulationDensity::getPopulationCount)
                .sum();

        AnalysisPopulationDensity maxDensity = dataList.stream()
                .max((a, b) -> a.getPopulationDensity().compareTo(b.getPopulationDensity()))
                .orElse(null);

        AnalysisPopulationDensity minDensity = dataList.stream()
                .min((a, b) -> a.getPopulationDensity().compareTo(b.getPopulationDensity()))
                .orElse(null);

        AnalysisPopulationDensity maxPopulation = dataList.stream()
                .max((a, b) -> a.getPopulationCount().compareTo(b.getPopulationCount()))
                .orElse(null);

        BigDecimal totalArea = dataList.stream()
                .map(AnalysisPopulationDensity::getAreaSize)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("=== 서울시 인구밀도 데이터 요약 ===");
        log.info("총 구 개수: {}개", dataList.size());
        log.info("총 인구수: {:,}명", totalPopulation);
        log.info("총 면적: {}㎢", totalArea);

        if (maxDensity != null) {
            log.info("최고 인구밀도: {} ({:,}명/㎢)",
                    maxDensity.getDistrictName(), maxDensity.getPopulationDensity());
        }
        if (minDensity != null) {
            log.info("최저 인구밀도: {} ({:,}명/㎢)",
                    minDensity.getDistrictName(), minDensity.getPopulationDensity());
        }
        if (maxPopulation != null) {
            log.info("최대 인구수: {} ({:,}명)",
                    maxPopulation.getDistrictName(), maxPopulation.getPopulationCount());
        }

        // 전체 서울시 평균 인구밀도 계산
        if (totalArea.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal avgDensity = new BigDecimal(totalPopulation)
                    .divide(totalArea, 2, BigDecimal.ROUND_HALF_UP);
            log.info("서울시 전체 평균 인구밀도: {}명/㎢", avgDensity);
        }

        log.info("===============================");
    }
}
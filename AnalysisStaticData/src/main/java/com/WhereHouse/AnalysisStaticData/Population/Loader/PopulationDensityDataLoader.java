package com.WhereHouse.AnalysisStaticData.Population.Loader;

import com.WhereHouse.AnalysisStaticData.Population.entity.SeoulPopulationDensity;
import com.WhereHouse.AnalysisStaticData.Population.repository.SeoulPopulationDensityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class PopulationDensityDataLoader implements CommandLineRunner {

    private final SeoulPopulationDensityRepository repository;

    private static final String DATA_TEXT = """
        동별(1) 동별(2) 동별(3) 2024 2024 2024 동별(1) 동별(2) 동별(3) 인구 (명) 면적 (㎢) 인구밀도 (명/㎢) 
        합계 소계 소계 9597372 605.21 15858 
        합계 종로구 소계 149608 23.91 6256 
        합계 중구 소계 131214 9.96 13174 
        합계 용산구 소계 217194 21.87 9932 
        합계 성동구 소계 281289 16.82 16723 
        합계 광진구 소계 348652 17.06 20433 
        합계 동대문구 소계 358603 14.22 25226 
        합계 중랑구 소계 385349 18.5 20832 
        합계 성북구 소계 435037 24.58 17700 
        합계 강북구 소계 289374 23.61 12258 
        합계 도봉구 소계 306032 20.65 14819 
        합계 노원구 소계 496552 35.44 14010 
        합계 은평구 소계 465350 29.71 15663 
        합계 서대문구 소계 318622 17.63 18073 
        합계 마포구 소계 372745 23.85 15626 
        합계 양천구 소계 434351 17.41 24953 
        합계 강서구 소계 562194 41.45 13563 
        합계 구로구 소계 411916 20.12 20472 
        합계 금천구 소계 239070 13.02 18364 
        합계 영등포구 소계 397173 24.55 16177 
        합계 동작구 소계 387352 16.35 23684 
        합계 관악구 소계 495620 29.57 16762 
        합계 서초구 소계 413076 46.97 8795 
        합계 강남구 소계 563215 39.5 14259 
        합계 송파구 소계 656310 33.88 19374 
        합계 강동구 소계 481474 24.59 19580
        """;

    @Override
    @Transactional
    public void run(String... args) {
        long existingCount = repository.count();
        if (existingCount > 0) {
            log.info("서울시 인구밀도 데이터 이미 존재 ({}개). 로딩 스킵", existingCount);
            return;
        }

        try {
            List<SeoulPopulationDensity> dataList = parseTextData();

            if (!dataList.isEmpty()) {
                List<SeoulPopulationDensity> savedList = repository.saveAll(dataList);
                log.info("서울시 인구밀도 데이터 로딩 완료 - 총 {} 건 저장", savedList.size());
            }

        } catch (Exception e) {
            log.error("서울시 인구밀도 데이터 로딩 실패: {}", e.getMessage(), e);
        }
    }

    private List<SeoulPopulationDensity> parseTextData() {
        List<SeoulPopulationDensity> dataList = new ArrayList<>();
        String[] lines = DATA_TEXT.trim().split("\\n");
        Pattern pattern = Pattern.compile("합계\\s+(\\S+)\\s+소계\\s+(\\d+)\\s+([\\d.]+)\\s+(\\d+)");

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.contains("동별") || trimmedLine.contains("인구")) {
                continue;
            }

            Matcher matcher = pattern.matcher(trimmedLine);
            if (matcher.find()) {
                try {
                    String districtName = matcher.group(1);
                    Long population = Long.parseLong(matcher.group(2));
                    BigDecimal area = new BigDecimal(matcher.group(3));
                    Long density = Long.parseLong(matcher.group(4));

                    SeoulPopulationDensity data = SeoulPopulationDensity.builder()
                            .districtLevel1("합계")
                            .districtLevel2(districtName)
                            .districtLevel3("소계")
                            .year20241(2024)
                            .year20242(2024)
                            .year20243(2024)
                            .populationCount(population)
                            .areaSize(area)
                            .populationDensity(new BigDecimal(density))
                            .build();

                    dataList.add(data);

                } catch (NumberFormatException e) {
                    log.warn("숫자 변환 실패: {}", trimmedLine);
                }
            }
        }

        log.info("총 {}개의 데이터 파싱 완료", dataList.size());
        return dataList;
    }
}
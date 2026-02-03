package com.wherehouse.AnalysisData.population.processor;

import com.wherehouse.AnalysisData.population.repository.AnalysisPopulationDensityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PopulationDataProcessor {

    private final AnalysisPopulationDensityRepository populationDensityRepository;

    /**
     * 분석에 사용할 구별 인구밀도를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 인구밀도 데이터 맵
     */
    public Map<String, Long> getPopulationCountMapByDistrict() {
        List<Object[]> results = populationDensityRepository.findPopulationDensityByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((BigDecimal) row[1]).longValue() // 두 번째 요소(인구밀도)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 인구수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 인구수 데이터 맵
     */
    public Map<String, Long> getPopulationCountOnlyMapByDistrict() {
        List<Object[]> results = populationDensityRepository.findPopulationCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(인구수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 면적을 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 면적 데이터 맵
     */
    public Map<String, Long> getAreaSizeMapByDistrict() {
        List<Object[]> results = populationDensityRepository.findAreaSizeByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((BigDecimal) row[1]).longValue() // 두 번째 요소(면적)를 Long 타입으로 변환
                ));
    }
}
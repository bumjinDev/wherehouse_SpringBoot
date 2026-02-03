package com.wherehouse.AnalysisData.police.processor;

import com.wherehouse.AnalysisData.police.repository.AnalysisPoliceFacilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PoliceFacilityDataProcessor {

    private final AnalysisPoliceFacilityRepository policeFacilityRepository;

    /**
     * 분석에 사용할 구별 경찰시설 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 경찰시설 수 데이터 맵
     */
    public Map<String, Long> getPoliceFacilityCountMapByDistrict() {
        List<Object[]> results = policeFacilityRepository.findPoliceFacilityCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 좌표 정보가 있는 경찰시설 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 좌표 보유 경찰시설 수 데이터 맵
     */
    public Map<String, Long> getPoliceFacilityWithCoordinatesCountByDistrict() {
        List<Object[]> results = policeFacilityRepository.findPoliceFacilityWithCoordinatesByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 시설유형별 경찰시설 수를 조회하여 Map 형태로 반환합니다.
     * @return 구별_시설유형별 경찰시설 수 데이터 맵 (구명_시설유형 -> 개수)
     */
    public Map<String, Long> getPoliceFacilityCountByDistrictAndType() {
        List<Object[]> results = policeFacilityRepository.findPoliceFacilityCountByDistrictAndType();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> row[0] + "_" + row[1], // 구명_시설유형
                        row -> ((Number) row[2]).longValue() // 개수
                ));
    }
}
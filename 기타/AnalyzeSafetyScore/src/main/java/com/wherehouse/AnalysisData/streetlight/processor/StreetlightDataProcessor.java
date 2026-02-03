package com.wherehouse.AnalysisData.streetlight.processor;

import com.wherehouse.AnalysisData.streetlight.repository.AnalysisStreetlightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class StreetlightDataProcessor {

    private final AnalysisStreetlightRepository streetlightRepository;

    /**
     * 분석에 사용할 구별 가로등 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 가로등 수 데이터 맵
     */
    public Map<String, Long> getStreetlightCountMapByDistrict() {
        List<Object[]> results = streetlightRepository.findStreetlightCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 좌표 정보가 있는 가로등 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 좌표 보유 가로등 수 데이터 맵
     */
    public Map<String, Long> getStreetlightWithCoordinatesCountByDistrict() {
        List<Object[]> results = streetlightRepository.findStreetlightWithCoordinatesByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 동별 가로등 수를 조회하여 Map 형태로 반환합니다.
     * @return 구별_동별 가로등 수 데이터 맵 (구명_동명 -> 개수)
     */
    public Map<String, Long> getStreetlightCountByDistrictAndDong() {
        List<Object[]> results = streetlightRepository.findStreetlightCountByDistrictAndDong();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> row[0] + "_" + row[1], // 구명_동명
                        row -> ((Number) row[2]).longValue() // 개수
                ));
    }
}
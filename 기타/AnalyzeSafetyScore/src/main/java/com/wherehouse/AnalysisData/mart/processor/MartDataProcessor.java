package com.wherehouse.AnalysisData.mart.processor;

import com.wherehouse.AnalysisData.mart.repository.AnalysisMartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class MartDataProcessor {

    private final AnalysisMartRepository martRepository;

    /**
     * 분석에 사용할 구별 마트 수를 조회하여 Map 형태로 반환합니다.
     * 영업/폐업 상관없이 전체 마트를 집계합니다.
     * @return 자치구별 마트 수 데이터 맵
     */
    public Map<String, Long> getMartCountMapByDistrict() {
        List<Object[]> results = martRepository.findMartCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 영업중인 마트 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 영업중인 마트 수 데이터 맵
     */
    public Map<String, Long> getActiveMartCountMapByDistrict() {
        List<Object[]> results = martRepository.findActiveMartCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 폐업한 마트 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 폐업한 마트 수 데이터 맵
     */
    public Map<String, Long> getClosedMartCountMapByDistrict() {
        List<Object[]> results = martRepository.findClosedMartCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 백화점 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 백화점 수 데이터 맵
     */
    public Map<String, Long> getDepartmentStoreCountMapByDistrict() {
        List<Object[]> results = martRepository.findMartCountByDistrictAndType("백화점");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 대형마트 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 대형마트 수 데이터 맵
     */
    public Map<String, Long> getLargeMartCountMapByDistrict() {
        List<Object[]> results = martRepository.findMartCountByDistrictAndType("대형마트");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 슈퍼마켓 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 슈퍼마켓 수 데이터 맵
     */
    public Map<String, Long> getSupermarketCountMapByDistrict() {
        List<Object[]> results = martRepository.findMartCountByDistrictAndType("슈퍼마켓");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 대형 상업시설(백화점+대형마트) 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 대형 상업시설 수 데이터 맵
     */
    public Map<String, Long> getLargeScaleMartCountMapByDistrict() {
        List<Object[]> results = martRepository.findLargeScaleMartCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 좌표 정보가 있는 마트 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 좌표 정보 보유 마트 수 데이터 맵
     */
    public Map<String, Long> getMartWithCoordinatesCountMapByDistrict() {
        List<Object[]> results = martRepository.findMartWithCoordinatesCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 특정 업종의 마트 수를 조회하여 Map 형태로 반환합니다.
     * @param businessType 업종명
     * @return 자치구별 해당 업종 마트 수 데이터 맵
     */
    public Map<String, Long> getMartCountByBusinessType(String businessType) {
        List<Object[]> results = martRepository.findMartCountByDistrictAndType(businessType);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 모든 구별 마트 집계 데이터를 통합하여 반환합니다.
     * @return 구별 마트 현황 종합 데이터
     */
    public Map<String, Map<String, Long>> getAllMartStatsByDistrict() {
        Map<String, Long> totalCount = getMartCountMapByDistrict();
        Map<String, Long> activeCount = getActiveMartCountMapByDistrict();
        Map<String, Long> closedCount = getClosedMartCountMapByDistrict();
        Map<String, Long> departmentStoreCount = getDepartmentStoreCountMapByDistrict();
        Map<String, Long> largeMartCount = getLargeMartCountMapByDistrict();
        Map<String, Long> supermarketCount = getSupermarketCountMapByDistrict();
        Map<String, Long> largeScaleCount = getLargeScaleMartCountMapByDistrict();
        Map<String, Long> coordinateCount = getMartWithCoordinatesCountMapByDistrict();

        return totalCount.keySet().stream()
                .collect(Collectors.toMap(
                        district -> district,
                        district -> Map.of(
                                "총마트수", totalCount.getOrDefault(district, 0L),
                                "영업중", activeCount.getOrDefault(district, 0L),
                                "폐업", closedCount.getOrDefault(district, 0L),
                                "백화점", departmentStoreCount.getOrDefault(district, 0L),
                                "대형마트", largeMartCount.getOrDefault(district, 0L),
                                "슈퍼마켓", supermarketCount.getOrDefault(district, 0L),
                                "대형상업시설", largeScaleCount.getOrDefault(district, 0L),
                                "좌표보유", coordinateCount.getOrDefault(district, 0L)
                        )
                ));
    }
}
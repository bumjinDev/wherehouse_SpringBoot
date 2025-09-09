package com.wherehouse.AnalysisData.lodging.processor;

import com.wherehouse.AnalysisData.lodging.repository.AnalysisLodgingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LodgingDataProcessor {

    private final AnalysisLodgingRepository lodgingRepository;

    /**
     * 분석에 사용할 구별 숙박업 수를 조회하여 Map 형태로 반환합니다.
     * 영업/폐업 상관없이 전체 숙박업을 집계합니다.
     * @return 자치구별 숙박업 수 데이터 맵
     */
    public Map<String, Long> getLodgingCountMapByDistrict() {
        List<Object[]> results = lodgingRepository.findLodgingCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 영업중인 숙박업 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 영업중인 숙박업 수 데이터 맵
     */
    public Map<String, Long> getActiveLodgingCountMapByDistrict() {
        List<Object[]> results = lodgingRepository.findActiveLodgingCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 폐업한 숙박업 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 폐업한 숙박업 수 데이터 맵
     */
    public Map<String, Long> getClosedLodgingCountMapByDistrict() {
        List<Object[]> results = lodgingRepository.findClosedLodgingCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 호텔 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 호텔 수 데이터 맵
     */
    public Map<String, Long> getHotelCountMapByDistrict() {
        List<Object[]> results = lodgingRepository.findLodgingCountByDistrictAndType("호텔");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 여관 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 여관 수 데이터 맵
     */
    public Map<String, Long> getInnCountMapByDistrict() {
        List<Object[]> results = lodgingRepository.findLodgingCountByDistrictAndType("여관");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 모텔 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 모텔 수 데이터 맵
     */
    public Map<String, Long> getMotelCountMapByDistrict() {
        List<Object[]> results = lodgingRepository.findLodgingCountByDistrictAndType("모텔");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 고급 숙박시설(호텔, 리조트 등) 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 고급 숙박시설 수 데이터 맵
     */
    public Map<String, Long> getHighEndLodgingCountMapByDistrict() {
        List<Object[]> results = lodgingRepository.findHighEndLodgingCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 관광 숙박시설 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 관광 숙박시설 수 데이터 맵
     */
    public Map<String, Long> getTourismLodgingCountMapByDistrict() {
        List<Object[]> results = lodgingRepository.findTourismLodgingCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 좌표 정보가 있는 숙박업 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 좌표 정보 보유 숙박업 수 데이터 맵
     */
    public Map<String, Long> getLodgingWithCoordinatesCountMapByDistrict() {
        List<Object[]> results = lodgingRepository.findLodgingWithCoordinatesCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 특정 숙박업 유형의 수를 조회하여 Map 형태로 반환합니다.
     * @param lodgingType 숙박업 유형
     * @return 자치구별 해당 유형 숙박업 수 데이터 맵
     */
    public Map<String, Long> getLodgingCountByType(String lodgingType) {
        List<Object[]> results = lodgingRepository.findLodgingCountByDistrictAndType(lodgingType);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 모든 구별 숙박업 집계 데이터를 통합하여 반환합니다.
     * @return 구별 숙박업 현황 종합 데이터
     */
    public Map<String, Map<String, Long>> getAllLodgingStatsByDistrict() {
        Map<String, Long> totalCount = getLodgingCountMapByDistrict();
        Map<String, Long> activeCount = getActiveLodgingCountMapByDistrict();
        Map<String, Long> closedCount = getClosedLodgingCountMapByDistrict();
        Map<String, Long> hotelCount = getHotelCountMapByDistrict();
        Map<String, Long> innCount = getInnCountMapByDistrict();
        Map<String, Long> motelCount = getMotelCountMapByDistrict();
        Map<String, Long> highEndCount = getHighEndLodgingCountMapByDistrict();
        Map<String, Long> tourismCount = getTourismLodgingCountMapByDistrict();
        Map<String, Long> coordinateCount = getLodgingWithCoordinatesCountMapByDistrict();

        return totalCount.keySet().stream()
                .collect(Collectors.toMap(
                        district -> district,
                        district -> Map.of(
                                "총숙박업수", totalCount.getOrDefault(district, 0L),
                                "영업중", activeCount.getOrDefault(district, 0L),
                                "폐업", closedCount.getOrDefault(district, 0L),
                                "호텔", hotelCount.getOrDefault(district, 0L),
                                "여관", innCount.getOrDefault(district, 0L),
                                "모텔", motelCount.getOrDefault(district, 0L),
                                "고급숙박", highEndCount.getOrDefault(district, 0L),
                                "관광숙박", tourismCount.getOrDefault(district, 0L),
                                "좌표보유", coordinateCount.getOrDefault(district, 0L)
                        )
                ));
    }

    /**
     * 관광 인프라 점수를 계산합니다 (숙박업 수와 유형 기반)
     * @return 구별 관광 인프라 점수 맵
     */
    public Map<String, Double> getTourismInfraScoreByDistrict() {
        Map<String, Long> totalLodging = getLodgingCountMapByDistrict();
        Map<String, Long> highEndLodging = getHighEndLodgingCountMapByDistrict();
        Map<String, Long> tourismLodging = getTourismLodgingCountMapByDistrict();

        return totalLodging.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> calculateTourismInfraScore(
                                entry.getValue(),
                                highEndLodging.getOrDefault(entry.getKey(), 0L),
                                tourismLodging.getOrDefault(entry.getKey(), 0L)
                        )
                ));
    }

    /**
     * 관광 인프라 점수를 계산하는 내부 메서드
     */
    private Double calculateTourismInfraScore(Long total, Long highEnd, Long tourism) {
        if (total == null || total == 0) {
            return 0.0;
        }

        // 기본 점수 (총 숙박업 수 기반, 최대 70점)
        double baseScore = Math.min(total * 0.5, 70.0);

        // 고급 숙박시설 보너스 (최대 20점)
        double highEndBonus = Math.min((highEnd != null ? highEnd : 0) * 2.0, 20.0);

        // 관광 숙박시설 보너스 (최대 10점)
        double tourismBonus = Math.min((tourism != null ? tourism : 0) * 1.0, 10.0);

        return Math.min(baseScore + highEndBonus + tourismBonus, 100.0);
    }
}
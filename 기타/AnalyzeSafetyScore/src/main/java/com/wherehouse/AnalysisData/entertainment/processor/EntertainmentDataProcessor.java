package com.wherehouse.AnalysisData.entertainment.processor;

import com.wherehouse.AnalysisData.entertainment.repository.AnalysisEntertainmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EntertainmentDataProcessor {

    private final AnalysisEntertainmentRepository entertainmentRepository;

    /**
     * 분석에 사용할 구별 유흥업소 수를 조회하여 Map 형태로 반환합니다.
     * 영업/폐업 상관없이 전체 유흥업소를 집계합니다.
     * @return 자치구별 유흥업소 수 데이터 맵
     */
    public Map<String, Long> getEntertainmentCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findEntertainmentCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 영업중인 유흥업소 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 영업중인 유흥업소 수 데이터 맵
     */
    public Map<String, Long> getActiveEntertainmentCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findActiveEntertainmentCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 폐업한 유흥업소 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 폐업한 유흥업소 수 데이터 맵
     */
    public Map<String, Long> getClosedEntertainmentCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findClosedEntertainmentCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 유흥주점 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 유흥주점 수 데이터 맵
     */
    public Map<String, Long> getBarCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findEntertainmentCountByDistrictAndCategory("유흥주점");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 단란주점 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 단란주점 수 데이터 맵
     */
    public Map<String, Long> getDanranBarCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findEntertainmentCountByDistrictAndCategory("단란주점");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 노래연습장 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 노래연습장 수 데이터 맵
     */
    public Map<String, Long> getKaraokeCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findEntertainmentCountByDistrictAndCategory("노래연습장");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 당구장 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 당구장 수 데이터 맵
     */
    public Map<String, Long> getBilliardCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findEntertainmentCountByDistrictAndCategory("당구장");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 고급 유흥업소 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 고급 유흥업소 수 데이터 맵
     */
    public Map<String, Long> getHighEndEntertainmentCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findHighEndEntertainmentCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 소규모 유흥업소 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 소규모 유흥업소 수 데이터 맵
     */
    public Map<String, Long> getSmallScaleEntertainmentCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findSmallScaleEntertainmentCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 좌표 정보가 있는 유흥업소 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 좌표 정보 보유 유흥업소 수 데이터 맵
     */
    public Map<String, Long> getEntertainmentWithCoordinatesCountMapByDistrict() {
        List<Object[]> results = entertainmentRepository.findEntertainmentWithCoordinatesCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 특정 유흥업소 카테고리의 수를 조회하여 Map 형태로 반환합니다.
     * @param category 유흥업소 카테고리
     * @return 자치구별 해당 카테고리 유흥업소 수 데이터 맵
     */
    public Map<String, Long> getEntertainmentCountByCategory(String category) {
        List<Object[]> results = entertainmentRepository.findEntertainmentCountByDistrictAndCategory(category);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 모든 구별 유흥업소 집계 데이터를 통합하여 반환합니다.
     * @return 구별 유흥업소 현황 종합 데이터
     */
    public Map<String, Map<String, Long>> getAllEntertainmentStatsByDistrict() {
        Map<String, Long> totalCount = getEntertainmentCountMapByDistrict();
        Map<String, Long> activeCount = getActiveEntertainmentCountMapByDistrict();
        Map<String, Long> closedCount = getClosedEntertainmentCountMapByDistrict();
        Map<String, Long> barCount = getBarCountMapByDistrict();
        Map<String, Long> danranCount = getDanranBarCountMapByDistrict();
        Map<String, Long> karaokeCount = getKaraokeCountMapByDistrict();
        Map<String, Long> billiardCount = getBilliardCountMapByDistrict();
        Map<String, Long> highEndCount = getHighEndEntertainmentCountMapByDistrict();
        Map<String, Long> smallScaleCount = getSmallScaleEntertainmentCountMapByDistrict();
        Map<String, Long> coordinateCount = getEntertainmentWithCoordinatesCountMapByDistrict();

        return totalCount.keySet().stream()
                .collect(Collectors.toMap(
                        district -> district,
                        district -> Map.of(
                                "총유흥업소수", totalCount.getOrDefault(district, 0L),
                                "영업중", activeCount.getOrDefault(district, 0L),
                                "폐업", closedCount.getOrDefault(district, 0L),
                                "유흥주점", barCount.getOrDefault(district, 0L),
                                "단란주점", danranCount.getOrDefault(district, 0L),
                                "노래연습장", karaokeCount.getOrDefault(district, 0L),
                                "당구장", billiardCount.getOrDefault(district, 0L),
                                "고급유흥", highEndCount.getOrDefault(district, 0L),
                                "소규모유흥", smallScaleCount.getOrDefault(district, 0L),
                                "좌표보유", coordinateCount.getOrDefault(district, 0L)
                        )
                ));
    }

    /**
     * 야간 상권 활성도 점수를 계산합니다 (유흥업소 수와 유형 기반)
     * @return 구별 야간 상권 활성도 점수 맵
     */
    public Map<String, Double> getNightlifeActivityScoreByDistrict() {
        Map<String, Long> totalEntertainment = getEntertainmentCountMapByDistrict();
        Map<String, Long> highEndEntertainment = getHighEndEntertainmentCountMapByDistrict();
        Map<String, Long> barCount = getBarCountMapByDistrict();
        Map<String, Long> danranCount = getDanranBarCountMapByDistrict();

        return totalEntertainment.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> calculateNightlifeActivityScore(
                                entry.getValue(),
                                highEndEntertainment.getOrDefault(entry.getKey(), 0L),
                                barCount.getOrDefault(entry.getKey(), 0L),
                                danranCount.getOrDefault(entry.getKey(), 0L)
                        )
                ));
    }

    /**
     * 야간 상권 활성도 점수를 계산하는 내부 메서드
     */
    private Double calculateNightlifeActivityScore(Long total, Long highEnd, Long bar, Long danran) {
        if (total == null || total == 0) {
            return 0.0;
        }

        // 기본 점수 (총 유흥업소 수 기반, 최대 60점)
        double baseScore = Math.min(total * 1.2, 60.0);

        // 고급 유흥업소 보너스 (최대 25점)
        double highEndBonus = Math.min((highEnd != null ? highEnd : 0) * 5.0, 25.0);

        // 유흥주점 보너스 (최대 10점)
        double barBonus = Math.min((bar != null ? bar : 0) * 2.0, 10.0);

        // 단란주점 보너스 (최대 5점)
        double danranBonus = Math.min((danran != null ? danran : 0) * 1.0, 5.0);

        return Math.min(baseScore + highEndBonus + barBonus + danranBonus, 100.0);
    }
}
package com.wherehouse.AnalysisData.subway.processor;

import com.wherehouse.AnalysisData.subway.repository.AnalysisSubwayStationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubwayStationDataProcessor {

    private final AnalysisSubwayStationRepository subwayStationRepository;

    /**
     * 분석에 사용할 구별 지하철역 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 지하철역 수 데이터 맵
     */
    public Map<String, Long> getSubwayStationCountMapByDistrict() {
        List<Object[]> results = subwayStationRepository.findSubwayStationCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 좌표 정보가 있는 지하철역 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 좌표 정보 보유 지하철역 수 데이터 맵
     */
    public Map<String, Long> getSubwayStationWithCoordinatesCountMapByDistrict() {
        List<Object[]> results = subwayStationRepository.findSubwayStationWithCoordinatesCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 환승역 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 환승역 수 데이터 맵
     */
    public Map<String, Long> getTransferStationCountMapByDistrict() {
        List<Object[]> results = subwayStationRepository.findTransferStationCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 연락처 정보가 있는 지하철역 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 연락처 정보 보유 지하철역 수 데이터 맵
     */
    public Map<String, Long> getSubwayStationWithContactCountMapByDistrict() {
        List<Object[]> results = subwayStationRepository.findSubwayStationWithContactCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 모든 구별 지하철역 집계 데이터를 통합하여 반환합니다.
     * @return 구별 지하철역 현황 종합 데이터
     */
    public Map<String, Map<String, Long>> getAllSubwayStationStatsByDistrict() {
        Map<String, Long> totalCount = getSubwayStationCountMapByDistrict();
        Map<String, Long> coordinateCount = getSubwayStationWithCoordinatesCountMapByDistrict();
        Map<String, Long> transferCount = getTransferStationCountMapByDistrict();
        Map<String, Long> contactCount = getSubwayStationWithContactCountMapByDistrict();

        return totalCount.keySet().stream()
                .collect(Collectors.toMap(
                        district -> district,
                        district -> Map.of(
                                "총지하철역수", totalCount.getOrDefault(district, 0L),
                                "좌표보유", coordinateCount.getOrDefault(district, 0L),
                                "환승역", transferCount.getOrDefault(district, 0L),
                                "연락처보유", contactCount.getOrDefault(district, 0L)
                        )
                ));
    }

    /**
     * 대중교통 접근성 점수를 계산합니다 (지하철역 수와 환승역 기반)
     * @return 구별 대중교통 접근성 점수 맵
     */
    public Map<String, Double> getPublicTransportAccessibilityScoreByDistrict() {
        Map<String, Long> totalStations = getSubwayStationCountMapByDistrict();
        Map<String, Long> transferStations = getTransferStationCountMapByDistrict();

        return totalStations.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> calculatePublicTransportAccessibilityScore(
                                entry.getValue(),
                                transferStations.getOrDefault(entry.getKey(), 0L)
                        )
                ));
    }

    /**
     * 대중교통 접근성 점수를 계산하는 내부 메서드
     */
    private Double calculatePublicTransportAccessibilityScore(Long total, Long transfer) {
        if (total == null || total == 0) {
            return 0.0;
        }

        // 기본 점수 (지하철역 수 기반, 최대 70점)
        double baseScore = Math.min(total * 3.5, 70.0);

        // 환승역 보너스 (최대 30점)
        double transferBonus = Math.min((transfer != null ? transfer : 0) * 10.0, 30.0);

        return Math.min(baseScore + transferBonus, 100.0);
    }

    /**
     * 교통 허브 등급을 계산합니다
     * @return 구별 교통 허브 등급 맵
     */
    public Map<String, String> getTransportationHubGradeByDistrict() {
        Map<String, Long> totalStations = getSubwayStationCountMapByDistrict();
        Map<String, Long> transferStations = getTransferStationCountMapByDistrict();

        return totalStations.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> calculateTransportationHubGrade(
                                entry.getValue(),
                                transferStations.getOrDefault(entry.getKey(), 0L)
                        )
                ));
    }

    /**
     * 교통 허브 등급을 계산하는 내부 메서드
     */
    private String calculateTransportationHubGrade(Long total, Long transfer) {
        if (total == null || total == 0) {
            return "없음";
        }

        if (total >= 25 && (transfer != null && transfer >= 5)) {
            return "메가허브";
        } else if (total >= 20 && (transfer != null && transfer >= 3)) {
            return "대형허브";
        } else if (total >= 15 && (transfer != null && transfer >= 2)) {
            return "중형허브";
        } else if (total >= 10) {
            return "소형허브";
        } else if (total >= 5) {
            return "교통거점";
        } else {
            return "일반";
        }
    }
}
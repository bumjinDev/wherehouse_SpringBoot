package com.wherehouse.AnalysisData.residentcenter.processor;

import com.wherehouse.AnalysisData.residentcenter.repository.AnalysisResidentCenterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResidentCenterDataProcessor {

    private final AnalysisResidentCenterRepository residentCenterRepository;

    /**
     * 분석에 사용할 구별 주민센터 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 주민센터 수 데이터 맵
     */
    public Map<String, Long> getResidentCenterCountMapByDistrict() {
        List<Object[]> results = residentCenterRepository.findResidentCenterCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 좌표 정보가 있는 주민센터 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 좌표 정보 보유 주민센터 수 데이터 맵
     */
    public Map<String, Long> getResidentCenterWithCoordinatesCountMapByDistrict() {
        List<Object[]> results = residentCenterRepository.findResidentCenterWithCoordinatesCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 '동' 단위 주민센터 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 동 단위 주민센터 수 데이터 맵
     */
    public Map<String, Long> getDongResidentCenterCountMapByDistrict() {
        List<Object[]> results = residentCenterRepository.findResidentCenterCountByDistrictAndType("동");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 '읍' 단위 주민센터 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 읍 단위 주민센터 수 데이터 맵
     */
    public Map<String, Long> getEupResidentCenterCountMapByDistrict() {
        List<Object[]> results = residentCenterRepository.findResidentCenterCountByDistrictAndType("읍");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 '면' 단위 주민센터 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 면 단위 주민센터 수 데이터 맵
     */
    public Map<String, Long> getMyeonResidentCenterCountMapByDistrict() {
        List<Object[]> results = residentCenterRepository.findResidentCenterCountByDistrictAndType("면");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 완전한 행정구역 정보가 있는 주민센터 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 완전한 행정정보 보유 주민센터 수 데이터 맵
     */
    public Map<String, Long> getCompleteAdminInfoResidentCenterCountMapByDistrict() {
        List<Object[]> results = residentCenterRepository.findCompleteAdminInfoResidentCenterCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 특정 읍면동 유형의 주민센터 수를 조회하여 Map 형태로 반환합니다.
     * @param eupmeondongType 읍면동 유형 (읍/면/동)
     * @return 자치구별 해당 유형 주민센터 수 데이터 맵
     */
    public Map<String, Long> getResidentCenterCountByEupmeondongType(String eupmeondongType) {
        List<Object[]> results = residentCenterRepository.findResidentCenterCountByDistrictAndType(eupmeondongType);

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 모든 구별 주민센터 집계 데이터를 통합하여 반환합니다.
     * @return 구별 주민센터 현황 종합 데이터
     */
    public Map<String, Map<String, Long>> getAllResidentCenterStatsByDistrict() {
        Map<String, Long> totalCount = getResidentCenterCountMapByDistrict();
        Map<String, Long> coordinateCount = getResidentCenterWithCoordinatesCountMapByDistrict();
        Map<String, Long> dongCount = getDongResidentCenterCountMapByDistrict();
        Map<String, Long> eupCount = getEupResidentCenterCountMapByDistrict();
        Map<String, Long> myeonCount = getMyeonResidentCenterCountMapByDistrict();
        Map<String, Long> completeAdminCount = getCompleteAdminInfoResidentCenterCountMapByDistrict();

        return totalCount.keySet().stream()
                .collect(Collectors.toMap(
                        district -> district,
                        district -> Map.of(
                                "총주민센터수", totalCount.getOrDefault(district, 0L),
                                "좌표보유", coordinateCount.getOrDefault(district, 0L),
                                "동단위", dongCount.getOrDefault(district, 0L),
                                "읍단위", eupCount.getOrDefault(district, 0L),
                                "면단위", myeonCount.getOrDefault(district, 0L),
                                "완전행정정보", completeAdminCount.getOrDefault(district, 0L)
                        )
                ));
    }

    /**
     * 행정 접근성 점수를 계산합니다 (주민센터 수 기반)
     * @return 구별 행정 접근성 점수 맵
     */
    public Map<String, Double> getAdministrativeAccessibilityScoreByDistrict() {
        Map<String, Long> residentCenterCount = getResidentCenterCountMapByDistrict();

        return residentCenterCount.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> calculateAccessibilityScore(entry.getValue())
                ));
    }

    /**
     * 주민센터 수를 기반으로 행정 접근성 점수를 계산하는 내부 메서드
     */
    private Double calculateAccessibilityScore(Long count) {
        if (count == null || count == 0) {
            return 0.0;
        }

        // 주민센터 수에 따른 점수 계산 (최대 100점)
        if (count >= 30) {
            return 100.0;
        } else if (count >= 20) {
            return 80.0;
        } else if (count >= 10) {
            return 60.0;
        } else if (count >= 5) {
            return 40.0;
        } else {
            return 20.0;
        }
    }
}
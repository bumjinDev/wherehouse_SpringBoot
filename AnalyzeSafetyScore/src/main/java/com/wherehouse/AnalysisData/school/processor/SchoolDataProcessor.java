package com.wherehouse.AnalysisData.school.processor;

import com.wherehouse.AnalysisData.school.repository.AnalysisSchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SchoolDataProcessor {

    private final AnalysisSchoolRepository schoolRepository;

    /**
     * 분석에 사용할 구별 학교 수를 조회하여 Map 형태로 반환합니다.
     * 운영/폐교 상관없이 전체 학교를 집계합니다.
     * @return 자치구별 학교 수 데이터 맵
     */
    public Map<String, Long> getSchoolCountMapByDistrict() {
        List<Object[]> results = schoolRepository.findSchoolCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 운영중인 학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 운영중인 학교 수 데이터 맵
     */
    public Map<String, Long> getActiveSchoolCountMapByDistrict() {
        List<Object[]> results = schoolRepository.findActiveSchoolCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 폐교한 학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 폐교한 학교 수 데이터 맵
     */
    public Map<String, Long> getClosedSchoolCountMapByDistrict() {
        List<Object[]> results = schoolRepository.findClosedSchoolCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 초등학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 초등학교 수 데이터 맵
     */
    public Map<String, Long> getElementarySchoolCountMapByDistrict() {
        List<Object[]> results = schoolRepository.findSchoolCountByDistrictAndLevel("초등학교");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 중학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 중학교 수 데이터 맵
     */
    public Map<String, Long> getMiddleSchoolCountMapByDistrict() {
        List<Object[]> results = schoolRepository.findSchoolCountByDistrictAndLevel("중학교");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 고등학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 고등학교 수 데이터 맵
     */
    public Map<String, Long> getHighSchoolCountMapByDistrict() {
        List<Object[]> results = schoolRepository.findSchoolCountByDistrictAndLevel("고등학교");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 공립학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 공립학교 수 데이터 맵
     */
    public Map<String, Long> getPublicSchoolCountMapByDistrict() {
        List<Object[]> results = schoolRepository.findSchoolCountByDistrictAndEstablishment("공립");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 사립학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 사립학교 수 데이터 맵
     */
    public Map<String, Long> getPrivateSchoolCountMapByDistrict() {
        List<Object[]> results = schoolRepository.findSchoolCountByDistrictAndEstablishment("사립");

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 좌표 정보가 있는 학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 좌표 정보 보유 학교 수 데이터 맵
     */
    public Map<String, Long> getSchoolWithCoordinatesCountMapByDistrict() {
        List<Object[]> results = schoolRepository.findSchoolWithCoordinatesCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 모든 구별 학교 집계 데이터를 통합하여 반환합니다.
     * @return 구별 학교 현황 종합 데이터
     */
    public Map<String, Map<String, Long>> getAllSchoolStatsByDistrict() {
        Map<String, Long> totalCount = getSchoolCountMapByDistrict();
        Map<String, Long> activeCount = getActiveSchoolCountMapByDistrict();
        Map<String, Long> closedCount = getClosedSchoolCountMapByDistrict();
        Map<String, Long> elementaryCount = getElementarySchoolCountMapByDistrict();
        Map<String, Long> middleCount = getMiddleSchoolCountMapByDistrict();
        Map<String, Long> highCount = getHighSchoolCountMapByDistrict();
        Map<String, Long> publicCount = getPublicSchoolCountMapByDistrict();
        Map<String, Long> privateCount = getPrivateSchoolCountMapByDistrict();
        Map<String, Long> coordinateCount = getSchoolWithCoordinatesCountMapByDistrict();

        return totalCount.keySet().stream()
                .collect(Collectors.toMap(
                        district -> district,
                        district -> Map.of(
                                "총학교수", totalCount.getOrDefault(district, 0L),
                                "운영중", activeCount.getOrDefault(district, 0L),
                                "폐교", closedCount.getOrDefault(district, 0L),
                                "초등학교", elementaryCount.getOrDefault(district, 0L),
                                "중학교", middleCount.getOrDefault(district, 0L),
                                "고등학교", highCount.getOrDefault(district, 0L),
                                "공립", publicCount.getOrDefault(district, 0L),
                                "사립", privateCount.getOrDefault(district, 0L),
                                "좌표보유", coordinateCount.getOrDefault(district, 0L)
                        )
                ));
    }
}
package com.wherehouse.AnalysisData.cinema.processor;

import com.wherehouse.AnalysisData.cinema.repository.AnalysisCinemaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CinemaDataProcessor {

    private final AnalysisCinemaRepository cinemaRepository;

    /**
     * 분석에 사용할 구별 영화관 수를 조회하여 Map 형태로 반환합니다.
     * 영업/폐업 상관없이 전체 영화관을 집계합니다.
     * @return 자치구별 영화관 수 데이터 맵
     */
    public Map<String, Long> getCinemaCountMapByDistrict() {
        List<Object[]> results = cinemaRepository.findCinemaCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 영업중인 영화관 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 영업중인 영화관 수 데이터 맵
     */
    public Map<String, Long> getActiveCinemaCountMapByDistrict() {
        List<Object[]> results = cinemaRepository.findActiveCinemaCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 폐업한 영화관 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 폐업한 영화관 수 데이터 맵
     */
    public Map<String, Long> getClosedCinemaCountMapByDistrict() {
        List<Object[]> results = cinemaRepository.findClosedCinemaCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }
}
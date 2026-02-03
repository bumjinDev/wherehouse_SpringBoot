package com.wherehouse.AnalysisData.convenience.processor;

import com.wherehouse.AnalysisData.convenience.repository.AnalysisConvenienceStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConvenienceStoreDataProcessor {

    private final AnalysisConvenienceStoreRepository convenienceStoreRepository;

    /**
     * 분석에 사용할 구별 편의점 수를 조회하여 Map 형태로 반환합니다.
     * 영업/폐업 상관없이 전체 편의점을 집계합니다.
     * @return 자치구별 편의점 수 데이터 맵
     */
    public Map<String, Long> getConvenienceStoreCountMapByDistrict() {
        List<Object[]> results = convenienceStoreRepository.findConvenienceStoreCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 영업중인 편의점 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 영업중인 편의점 수 데이터 맵
     */
    public Map<String, Long> getActiveConvenienceStoreCountMapByDistrict() {
        List<Object[]> results = convenienceStoreRepository.findActiveConvenienceStoreCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 폐업한 편의점 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 폐업한 편의점 수 데이터 맵
     */
    public Map<String, Long> getClosedConvenienceStoreCountMapByDistrict() {
        List<Object[]> results = convenienceStoreRepository.findClosedConvenienceStoreCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }
}
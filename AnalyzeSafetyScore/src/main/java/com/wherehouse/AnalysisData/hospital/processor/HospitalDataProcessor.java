package com.wherehouse.AnalysisData.hospital.processor;

import com.wherehouse.AnalysisData.hospital.repository.AnalysisHospitalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class HospitalDataProcessor {

    private final AnalysisHospitalRepository hospitalRepository;

    /**
     * 분석에 사용할 구별 병원 수를 조회하여 Map 형태로 반환합니다.
     * 영업/폐업 상관없이 전체 병원을 집계합니다.
     * @return 자치구별 병원 수 데이터 맵
     */
    public Map<String, Long> getHospitalCountMapByDistrict() {
        List<Object[]> results = hospitalRepository.findHospitalCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 영업중인 병원 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 영업중인 병원 수 데이터 맵
     */
    public Map<String, Long> getActiveHospitalCountMapByDistrict() {
        List<Object[]> results = hospitalRepository.findActiveHospitalCountByDistrict();

        Map<String, Long> results2 = results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));

        System.out.println("ActiveHospitalCountMapByDistrict : " + results2.size());

        return results2;
    }

    /**
     * 구별 폐업한 병원 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 폐업한 병원 수 데이터 맵
     */
    public Map<String, Long> getClosedHospitalCountMapByDistrict() {
        List<Object[]> results = hospitalRepository.findClosedHospitalCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }
}
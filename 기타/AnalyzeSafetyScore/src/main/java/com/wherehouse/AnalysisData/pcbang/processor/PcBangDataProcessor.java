package com.wherehouse.AnalysisData.pcbang.processor;

import com.wherehouse.AnalysisData.pcbang.repository.AnalysisPcBangRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PcBangDataProcessor {

    private final AnalysisPcBangRepository pcBangRepository;

    /**
     * 분석에 사용할 구별 PC방 수를 조회하여 Map 형태로 반환합니다.
     * "취소/말소/만료/정지/중지" 또는 "폐업"을 제외한 정상 영업 PC방만 집계합니다.
     * @return 자치구별 PC방 수 데이터 맵
     */
    public Map<String, Long> getPcBangCountMapByDistrict() {
        List<Object[]> results = pcBangRepository.findActivePcBangCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 전체 PC방 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 전체 PC방 수 데이터 맵
     */
    public Map<String, Long> getAllPcBangCountMapByDistrict() {
        List<Object[]> results = pcBangRepository.findAllPcBangCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }

    /**
     * 구별 폐업/정지 PC방 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 폐업/정지 PC방 수 데이터 맵
     */
    public Map<String, Long> getInactivePcBangCountMapByDistrict() {
        List<Object[]> results = pcBangRepository.findInactivePcBangCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }
}
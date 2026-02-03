package com.wherehouse.AnalysisData.cctv.processor;

import com.wherehouse.AnalysisData.cctv.repository.AnalysisCctvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CctvDataProcessor {

    private final AnalysisCctvRepository analysisCctvRepository;

    /**
     * 분석에 사용할 구별 CCTV 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 CCTV 수 데이터 맵
     */
    public Map<String, Long> getCctvCountMapByDistrict() {
        List<Object[]> results = analysisCctvRepository.findCctvCountByDistrict();

        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0], // 첫 번째 요소(자치구명)
                        row -> ((Number) row[1]).longValue() // 두 번째 요소(개수)를 Long 타입으로 변환
                ));
    }
}
package com.wherehouse.AnalysisData.danran.processor;

import com.wherehouse.AnalysisData.danran.dto.DistrictDanranBarCountDto;
import com.wherehouse.AnalysisData.danran.repository.AnalysisDanranBarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DanranBarDataProcessor {

    private final AnalysisDanranBarRepository analysisDanranBarRepository;

    /**
     * 분석에 사용할 구별 유흥주점 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 유흥주점 수 데이터 맵
     */
    public Map<String, Long> getDanranBarCountMapByDistrict() {
        List<DistrictDanranBarCountDto> dtoList = analysisDanranBarRepository.findDanranBarCountByDistrict();

        return dtoList.stream()
                .collect(Collectors.toMap(
                        DistrictDanranBarCountDto::getDistrictName,
                        DistrictDanranBarCountDto::getTotalCount
                ));
    }
}
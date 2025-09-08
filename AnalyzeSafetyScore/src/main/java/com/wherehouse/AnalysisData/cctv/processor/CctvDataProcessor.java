package com.wherehouse.AnalysisData.cctv.processor;

import com.wherehouse.AnalysisData.cctv.dto.DistrictCctvCountDto;
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
        List<DistrictCctvCountDto> dtoList = analysisCctvRepository.findCctvCountByDistrict();

        return dtoList.stream()
                .collect(Collectors.toMap(
                        DistrictCctvCountDto::getDistrictName,
                        DistrictCctvCountDto::getTotalCount
                ));
    }
}
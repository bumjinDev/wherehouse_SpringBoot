package com.wherehouse.AnalysisData.convenience.Processor;

import com.wherehouse.AnalysisData.convenience.dto.DistrictConvenienceStoreCountDto;
import com.wherehouse.AnalysisData.convenience.repository.AnalysisConvenienceStoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ConvenienceStoreDataProcessor {

    private final AnalysisConvenienceStoreRepository analysisConvenienceStoreRepository;

    /**
     * 분석에 사용할 구별 편의점 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 편의점 수 데이터 맵
     */
    public Map<String, Long> getConvenienceStoreCountMapByDistrict() {
        List<DistrictConvenienceStoreCountDto> dtoList = analysisConvenienceStoreRepository.findConvenienceStoreCountByDistrict();

        return dtoList.stream()
                .collect(Collectors.toMap(
                        DistrictConvenienceStoreCountDto::getDistrictName,
                        DistrictConvenienceStoreCountDto::getTotalCount
                ));
    }
}
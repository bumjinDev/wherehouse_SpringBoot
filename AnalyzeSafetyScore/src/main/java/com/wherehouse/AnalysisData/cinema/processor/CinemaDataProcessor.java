package com.wherehouse.AnalysisData.cinema.processor;

import com.wherehouse.AnalysisData.cinema.dto.DistrictCinemaCountDto;
import com.wherehouse.AnalysisData.cinema.repository.AnalysisCinemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CinemaDataProcessor {

    private final AnalysisCinemaRepository analysisCinemaRepository;

    /**
     * 분석에 사용할 구별 영화관 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 영화관 수 데이터 맵
     */
    public Map<String, Long> getCinemaCountMapByDistrict() {
        List<DistrictCinemaCountDto> dtoList = analysisCinemaRepository.findCinemaCountByDistrict();

        return dtoList.stream()
                .collect(Collectors.toMap(
                        DistrictCinemaCountDto::getDistrictName,
                        DistrictCinemaCountDto::getTotalCount
                ));
    }
}
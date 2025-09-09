package com.wherehouse.AnalysisData.crime.processor;

import com.wherehouse.AnalysisData.crime.dto.DistrictCrimeCountDto;
import com.wherehouse.AnalysisData.crime.repository.AnalysisCrimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CrimeDataProcessor {

    private final AnalysisCrimeRepository analysisCrimeRepository;

    /**
     * 분석에 사용할 구별 총 범죄 발생 건수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 총 범죄 발생 건수 데이터 맵
     */
    public Map<String, Long> getCrimeCountMapByDistrict() {
        List<DistrictCrimeCountDto> dtoList = analysisCrimeRepository.findCrimeCount();

        return dtoList.stream()
                .collect(Collectors.toMap(
                        DistrictCrimeCountDto::getDistrictName,
                        DistrictCrimeCountDto::getTotalOccurrence
                ));
    }
}
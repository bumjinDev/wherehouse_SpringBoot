package com.wherehouse.AnalysisData.bankcount.service;

import com.wherehouse.AnalysisData.bankcount.dto.DistrictBankCountDto; // DTO 임포트
import com.wherehouse.AnalysisData.bankcount.repository.AnalysisBankCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BankCountDataProcessor {

    private final AnalysisBankCountRepository analysisBankCountRepository;

    /**
     * 분석에 사용할 구별 총 은행 수 데이터를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 총 은행 수 데이터 맵
     */
    public Map<String, Long> getBankCountMapByDistrict() {
        List<DistrictBankCountDto> dtoList = analysisBankCountRepository.findDistrictBankDensityRanking();

        // DTO 리스트를 Map으로 변환하는 로직이 훨씬 명확하고 안전해집니다.
        return dtoList.stream()
                .collect(Collectors.toMap(
                        DistrictBankCountDto::getDistrictName, // row -> (String) row[0] 대신
                        DistrictBankCountDto::getTotalBankCount  // row -> ((Number) row[1]).longValue() 대신
                ));
    }
}
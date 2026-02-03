package com.wherehouse.AnalysisData.university.processor;

import com.wherehouse.AnalysisData.university.dto.DistrictUniversityCountDto;
import com.wherehouse.AnalysisData.university.repository.AnalysisUniversityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UniversityDataProcessor {

    private final AnalysisUniversityRepository analysisUniversityRepository;

    /**
     * 분석에 사용할 구별 총 대학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 총 대학교 수 데이터 맵
     */
    public Map<String, Long> getUniversityCountMapByDistrict() {
        List<DistrictUniversityCountDto> dtoList = analysisUniversityRepository.findUniversityCountByDistrict();

        return dtoList.stream()
                .collect(Collectors.toMap(
                        DistrictUniversityCountDto::getDistrictName,
                        DistrictUniversityCountDto::getTotalUniversityCount
                ));
    }

    /**
     * 구별 설립주체별 대학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 설립주체별 대학교 수 데이터 맵
     */
    public Map<String, Map<String, Long>> getUniversityCountByEstablishmentType() {
        List<DistrictUniversityCountDto> dtoList = analysisUniversityRepository.findUniversityCountByDistrict();

        return dtoList.stream()
                .collect(Collectors.toMap(
                        DistrictUniversityCountDto::getDistrictName,
                        dto -> Map.of(
                                "국립", dto.getNationalUniversityCount(),
                                "공립", dto.getPublicUniversityCount(),
                                "사립", dto.getPrivateUniversityCount()
                        )
                ));
    }

    /**
     * 구별 학교급별 대학교 수를 조회하여 Map 형태로 반환합니다.
     * @return 자치구별 학교급별 대학교 수 데이터 맵
     */
    public Map<String, Map<String, Long>> getUniversityCountBySchoolType() {
        List<DistrictUniversityCountDto> dtoList = analysisUniversityRepository.findUniversityCountByDistrict();

        return dtoList.stream()
                .collect(Collectors.toMap(
                        DistrictUniversityCountDto::getDistrictName,
                        dto -> Map.of(
                                "전문대학", dto.getJuniorCollegeCount(),
                                "대학교", dto.getUniversityCount(),
                                "대학원", dto.getGraduateSchoolCount(),
                                "기타", dto.getOtherCount()
                        )
                ));
    }

    /**
     * 모든 구별 대학교 통계 정보를 리스트로 반환합니다.
     * @return 구별 대학교 통계 DTO 리스트
     */
    public List<DistrictUniversityCountDto> getAllDistrictUniversityStatistics() {
        return analysisUniversityRepository.findUniversityCountByDistrict();
    }
}
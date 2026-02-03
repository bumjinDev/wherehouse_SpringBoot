package com.wherehouse.AnalysisData.university.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DistrictUniversityCountDto {

    private String districtName;
    private Long totalUniversityCount;
    private Long nationalUniversityCount;
    private Long publicUniversityCount;
    private Long privateUniversityCount;
    private Long juniorCollegeCount;
    private Long universityCount;
    private Long graduateSchoolCount;
    private Long otherCount;

    /**
     * JPQL 프로젝션을 위한 생성자
     * 각 구별 대학교 통계를 집계하여 전달받습니다.
     */
    public DistrictUniversityCountDto(String districtName,
                                      Long totalUniversityCount,
                                      Long nationalUniversityCount,
                                      Long publicUniversityCount,
                                      Long privateUniversityCount,
                                      Long juniorCollegeCount,
                                      Long universityCount,
                                      Long graduateSchoolCount,
                                      Long otherCount) {
        this.districtName = districtName;
        this.totalUniversityCount = totalUniversityCount;
        this.nationalUniversityCount = nationalUniversityCount;
        this.publicUniversityCount = publicUniversityCount;
        this.privateUniversityCount = privateUniversityCount;
        this.juniorCollegeCount = juniorCollegeCount;
        this.universityCount = universityCount;
        this.graduateSchoolCount = graduateSchoolCount;
        this.otherCount = otherCount;
    }
}
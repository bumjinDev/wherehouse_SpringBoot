package com.wherehouse.recommand.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistrictCrimeCountDto {

    private Long id;
    private String districtName;
    private Long year; // 'year' 필드는 의도적으로 제외
    private Long totalOccurrence;
    private Long totalArrest;
    private Long murderOccurrence;
    private Long murderArrest;
    private Long robberyOccurrence;
    private Long robberyArrest;
    private Long sexualCrimeOccurrence;
    private Long sexualCrimeArrest;
    private Long theftOccurrence;
    private Long theftArrest;
    private Long violenceOccurrence;
    private Long violenceArrest;


    /**
     * JPQL 프로젝션을 위해 'year'를 제외한 모든 필드를 받는 생성자
     */
    public DistrictCrimeCountDto(String districtName, Long totalOccurrence,
                                 Long totalArrest, Long murderOccurrence,
                                 Long murderArrest, Long robberyOccurrence,
                                 Long robberyArrest, Long sexualCrimeOccurrence,
                                 Long sexualCrimeArrest, Long theftOccurrence,
                                 Long theftArrest, Long violenceOccurrence,
                                 Long violenceArrest) {

        this.districtName = districtName;
        this.totalOccurrence = totalOccurrence;
        this.totalArrest = totalArrest;
        this.murderOccurrence = murderOccurrence;
        this.murderArrest = murderArrest;
        this.robberyOccurrence = robberyOccurrence;
        this.robberyArrest = robberyArrest;
        this.sexualCrimeOccurrence = sexualCrimeOccurrence;
        this.sexualCrimeArrest = sexualCrimeArrest;
        this.theftOccurrence = theftOccurrence;
        this.theftArrest = theftArrest;
        this.violenceOccurrence = violenceOccurrence;
        this.violenceArrest = violenceArrest;
    }
}
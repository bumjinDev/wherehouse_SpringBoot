package com.WhereHouse.AnalysisStaticData.CommunityCenterSave.DTO;

import com.opencsv.bean.CsvBindByPosition;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ResidentCenterDto {

    @CsvBindByPosition(position = 0)
    private Integer serialNo; // 연번

    @CsvBindByPosition(position = 1)
    private String sido; // 시도

    @CsvBindByPosition(position = 2)
    private String sigungu; // 시군구

    @CsvBindByPosition(position = 3)
    private String eupmeondong; // 읍면동

    @CsvBindByPosition(position = 4)
    private String postalCode; // 우편번호

    @CsvBindByPosition(position = 5)
    private String address; // 주소

    @CsvBindByPosition(position = 6)
    private String phoneNumber; // 전화번호
}
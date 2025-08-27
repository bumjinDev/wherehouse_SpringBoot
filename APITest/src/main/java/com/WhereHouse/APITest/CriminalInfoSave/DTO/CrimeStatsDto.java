package com.WhereHouse.API.Test.APITest.CriminalInfoSave.DTO;

import com.opencsv.bean.CsvBindByPosition;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class CrimeStatsDto {

    // CsvBindByPosition: CSV의 위치(순서)를 기반으로 필드에 데이터를 매핑합니다.
    // 0부터 시작합니다.

    @CsvBindByPosition(position = 1)
    private String district; // 자치구

    @CsvBindByPosition(position = 2)
    private int totalOccurrences; // 발생 합계

    @CsvBindByPosition(position = 3)
    private int totalArrests; // 검거 합계

    @CsvBindByPosition(position = 4)
    private int murderOccurrences; // 살인 발생

    @CsvBindByPosition(position = 5)
    private int murderArrests; // 살인 검거

    @CsvBindByPosition(position = 6)
    private int robberyOccurrences; // 강도 발생

    @CsvBindByPosition(position = 7)
    private int robberyArrests; // 강도 검거

    @CsvBindByPosition(position = 8)
    private int rapeOccurrences; // 강간·강제추행 발생

    @CsvBindByPosition(position = 9)
    private int rapeArrests; // 강간·강제추행 검거

    @CsvBindByPosition(position = 10)
    private int theftOccurrences; // 절도 발생

    @CsvBindByPosition(position = 11)
    private int theftArrests; // 절도 검거

    @CsvBindByPosition(position = 12)
    private int violenceOccurrences; // 폭력 발생

    @CsvBindByPosition(position = 13)
    private int violenceArrests; // 폭력 검거
}
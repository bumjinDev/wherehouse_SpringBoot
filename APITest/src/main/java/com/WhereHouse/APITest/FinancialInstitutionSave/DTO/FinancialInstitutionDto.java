package com.WhereHouse.API.Test.APITest.FinancialInstitutionSave.DTO;

import com.opencsv.bean.CsvBindByPosition;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class FinancialInstitutionDto {

    @CsvBindByPosition(position = 1)
    private String sigungu; // 시군구

    @CsvBindByPosition(position = 2)
    private String dong; // 동

    @CsvBindByPosition(position = 3)
    private Integer wooriBankCount; // 우리은행

    @CsvBindByPosition(position = 4)
    private Integer scBankCount; // SC제일은행

    @CsvBindByPosition(position = 5)
    private Integer kbBankCount; // KB국민은행

    @CsvBindByPosition(position = 6)
    private Integer shinhanBankCount; // 신한은행

    @CsvBindByPosition(position = 7)
    private Integer citiBankCount; // 한국씨티은행

    @CsvBindByPosition(position = 8)
    private Integer hanaBankCount; // KEB하나은행

    @CsvBindByPosition(position = 9)
    private Integer ibkBankCount; // IBK기업은행

    @CsvBindByPosition(position = 10)
    private Integer nhBankCount; // NH농협은행

    @CsvBindByPosition(position = 11)
    private Integer suhyupBankCount; // 수협은행

    @CsvBindByPosition(position = 12)
    private Integer kdbBankCount; // KDB산업은행

    @CsvBindByPosition(position = 13)
    private Integer eximBankCount; // 한국수출입은행

    @CsvBindByPosition(position = 14)
    private Integer foreignBankCount; // 외국은행
}
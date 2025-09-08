package com.wherehouse.AnalysisData.bankcount.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ANALYSIS_BANKCOUNT_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisBankCountStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_bankcount_seq")
    @SequenceGenerator(name = "analysis_bankcount_seq", sequenceName = "SEQ_ANALYSIS_BANKCOUNT_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SIGUNGU", length = 100)
    private String sigungu;

    @Column(name = "DONG", length = 100)
    private String dong;

    @Column(name = "WOORI_BANK")
    private Integer wooriBankCount;

    @Column(name = "SC_BANK")
    private Integer scBankCount;

    @Column(name = "KB_BANK")
    private Integer kbBankCount;

    @Column(name = "SHINHAN_BANK")
    private Integer shinhanBankCount;

    @Column(name = "CITI_BANK")
    private Integer citiBankCount;

    @Column(name = "HANA_BANK")
    private Integer hanaBankCount;

    @Column(name = "IBK_BANK")
    private Integer ibkBankCount;

    @Column(name = "NH_BANK")
    private Integer nhBankCount;

    @Column(name = "SUHYUP_BANK")
    private Integer suhyupBankCount;

    @Column(name = "KDB_BANK")
    private Integer kdbBankCount;

    @Column(name = "EXIM_BANK")
    private Integer eximBankCount;

    @Column(name = "FOREIGN_BANK")
    private Integer foreignBankCount;
}
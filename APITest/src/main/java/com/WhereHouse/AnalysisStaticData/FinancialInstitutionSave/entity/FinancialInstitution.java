package com.WhereHouse.APITest.FinancialInstitutionSave.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "FINANCIAL_INSTITUTION",
        uniqueConstraints = @UniqueConstraint(columnNames = {"SIGUNGU", "DONG"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialInstitution {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "financial_institution_seq")
    @SequenceGenerator(name = "financial_institution_seq", sequenceName = "SEQ_FINANCIAL_INSTITUTION", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SIGUNGU", nullable = false, length = 100)
    private String sigungu;

    @Column(name = "DONG", nullable = false, length = 100)
    private String dong;

    @Column(name = "WOORI_BANK")
    private Integer wooriBankCount = 0;

    @Column(name = "SC_BANK")
    private Integer scBankCount = 0;

    @Column(name = "KB_BANK")
    private Integer kbBankCount = 0;

    @Column(name = "SHINHAN_BANK")
    private Integer shinhanBankCount = 0;

    @Column(name = "CITI_BANK")
    private Integer citiBankCount = 0;

    @Column(name = "HANA_BANK")
    private Integer hanaBankCount = 0;

    @Column(name = "IBK_BANK")
    private Integer ibkBankCount = 0;

    @Column(name = "NH_BANK")
    private Integer nhBankCount = 0;

    @Column(name = "SUHYUP_BANK")
    private Integer suhyupBankCount = 0;

    @Column(name = "KDB_BANK")
    private Integer kdbBankCount = 0;

    @Column(name = "EXIM_BANK")
    private Integer eximBankCount = 0;

    @Column(name = "FOREIGN_BANK")
    private Integer foreignBankCount = 0;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
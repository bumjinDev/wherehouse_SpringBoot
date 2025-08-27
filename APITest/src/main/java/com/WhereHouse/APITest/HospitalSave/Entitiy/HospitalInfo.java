package com.WhereHouse.API.Test.APITest.HospitalSave.HospitalInfo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "HOSPITAL_INFO")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HospitalInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hospital_seq")
    @SequenceGenerator(name = "hospital_seq", sequenceName = "SEQ_HOSPITAL_INFO", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "YADM_NM", nullable = false, length = 200)
    private String yadmNm;

    @Column(name = "CL_CD", length = 10)
    private String clCd;

    @Column(name = "CL_CD_NM", length = 50)
    private String clCdNm;

    @Column(name = "SIDO_CD", length = 10)
    private String sidoCd;

    @Column(name = "SIDO_CD_NM", length = 50)
    private String sidoCdNm;

    @Column(name = "SGGU_CD", length = 10)
    private String sgguCd;

    @Column(name = "SGGU_CD_NM", length = 50)
    private String sgguCdNm;

    @Column(name = "EMDONG_NM", length = 100)
    private String emdongNm;

    @Column(name = "POST_NO", length = 10)
    private String postNo;

    @Column(name = "ADDR", length = 500)
    private String addr;

    @Column(name = "TELNO", length = 20)
    private String telno;

    @Column(name = "HOSP_URL", length = 500)
    private String hospUrl;

    @Column(name = "ESTB_DD", length = 8)
    private String estbDd;

    @Column(name = "DR_TOT_CNT")
    private Integer drTotCnt = 0;

    @Column(name = "PNURS_CNT")
    private Integer pnursCnt = 0;

    @Column(name = "TRMT_SUBJ_CNT")
    private Integer trmtSubjCnt = 0;

    @Column(name = "X_POS", precision = 15, scale = 10)
    private BigDecimal xPos;

    @Column(name = "Y_POS", precision = 15, scale = 10)
    private BigDecimal yPos;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
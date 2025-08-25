package com.WhereHouse.API.Test.APITest.ConvenienceStore.Entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CONVENIENCE_STORE_DATA")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConvenienceStoreData {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "convenience_seq")
    @SequenceGenerator(name = "convenience_seq", sequenceName = "SEQ_CONVENIENCE_STORE", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "OBJT_ID", nullable = false, unique = true)
    private Long objtId;

    @Column(name = "FCLTY_TY", length = 500)
    private String fcltyTy = "편의점";

    @Column(name = "FCLTY_CD", length = 6)
    private String fcltyCd = "509010";

    @Column(name = "FCLTY_NM", nullable = false, length = 500)
    private String fcltyNm;

    @Column(name = "ADRES", length = 500)
    private String adres;

    @Column(name = "RN_ADRES", length = 500)
    private String rnAdres;

    @Column(name = "TELNO", length = 20)
    private String telno;

    @Column(name = "CTPRVN_CD", length = 2)
    private String ctprvnCd;

    @Column(name = "SGG_CD", length = 5)
    private String sggCd;

    @Column(name = "EMD_CD", length = 8)
    private String emdCd;

    @Column(name = "X_COORDINATE")
    private BigDecimal xCoordinate;

    @Column(name = "Y_COORDINATE")
    private BigDecimal yCoordinate;

    @Column(name = "DATA_YR", length = 20)
    private String dataYr;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
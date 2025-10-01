package com.aws.database.ENTERTAINMENT.domain; // 패키지명 소문자로 수정 권장

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal; // BigDecimal import 추가

@Entity
@Table(name = "ANALYSIS_ENTERTAINMENT_STATISTICS")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisEntertainmentStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_entertainment_seq")
    @SequenceGenerator(name = "analysis_entertainment_seq", sequenceName = "SEQ_ANALYSIS_ENTERTAINMENT_STATISTICS", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "BUSINESS_STATUS_NAME", length = 4000)
    private String businessStatusName;

    @Column(name = "PHONE_NUMBER", length = 4000)
    private String phoneNumber;

    @Column(name = "JIBUN_ADDRESS", length = 4000)
    private String jibunAddress;

    @Column(name = "ROAD_ADDRESS", length = 4000)
    private String roadAddress;

    @Column(name = "BUSINESS_NAME", length = 4000)
    private String businessName;

    @Column(name = "BUSINESS_CATEGORY", length = 4000)
    private String businessCategory;

    @Column(name = "HYGIENE_BUSINESS_TYPE", length = 4000)
    private String hygieneBusinessType;

    // 데이터 타입을 BigDecimal로 변경
    @Column(name = "LATITUDE", precision = 10, scale = 7)
    private BigDecimal latitude;

    // 데이터 타입을 BigDecimal로 변경
    @Column(name = "LONGITUDE", precision = 10, scale = 7)
    private BigDecimal longitude;
}
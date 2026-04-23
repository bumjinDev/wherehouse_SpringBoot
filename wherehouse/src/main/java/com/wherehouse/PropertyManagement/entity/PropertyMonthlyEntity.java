package com.wherehouse.PropertyManagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 월세 매물 JPA Entity (설계 명세서 섹션 4.3, 8.1).
 *
 * 대응 테이블: PROPERTIES_MONTHLY (24개 컬럼 = 기존 17개 + 신규 7개)
 *
 * Charter Entity 와의 차이점:
 *   1. MONTHLY_RENT 컬럼 — 월세금(만원). Charter 에는 존재하지 않음.
 *   2. USER_PROPOSED_MONTHLY_RENT 컬럼 — Monthly 전용 F007 선제 반영 컬럼.
 *   3. LEASE_TYPE 저장값 — 한글 "월세" (Charter 는 "전세", 기존 관례).
 *
 * 공통 구조는 PropertyCharterEntity 와 동일한 설계 결정을 따른다:
 *   - PROPERTY_ID 는 CHAR(32) 애플리케이션 주입 PK(섹션 9.1.1)
 *   - DEAL_DATE, RGST_DATE 는 String 매핑(TD-001)
 *   - DATA_SOURCE, STATUS 는 @Enumerated(EnumType.STRING)
 *   - @DynamicInsert / @DynamicUpdate 로 PATCH 시맨틱 지원
 *
 * Entity 공통 부모 클래스를 두지 않은 근거:
 *   1) MappedSuperclass 로 공통 필드를 추출하면 Lombok @Builder 가
 *      부모 필드를 인식하지 못해 @SuperBuilder 전환이 강제됨.
 *   2) 기존 프로젝트의 다른 Entity(리뷰 도메인)들도 상속 미사용 관례.
 *   3) 두 Entity 의 차이점이 2개 컬럼에 국한되어 중복 부담이 크지 않음.
 *
 * ────────────────────────────────────────────────────────────
 * REGISTERED_USER_ID 길이 통일 (2026-04-23 결정 반영)
 * ────────────────────────────────────────────────────────────
 * 초기 DDL 생성 시 Charter VARCHAR2(100), Monthly VARCHAR2(50) 로 불일치가 있었으나
 * ALTER TABLE 로 Monthly 를 100 으로 통일. 본 Entity 도 length=100 로 매핑.
 */
@Entity
@Table(name = "PROPERTIES_MONTHLY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class PropertyMonthlyEntity {

    // ============================================================
    // 기존 컬럼 (17개) — 설계 명세서 8.1.5 "변경 없음" 준수
    // ============================================================

    /**
     * 매물 식별자. MD5 해시 32자 Hex String.
     */
    @Id
    @Column(name = "PROPERTY_ID", length = 32, columnDefinition = "CHAR(32)", nullable = false)
    private String propertyId;

    /**
     * 아파트·건물명. NOT NULL.
     */
    @Column(name = "APT_NM", length = 100, nullable = false)
    private String aptNm;

    /**
     * 전용면적(㎡). NUMBER(10, 4) NOT NULL.
     */
    @Column(name = "EXCLU_USE_AR", precision = 10, scale = 4, nullable = false)
    private BigDecimal excluUseAr;

    /**
     * 층수. NUMBER(3, 0) NOT NULL.
     */
    @Column(name = "FLOOR", nullable = false)
    private Integer floor;

    /**
     * 건축연도. NUMBER(4, 0) NULL 허용.
     */
    @Column(name = "BUILD_YEAR")
    private Integer buildYear;

    /**
     * 계약일자. VARCHAR2(10 BYTE) NULL 허용.
     * TD-001: DB 컬럼이 VARCHAR2 로 저장되어 있어 String 타입 유지 (향후 DATE 타입 전환 예정).
     */
    @Column(name = "DEAL_DATE", length = 10)
    private String dealDate;

    /**
     * 월세 보증금(만원). NUMBER(15, 0) NULL 허용.
     */
    @Column(name = "DEPOSIT", precision = 15)
    private Long deposit;

    /**
     * 월세금(만원). NUMBER(10, 0) NULL 허용.
     * Monthly 테이블 전용 컬럼.
     */
    @Column(name = "MONTHLY_RENT", precision = 10)
    private Long monthlyRent;

    /**
     * 임대 유형. VARCHAR2(10 BYTE). 한글 "월세" 저장(기존 관례).
     */
    @Column(name = "LEASE_TYPE", length = 10)
    private String leaseType;

    /**
     * 법정동명. VARCHAR2(100 BYTE) NULL 허용.
     */
    @Column(name = "UMD_NM", length = 100)
    private String umdNm;

    /**
     * 지번. NOT NULL.
     */
    @Column(name = "JIBUN", length = 50, nullable = false)
    private String jibun;

    /**
     * 시군구 코드. NOT NULL.
     */
    @Column(name = "SGG_CD", length = 10, nullable = false)
    private String sggCd;

    /**
     * 전체 주소 (파생값).
     */
    @Column(name = "ADDRESS", length = 200)
    private String address;

    /**
     * 전용면적(평, 파생값).
     */
    @Column(name = "AREA_IN_PYEONG", precision = 10, scale = 4)
    private BigDecimal areaInPyeong;

    /**
     * 배치 적재 시각(문자열 표현, 기존 배치 관례).
     */
    @Column(name = "RGST_DATE", length = 12)
    private String rgstDate;

    /**
     * 지역구명.
     */
    @Column(name = "DISTRICT_NAME", length = 50)
    private String districtName;

    /**
     * 최종 갱신 시각. 배치 파이프라인이 갱신.
     */
    @Column(name = "LAST_UPDATED")
    private LocalDateTime lastUpdated;

    // ============================================================
    // 신규 컬럼 (7개) — 설계 명세서 8.1.1
    // ============================================================

    /**
     * 데이터 출처. DDL DEFAULT 'BATCH', NOT NULL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "DATA_SOURCE", length = 10, nullable = false)
    private DataSource dataSource;

    /**
     * 매물 상태. DDL DEFAULT 'ACTIVE', NOT NULL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 10, nullable = false)
    private PropertyStatus status;

    /**
     * 등록자 식별자. 배치 매물은 NULL.
     * ALTER TABLE 로 Charter 와 동일 100 BYTE 로 통일 완료(2026-04-23).
     */
    @Column(name = "REGISTERED_USER_ID", length = 100)
    private String registeredUserId;

    /**
     * 사용자 등록 시각. 배치 매물은 NULL.
     */
    @Column(name = "REGISTERED_AT")
    private LocalDateTime registeredAt;

    /**
     * 사용자 매물 수정 시각.
     */
    @Column(name = "MODIFIED_AT")
    private LocalDateTime modifiedAt;

    /**
     * 사용자 원본 월세 보증금(만원). F007 선제 반영.
     */
    @Column(name = "USER_PROPOSED_DEPOSIT", precision = 15)
    private Long userProposedDeposit;

    /**
     * 사용자 원본 월세금(만원). F007 선제 반영. Monthly 전용.
     */
    @Column(name = "USER_PROPOSED_MONTHLY_RENT", precision = 10)
    private Long userProposedMonthlyRent;
}
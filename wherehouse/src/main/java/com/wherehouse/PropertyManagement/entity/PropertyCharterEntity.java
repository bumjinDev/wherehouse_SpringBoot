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
 * 전세 매물 JPA Entity (설계 명세서 섹션 4.3, 8.1).
 *
 * 대응 테이블: PROPERTIES_CHARTER (22개 컬럼 = 기존 16개 + 신규 6개)
 *
 * ────────────────────────────────────────────────────────────
 * PK 전략
 * ────────────────────────────────────────────────────────────
 * PROPERTY_ID 는 CHAR(32 BYTE) 로 저장되며 불변 속성 5개(시군구코드·지번·아파트명·층·
 * 전용면적)의 MD5 해시 32자 Hex String. 자동 생성이 아닌 애플리케이션 계층에서
 * IdGenerator 로 사전 산출된 값을 주입(섹션 9.1.1). 따라서 @GeneratedValue 미사용.
 *
 * ────────────────────────────────────────────────────────────
 * 타입 매핑 특이사항
 * ────────────────────────────────────────────────────────────
 * 1. DEAL_DATE (TD-001): DB 가 VARCHAR2(10 BYTE) 로 문자열 저장.
 *    설계 명세서 8.1.5 전제(기존 컬럼 변경 없음) 준수로 Entity 필드도 String 유지.
 *    향후 DATE 타입 전환은 기술 부채로 기록.
 *
 * 2. RGST_DATE: DB VARCHAR2(12 BYTE). 배치 적재 시각의 문자열 표현(기존 배치 관례).
 *    사용자 등록 경로에서는 사용되지 않으며 REGISTERED_AT(TIMESTAMP)과 의미가 다름.
 *
 * 3. LEASE_TYPE: DB VARCHAR2(10 BYTE) 로 한글 "전세" 저장(기존 관례, 섹션 8.3).
 *    본 Entity 에서는 String 필드로 유지. 신규 Enum 을 이 필드에 적용하지 않음.
 *
 * ────────────────────────────────────────────────────────────
 * 신규 컬럼과 Enum 매핑
 * ────────────────────────────────────────────────────────────
 * DATA_SOURCE, STATUS 는 VARCHAR2(10) 컬럼에 대문자 영문 문자열로 저장되며,
 * @Enumerated(EnumType.STRING) 으로 Enum 자동 변환. Enum 의 name() 이 DB 값과 일치.
 *
 * ────────────────────────────────────────────────────────────
 * DynamicInsert / DynamicUpdate 사용 근거
 * ────────────────────────────────────────────────────────────
 * @DynamicInsert: NULL 값 컬럼을 INSERT 문에서 제외하여 DDL DEFAULT 값이 적용되도록 함.
 *                 사용자 매물 등록 시 DATA_SOURCE 를 명시하지 않으면 'BATCH' 가 적용되는
 *                 문제를 방지하기 위한 안전장치는 필요 없으나(서비스 계층이 명시 주입),
 *                 USER_PROPOSED_DEPOSIT 등 NULL 허용 컬럼을 INSERT 문에서 제외해 쿼리 간결화.
 * @DynamicUpdate: UPDATE 문에서 변경된 컬럼만 갱신. F002 매물 수정이 PATCH 시맨틱으로
 *                 일부 필드만 변경하므로 이 설정이 쿼리 효율에 기여.
 */
@Entity
@Table(name = "PROPERTIES_CHARTER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class PropertyCharterEntity {

    // ============================================================
    // 기존 컬럼 (16개) — 설계 명세서 8.1.5 "변경 없음" 준수
    // ============================================================

    /**
     * 매물 식별자. MD5 해시 32자 Hex String.
     * CHAR(32 BYTE) 고정 길이로 저장.
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
     * 전세금(만원). NUMBER(15, 0) NULL 허용.
     */
    @Column(name = "DEPOSIT", precision = 15)
    private Long deposit;

    /**
     * 임대 유형. VARCHAR2(10 BYTE). 한글 "전세" 저장(기존 관례).
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
     * 전체 주소 (파생값). 시군구명 + 법정동 + 지번 조합.
     */
    @Column(name = "ADDRESS", length = 200)
    private String address;

    /**
     * 전용면적(평, 파생값). NUMBER(10, 4).
     */
    @Column(name = "AREA_IN_PYEONG", precision = 10, scale = 4)
    private BigDecimal areaInPyeong;

    /**
     * 배치 적재 시각(문자열 표현, 기존 배치 관례).
     */
    @Column(name = "RGST_DATE", length = 12)
    private String rgstDate;

    /**
     * 지역구명. 예: "강남구".
     */
    @Column(name = "DISTRICT_NAME", length = 50)
    private String districtName;

    /**
     * 최종 갱신 시각. 배치 파이프라인이 갱신.
     */
    @Column(name = "LAST_UPDATED")
    private LocalDateTime lastUpdated;

    // ============================================================
    // 신규 컬럼 (6개) — 설계 명세서 8.1.1
    // ============================================================

    /**
     * 데이터 출처. DDL DEFAULT 'BATCH', NOT NULL.
     * Enum 값: BATCH, USER, MERGED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "DATA_SOURCE", length = 10, nullable = false)
    private DataSource dataSource;

    /**
     * 매물 상태. DDL DEFAULT 'ACTIVE', NOT NULL.
     * Enum 값: ACTIVE, COMPLETED, DELETED.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 10, nullable = false)
    private PropertyStatus status;

    /**
     * 등록자 식별자. 배치 매물은 NULL.
     * Charter 테이블은 VARCHAR2(100 BYTE).
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
     * 사용자 원본 전세금(만원). F007 선제 반영(섹션 11.5).
     * 본 설계 범위에서 값이 주입되지 않음.
     */
    @Column(name = "USER_PROPOSED_DEPOSIT", precision = 15)
    private Long userProposedDeposit;
}
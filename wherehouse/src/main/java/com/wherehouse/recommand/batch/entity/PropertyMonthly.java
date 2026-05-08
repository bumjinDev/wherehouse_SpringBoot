package com.wherehouse.recommand.batch.entity;

import com.wherehouse.PropertyManagement.entity.DataSource;
import com.wherehouse.PropertyManagement.entity.PropertyStatus;
import com.wherehouse.recommand.batch.dto.Property;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 월세 매물 엔티티
 *
 * 설계 명세서: 7.1.1 B. PROPERTIES_MONTHLY
 * 용도: 월세 매물의 마스터 데이터 저장 (Oracle RDB)
 * PK 생성 전략: MD5(SGG_CD | JIBUN | APT_NM | FLOOR | EXCLU_USE_AR)
 */
@Entity
@Table(name = "PROPERTIES_MONTHLY")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyMonthly {

    /**
     * 매물 식별자 (MD5 Hash 32자)
     * 불변 속성을 조합하여 생성된 Business Key
     */
    @Id
    @Column(name = "PROPERTY_ID", length = 32, nullable = false)
    private String propertyId;

    /**
     * 아파트/건물명
     */
    @Column(name = "APT_NM", nullable = false)
    private String aptNm;

    /**
     * 전용면적 (㎡)
     */
    @Column(name = "EXCLU_USE_AR", nullable = false)
    private Double excluUseAr;

    /**
     * 층수
     */
    @Column(name = "FLOOR", nullable = false)
    private Integer floor;

    /**
     * 건축연도
     */
    @Column(name = "BUILD_YEAR")
    private Integer buildYear;

    /**
     * 계약일자 (YYYY-MM-DD)
     */
    @Column(name = "DEAL_DATE", length = 10)
    private String dealDate;

    /**
     * 보증금 (만원)
     */
    @Column(name = "DEPOSIT")
    private Integer deposit;

    /**
     * 월세금 (만원)
     * 전세 테이블과 달리 월세금 컬럼이 존재함
     */
    @Column(name = "MONTHLY_RENT")
    private Integer monthlyRent;

    /**
     * 임대유형 ("월세" 고정값)
     */
    @Column(name = "LEASE_TYPE", length = 10)
    private String leaseType;

    /**
     * 법정동명
     */
    @Column(name = "UMD_NM")
    private String umdNm;

    /**
     * 지번
     */
    @Column(name = "JIBUN", nullable = false)
    private String jibun;

    /**
     * 시군구코드
     */
    @Column(name = "SGG_CD", nullable = false)
    private String sggCd;

    /**
     * 전체 주소 (파생값)
     */
    @Column(name = "ADDRESS")
    private String address;

    /**
     * 전용면적 (평)
     */
    @Column(name = "AREA_IN_PYEONG")
    private Double areaInPyeong;

    /**
     * 등록일자
     */
    @Column(name = "RGST_DATE", length = 12)
    private String rgstDate;

    /**
     * 지역구명
     */
    @Column(name = "DISTRICT_NAME")
    private String districtName;

    /**
     * 최종 동기화 일시 (시스템 관리용)
     */
    @Column(name = "LAST_UPDATED")
    private LocalDateTime lastUpdated;

    // F005 확장 필드 (설계 명세서 8.1.1)

    @Enumerated(EnumType.STRING)
    @Column(name = "DATA_SOURCE", length = 10, nullable = false)
    private DataSource dataSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 10, nullable = false)
    private PropertyStatus status;

    @Column(name = "REGISTERED_USER_ID", length = 100)
    private String registeredUserId;

    @Column(name = "REGISTERED_AT")
    private LocalDateTime registeredAt;

    @Column(name = "MODIFIED_AT")
    private LocalDateTime modifiedAt;

    @Column(name = "USER_PROPOSED_DEPOSIT", precision = 15)
    private Long userProposedDeposit;

    @Column(name = "USER_PROPOSED_MONTHLY_RENT", precision = 10)
    private Long userProposedMonthlyRent;

    /**
     * 배치용 DTO(Property)를 엔티티(PropertyMonthly)로 변환하는 정적 팩토리 메서드
     *
     * @param dto 배치 수집 단계에서 넘어온 Property 객체
     * @return RDB 저장용 PropertyMonthly 엔티티
     */
    public static PropertyMonthly from(Property dto) {
        return PropertyMonthly.builder()
                .propertyId(dto.getPropertyId())
                .aptNm(dto.getAptNm())
                .excluUseAr(dto.getExcluUseAr())
                .floor(dto.getFloor())
                .buildYear(dto.getBuildYear())
                .dealDate(dto.getDealDate())
                .deposit(dto.getDeposit())
                .monthlyRent(dto.getMonthlyRent())
                .leaseType("월세")
                .umdNm(dto.getUmdNm())
                .jibun(dto.getJibun())
                .sggCd(dto.getSggCd())
                .address(dto.getAddress())
                .areaInPyeong(dto.getAreaInPyeong())
                .rgstDate(dto.getRgstDate())
                .districtName(dto.getDistrictName())
                .lastUpdated(LocalDateTime.now())
                .dataSource(dto.getDataSource() != null ? DataSource.valueOf(dto.getDataSource()) : DataSource.BATCH)
                .status(dto.getStatus() != null ? PropertyStatus.valueOf(dto.getStatus()) : PropertyStatus.ACTIVE)
                .registeredUserId(dto.getRegisteredUserId())
                .registeredAt(dto.getRegisteredAt() != null ? LocalDateTime.parse(dto.getRegisteredAt()) : null)
                .modifiedAt(dto.getModifiedAt() != null ? LocalDateTime.parse(dto.getModifiedAt()) : null)
                .build();
    }
}
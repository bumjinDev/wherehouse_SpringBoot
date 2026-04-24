package com.wherehouse.PropertyManagement.repository;

import com.wherehouse.PropertyManagement.entity.DataSource;
import com.wherehouse.PropertyManagement.entity.PropertyCharterEntity;
import com.wherehouse.PropertyManagement.entity.PropertyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 전세 매물 Repository.
 *
 * 설계 명세서: 섹션 5.2 (컴포넌트 구조), 섹션 9.4.1 (F004 목록 조회).
 *
 * leaseType 미지정 시의 Charter·Monthly 병합은 PropertyQueryService 책임.
 * 본 Repository 는 PROPERTIES_CHARTER 단일 테이블만 다룬다.
 *
 * 기본 제공 메서드 사용 지점:
 *   - findById       : F002·F003 권한 검증(섹션 9.2.3), F004 상세 조회(섹션 9.4.2)
 *   - existsById     : F006 중복 감지 스텁 구현체가 호출
 *   - save           : F001 신규 저장, F002·F003 Dirty Checking 갱신
 *
 *   ! 현재 키워드를 중간 부분 일치로 처리하여서 인덱스 FullScan 발생 될 것이며 이를 해결할 기술 필요.
 */
@Repository
public interface PropertyCharterRegistrationRepository extends JpaRepository<PropertyCharterEntity, String> {

    /**
     * F004 매물 목록 조회 — 일반 정렬용 (priceDesc/priceAsc/areaDesc/areaAsc).
     *
     * 정렬은 Pageable.Sort 에 위임하므로 본 쿼리에 ORDER BY 없음.
     * DELETED 는 쿼리 조건에서 강제 제외됨 (섹션 6.4).
     *
     * @param status        NULL 허용. NULL 이면 DELETED 제외한 전체.
     * @param dataSource    NULL 허용.
     * @param districtName  NULL 허용. 정확 일치.
     * @param keyword       NULL 허용. aptNm 또는 districtName 부분 일치.
     */
    @Query("SELECT p FROM PropertyCharterEntity p " +
           "WHERE p.status <> com.wherehouse.PropertyManagement.entity.PropertyStatus.DELETED " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:dataSource IS NULL OR p.dataSource = :dataSource) " +
           "AND (:districtName IS NULL OR p.districtName = :districtName) " +
           "AND (:keyword IS NULL " +
           "     OR p.aptNm LIKE %:keyword% " +
           "     OR p.districtName LIKE %:keyword%)")
    Page<PropertyCharterEntity> findByFilters(
            @Param("status") PropertyStatus status,
            @Param("dataSource") DataSource dataSource,
            @Param("districtName") String districtName,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * F004 매물 목록 조회 — latest 정렬 전용.
     *
     * ORDER BY COALESCE(modifiedAt, registeredAt, lastUpdated) DESC
     * (섹션 7.5.1, 9.4.1).
     *
     * 호출 시 Pageable 의 Sort 는 반드시 Sort.unsorted() 로 전달한다.
     * Pageable.Sort 가 지정되면 Spring Data JPA 가 본 쿼리의 ORDER BY 뒤에
     * 추가 정렬 기준을 덧붙여 의도와 다르게 동작한다.
     */
    @Query("SELECT p FROM PropertyCharterEntity p " +
           "WHERE p.status <> com.wherehouse.PropertyManagement.entity.PropertyStatus.DELETED " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:dataSource IS NULL OR p.dataSource = :dataSource) " +
           "AND (:districtName IS NULL OR p.districtName = :districtName) " +
           "AND (:keyword IS NULL " +
           "     OR p.aptNm LIKE %:keyword% " +
           "     OR p.districtName LIKE %:keyword%) " +
           "ORDER BY COALESCE(p.modifiedAt, p.registeredAt, p.lastUpdated) DESC")
    Page<PropertyCharterEntity> findByFiltersOrderByLatest(
            @Param("status") PropertyStatus status,
            @Param("dataSource") DataSource dataSource,
            @Param("districtName") String districtName,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}

package com.wherehouse.PropertyManagement.repository;

import com.wherehouse.PropertyManagement.entity.DataSource;
import com.wherehouse.PropertyManagement.entity.PropertyMonthlyEntity;
import com.wherehouse.PropertyManagement.entity.PropertyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 월세 매물 Repository.
 *
 * 설계 명세서: 섹션 5.2, 섹션 9.4.1.
 * 구조·쿼리 전략은 PropertyCharterRepository 와 동일. 대상 테이블만 PROPERTIES_MONTHLY.
 *
 * 기본 제공 메서드 사용 지점은 PropertyCharterRepository 참조.
 */
@Repository
public interface PropertyMonthlyRegistrationRepository extends JpaRepository<PropertyMonthlyEntity, String> {

    /**
     * F004 매물 목록 조회 — 일반 정렬용 (priceDesc/priceAsc/areaDesc/areaAsc).
     *
     * 정렬은 Pageable.Sort 에 위임. DELETED 는 쿼리 조건에서 강제 제외됨 (섹션 6.4).
     *
     * @param status        NULL 허용. NULL 이면 DELETED 제외한 전체.
     * @param dataSource    NULL 허용.
     * @param districtName  NULL 허용. 정확 일치.
     * @param keyword       NULL 허용. aptNm 또는 districtName 부분 일치.
     */
    @Query("SELECT p FROM PropertyMonthlyEntity p " +
           "WHERE p.status <> com.wherehouse.PropertyManagement.entity.PropertyStatus.DELETED " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:dataSource IS NULL OR p.dataSource = :dataSource) " +
           "AND (:districtName IS NULL OR p.districtName = :districtName) " +
           "AND (:keyword IS NULL " +
           "     OR p.aptNm LIKE %:keyword% " +
           "     OR p.districtName LIKE %:keyword%)")
    Page<PropertyMonthlyEntity> findByFilters(
            @Param("status") PropertyStatus status,
            @Param("dataSource") DataSource dataSource,
            @Param("districtName") String districtName,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /**
     * F004 매물 목록 조회 — latest 정렬 전용.
     *
     * ORDER BY COALESCE(modifiedAt, registeredAt, lastUpdated) DESC (섹션 7.5.1, 9.4.1).
     * 호출 시 Pageable 의 Sort 는 반드시 Sort.unsorted() 로 전달.
     */
    @Query("SELECT p FROM PropertyMonthlyEntity p " +
           "WHERE p.status <> com.wherehouse.PropertyManagement.entity.PropertyStatus.DELETED " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:dataSource IS NULL OR p.dataSource = :dataSource) " +
           "AND (:districtName IS NULL OR p.districtName = :districtName) " +
           "AND (:keyword IS NULL " +
           "     OR p.aptNm LIKE %:keyword% " +
           "     OR p.districtName LIKE %:keyword%) " +
           "ORDER BY COALESCE(p.modifiedAt, p.registeredAt, p.lastUpdated) DESC")
    Page<PropertyMonthlyEntity> findByFiltersOrderByLatest(
            @Param("status") PropertyStatus status,
            @Param("dataSource") DataSource dataSource,
            @Param("districtName") String districtName,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}

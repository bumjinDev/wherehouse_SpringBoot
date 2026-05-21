package com.wherehouse.VisitReservation.repository;

import com.wherehouse.VisitReservation.entity.LeaseType;
import com.wherehouse.VisitReservation.entity.VisitWindowEntity;
import com.wherehouse.VisitReservation.entity.VisitWindowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 방문 윈도우 저장소 (설계 명세서 섹션 4.1.6 IX_VISIT_WINDOW_PROPERTY).
 *
 * 핵심 조회 방향:
 *   - 한 매물의 활성 윈도우 목록  (F001 시간 겹침 검증, F003 슬롯 조회 1단계)
 *   - 한 매물의 모든 활성 윈도우 (매물 비활성화 연동 일괄 철회)
 */
@Repository
public interface VisitWindowRepository extends JpaRepository<VisitWindowEntity, Long> {

    /**
     * 한 매물의 특정 상태 윈도우 조회.
     *
     * IX_VISIT_WINDOW_PROPERTY (PROPERTY_ID, LEASE_TYPE, STATUS) 인덱스 일치.
     *
     * 용도:
     *   - F001 윈도우 공개 시 같은 매물의 활성 윈도우와 시간 겹침 검증 (status=ACTIVE)
     *   - F003 슬롯 조회 1단계 — 활성 윈도우 식별 (status=ACTIVE)
     *   - 매물 비활성화 연동 시 일괄 철회 대상 식별 (status=ACTIVE)
     */
    List<VisitWindowEntity> findByPropertyIdAndLeaseTypeAndStatus(
            String propertyId, LeaseType leaseType, VisitWindowStatus status);

    /**
     * 같은 매물·임대 유형의 활성 윈도우 중 요청 시간 범위와 겹치는 것이 있는지 확인.
     *
     * 두 시간 구간이 겹치는 조건: A.startTime &lt; B.endTime AND A.endTime &gt; B.startTime
     *
     * F001 윈도우 공개 4단계 (섹션 6.1) 의 겹침 검증에 사용된다.
     */
    @Query("SELECT COUNT(w) FROM VisitWindowEntity w " +
            "WHERE w.propertyId = :propertyId " +
            "AND w.leaseType = :leaseType " +
            "AND w.status = com.wherehouse.VisitReservation.entity.VisitWindowStatus.ACTIVE " +
            "AND w.startTime < :requestEnd " +
            "AND w.endTime > :requestStart")
    long countOverlappingActiveWindows(
            @Param("propertyId") String propertyId,
            @Param("leaseType") LeaseType leaseType,
            @Param("requestStart") LocalDateTime requestStart,
            @Param("requestEnd") LocalDateTime requestEnd);

    /**
     * 한 매물 식별자에 묶인 모든 활성 윈도우 조회 (전세·월세 통합).
     *
     * 매물 비활성화 연동에서 임대 유형 양쪽의 활성 윈도우를 한 번에 거두기 위해 제공.
     * leaseType 을 지정하지 않으므로 인덱스의 일부 컬럼만 사용한다.
     */
    @Query("SELECT w FROM VisitWindowEntity w " +
            "WHERE w.propertyId = :propertyId " +
            "AND w.status = com.wherehouse.VisitReservation.entity.VisitWindowStatus.ACTIVE")
    List<VisitWindowEntity> findAllActiveByPropertyId(@Param("propertyId") String propertyId);
}

package com.wherehouse.VisitReservation.repository;

import com.wherehouse.VisitReservation.entity.VisitReservationEntity;
import com.wherehouse.VisitReservation.entity.VisitReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 방문 예약 저장소 (설계 명세서 섹션 4.1.6).
 *
 * 핵심 조회 방향:
 *   - 단건 PK 조회 (취소·결과 분류 처리의 진입점)
 *   - 한 탐색자의 예약 목록 (F004 검증, F008 현황 조회) — IX_VISIT_RESERVATION_SEARCHER
 *   - 한 슬롯의 예약 목록 (F002 무효화, F007 종료 전이) — IX_VISIT_RESERVATION_SLOT
 *   - 한 슬롯의 확정 예약 단건 (슬롯 ↔ 예약 매핑)
 */
@Repository
public interface VisitReservationRepository extends JpaRepository<VisitReservationEntity, Long> {

    /**
     * 한 탐색자의 특정 상태 예약 목록.
     *
     * F004 슬롯 예약 4·5단계 — 요청자의 활성 예약 후보를 좁히는 진입점.
     * F008 탐색자 예약 현황 조회.
     */
    List<VisitReservationEntity> findBySearcherUserIdAndStatus(
            String searcherUserId, VisitReservationStatus status);

    /**
     * 한 탐색자의 모든 예약 목록 (최신 확정 순).
     *
     * F008 탐색자 예약 현황 조회 — 활성·종료 예약 모두 포함하여 최신순.
     */
    List<VisitReservationEntity> findBySearcherUserIdOrderByConfirmedAtDesc(String searcherUserId);

    /**
     * 한 슬롯의 확정 예약 단건.
     *
     * UQ_VISIT_RESERVATION_CONFIRMED_SLOT 부분 유일 제약에 의해 확정 예약은 슬롯당
     * 최대 1 건이므로 Optional 로 반환.
     *
     * F004 알림 발송 시 등록자 식별자 조회를 위한 매물 조인 진입점,
     * F005 예약 취소 시 본인 확인 등에서 사용.
     */
    Optional<VisitReservationEntity> findBySlotIdAndStatus(
            Long slotId, VisitReservationStatus status);

    /**
     * 여러 슬롯에 묶인 확정 예약 일괄 조회.
     *
     * F002 윈도우 철회 처리에서 윈도우 소속 슬롯들의 확정 예약을 한 번에 거두기 위해 사용.
     */
    @Query("SELECT r FROM VisitReservationEntity r " +
            "WHERE r.slotId IN :slotIds " +
            "AND r.status = :status")
    List<VisitReservationEntity> findBySlotIdInAndStatus(
            @Param("slotIds") Collection<Long> slotIds,
            @Param("status") VisitReservationStatus status);

    /**
     * 한 슬롯에 묶인 모든 예약 (상태 무관, 최신 확정 순).
     *
     * F008 등록자 슬롯 현황 조회에서 슬롯에 묶인 예약을 표시할 때 사용 가능.
     */
    List<VisitReservationEntity> findBySlotIdOrderByConfirmedAtDesc(Long slotId);
}

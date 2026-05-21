package com.wherehouse.VisitReservation.repository;

import com.wherehouse.VisitReservation.entity.ReopenSubscriptionEntity;
import com.wherehouse.VisitReservation.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 재개방 알림 구독 저장소 (설계 명세서 섹션 4.1.6).
 *
 * 핵심 조회 방향:
 *   - 한 슬롯의 활성 구독자 (F005 예약 취소 시 재개방 알림 발송) — IX_REOPEN_SUBSCRIPTION_SLOT
 *   - 한 탐색자의 구독 목록 (F008 탐색자 구독 현황) — IX_REOPEN_SUBSCRIPTION_SEARCHER
 *   - (탐색자, 슬롯) 활성 구독 단건 (구독 신청 중복 검증·해제 진입점)
 *   - 여러 슬롯의 활성 구독 (F007 슬롯 종료 시 EXPIRED 폐기)
 */
@Repository
public interface ReopenSubscriptionRepository extends JpaRepository<ReopenSubscriptionEntity, Long> {

    /**
     * 한 슬롯의 활성 구독자 목록.
     *
     * F005 예약 취소 시 슬롯 재개방 알림 발송 대상 식별.
     */
    List<ReopenSubscriptionEntity> findBySlotIdAndStatus(
            Long slotId, SubscriptionStatus status);

    /**
     * (탐색자, 슬롯) 활성 구독 단건. UQ_REOPEN_SUBSCRIPTION_ACTIVE 에 의해 최대 1 건.
     *
     * F006 구독 신청 시 중복 검증, 구독 해제 시 진입점.
     */
    Optional<ReopenSubscriptionEntity> findBySlotIdAndSearcherUserIdAndStatus(
            Long slotId, String searcherUserId, SubscriptionStatus status);

    /**
     * 한 탐색자의 구독 목록 (모든 상태, 최신 구독 순).
     *
     * F008 탐색자 구독 현황 조회.
     */
    List<ReopenSubscriptionEntity> findBySearcherUserIdOrderBySubscribedAtDesc(String searcherUserId);

    /**
     * 여러 슬롯의 활성 구독 일괄 조회.
     *
     * F002 윈도우 철회·F007 슬롯 종료 시 소속 슬롯의 활성 구독을 한 번에 거두어 EXPIRED 로 폐기.
     */
    List<ReopenSubscriptionEntity> findBySlotIdInAndStatus(
            Collection<Long> slotIds, SubscriptionStatus status);
}

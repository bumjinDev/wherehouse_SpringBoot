package com.wherehouse.VisitReservation.scheduler;

import com.wherehouse.VisitReservation.entity.*;
import com.wherehouse.VisitReservation.repository.ReopenSubscriptionRepository;
import com.wherehouse.VisitReservation.repository.VisitReservationRepository;
import com.wherehouse.VisitReservation.repository.VisitSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 슬롯 종료 처리 컴포넌트 (설계 명세서 섹션 6.7 시스템 주도 부분).
 *
 * 1 분 주기로 종료 시각이 경과한 슬롯을 찾아 다음을 수행한다.
 *  1) 슬롯 STATUS → CLOSED (T-S2 또는 T-S5)
 *  2) 슬롯에 묶인 CONFIRMED 예약이 있으면 STATUS → COMPLETED (T-R3),
 *     VISIT_RESULT 는 NULL 유지 (등록자 분류 대기)
 *  3) 슬롯의 활성 구독이 있으면 STATUS → EXPIRED (SLOT_CLOSED 사유로 폐기)
 *
 * 본 컴포넌트는 알림을 발송하지 않는다. 종료는 시간 경과로 인한 자연스러운 전이이며,
 * 영향을 받는 예약자·구독자는 본인 자원 조회 (F008) 로 변경된 상태를 확인한다.
 *
 * 처리 단위는 한 트랜잭션이며, 처리 중 일부 슬롯이 실패해도 다음 슬롯은 동일 트랜잭션에서
 * 함께 롤백된다. 운영 중 일부 슬롯에서 예외가 발생하면 본 사이클 전체가 롤백되고 다음
 * 사이클에서 동일 슬롯을 다시 시도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotExpirationScheduler {

    private final VisitSlotRepository slotRepository;
    private final VisitReservationRepository reservationRepository;
    private final ReopenSubscriptionRepository subscriptionRepository;

    /**
     * fixedDelay = 60000: 이전 실행 완료 후 60 초 뒤 다음 실행.
     * 실행 중 예외가 발생해도 스케줄러 자체는 중단되지 않는다 (Spring 기본 동작).
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void closeExpiredSlots() {
        LocalDateTime now = LocalDateTime.now();
        List<VisitSlotEntity> expired = slotRepository.findExpiredActiveSlots(
                List.of(VisitSlotStatus.AVAILABLE, VisitSlotStatus.RESERVED), now);

        if (expired.isEmpty()) return;

        log.info("[SLOT_EXPIRATION_SCHEDULER] expiredSlots={}", expired.size());

        for (VisitSlotEntity slot : expired) {
            try {
                processOneSlot(slot, now);
            } catch (Exception e) {
                log.error("[SLOT_EXPIRATION_FAILED] slotId={}, error={}",
                        slot.getSlotId(), e.getMessage(), e);
                throw e; // 본 사이클 전체 롤백 — 다음 사이클에서 재시도
            }
        }
    }

    private void processOneSlot(VisitSlotEntity slot, LocalDateTime now) {
        // RESERVED 였다면 연결 예약을 COMPLETED 로 전이
        if (slot.getStatus() == VisitSlotStatus.RESERVED) {
            Optional<VisitReservationEntity> confirmed = reservationRepository
                    .findBySlotIdAndStatus(slot.getSlotId(), VisitReservationStatus.CONFIRMED);
            confirmed.ifPresent(r -> {
                r.setStatus(VisitReservationStatus.COMPLETED);
                // VISIT_RESULT 는 NULL 유지 — 등록자가 F007 로 분류한다.
                reservationRepository.save(r);
            });
        }

        // 슬롯 → CLOSED
        slot.setStatus(VisitSlotStatus.CLOSED);
        slotRepository.save(slot);

        // 활성 구독 폐기 — SLOT_CLOSED 사유
        List<ReopenSubscriptionEntity> activeSubs = subscriptionRepository
                .findBySlotIdInAndStatus(Collections.singletonList(slot.getSlotId()),
                        SubscriptionStatus.ACTIVE);
        for (ReopenSubscriptionEntity sub : activeSubs) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setTerminatedAt(now);
            sub.setTerminationReason(SubscriptionTerminationReason.SLOT_CLOSED);
            subscriptionRepository.save(sub);
        }

        if (!activeSubs.isEmpty()) {
            log.info("[SLOT_CLOSED] slotId={}, expiredSubscriptions={}",
                    slot.getSlotId(), activeSubs.size());
        }
    }
}

/* ============================================================================
 * F004 시나리오 1 — ④ 낙관적 락 (C, @Version)
 *
 * 슬롯 읽기  : findById (락 없음 — 5건 모두 같은 version 으로 읽음)
 * 슬롯 쓰기  : slot.setStatus(RESERVED) + save  (무조건; 차단은 @Version 이 만든다)
 * 동반 변경  : VisitSlotEntity 에 @Version 필드 + DB 컬럼 (아래)
 *
 * 로깅(★ baseline 과 동일): logSlotSnapshot/logReservationsOnSlot 를 CP1/CP2/CP3 에 둔다.
 *   - 메서드 본문 안에서는 아무도 차단되지 않는다 → CP1 5 / CP2 5 / CP3 5 (후착도 헛 INSERT 까지 도달).
 *   - 차단은 "커밋 시 version 충돌": flush 에서 UPDATE … WHERE version=? 가 0행 →
 *     OptimisticLockException → 후착 4건 롤백(메서드 밖, 트랜잭션 커밋 단계).
 *
 * ※ 그래서 C 의 정합성은 CP 분포가 아니라 ★DB 잔여 CONFIRMED=1 (+ TX 롤백 4)★ 로 판정한다.
 *   report.py 의 "등록 통과/CONFIRMED 수"는 CP3 도달 기준이라 5 로 나오므로, C 회차는 DB 조회로 정합성 확정.
 *   커밋 단계 OptimisticLockException 은 메서드 본문 밖이라 메서드 안에 로그를 둘 수 없다
 *   → Spring TX 롤백 로그(JpaTransactionManager) 가 차단 신호.
 *
 * ※ logSlotSnapshot/logReservationsOnSlot 헬퍼는 클래스에 이미 존재(공통).
 * ※ mock·단위테스트 아님. JMeter N=5 동일슬롯, 실 DB.
 * ※ 참조용(docs). VisitReservationWriteService.createReservation 을 이 메서드로 교체.
 * ========================================================================== */

@Transactional
public ReservationCreateResponseDto createReservation(ReservationCreateRequestDto dto, String userId) {
    Long slotId = dto.getSlotId();
    LocalDateTime now = LocalDateTime.now();

    // 1단계: 슬롯 read (무락 — 5건 모두 같은 version 으로 읽음) + 윈도우
    VisitSlotEntity slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new VisitSlotNotFoundException("슬롯을 찾을 수 없습니다. slotId=" + slotId));
    VisitWindowEntity window = windowRepository.findById(slot.getWindowId())
            .orElseThrow(() -> new VisitWindowNotFoundException("슬롯의 윈도우를 찾을 수 없습니다. windowId=" + slot.getWindowId()));

    // === CP1: 진입(읽기) 직후 — 모든 스레드 도달(전부 같은 version·AVAILABLE 로 읽음). ===
    logSlotSnapshot("CP1-AFTER-AVAILABLE-CHECK", slotId);
    logReservationsOnSlot("CP1-AFTER-AVAILABLE-CHECK", slotId);

    // 2단계: AVAILABLE 확인 (무락이라 5건 모두 AVAILABLE 을 보고 통과 — 차단은 커밋 때 일어난다).
    if (slot.getStatus() != VisitSlotStatus.AVAILABLE) {
        throw SlotUnavailableException.alreadyReserved(window.getLeaseType(),
                findAvailableSlotItems(window.getPropertyId(), window.getLeaseType()));
    }
    if (!slot.getStartTime().isAfter(now)) {
        throw SlotUnavailableException.startTimeExpired(window.getLeaseType(),
                findAvailableSlotItems(window.getPropertyId(), window.getLeaseType()));
    }
    PropertyOwner owner = lookupPropertyOwner(window.getPropertyId(), window.getLeaseType());
    if (userId.equals(owner.registeredUserId)) {
        throw new SelfPropertyReservationException("자신이 등록한 매물의 슬롯은 예약할 수 없습니다.");
    }
    if (reservationRepository.countDuplicatePropertyReservation(userId, window.getPropertyId(), window.getLeaseType()) > 0) {
        throw new DuplicateReservationException("같은 매물에 대해 이미 다른 시간대 본인의 예약이 이미 있습니다.");
    }
    if (reservationRepository.countTimeOverlappingReservation(userId, slot.getStartTime(), slot.getEndTime()) > 0) {
        throw new ReservationTimeOverlapException("현재 시도하신 예약 시간 대에 대한 동일한 시간 대의 다른 매물에 대한 예약이 이미 존재 합니다.");
    }

    // 6단계: 슬롯 점유 — 코드는 무조건 UPDATE. 단 @Version 때문에 flush 시
    //         UPDATE … SET status=RESERVED, version=version+1 WHERE slotId=? AND version=? 로 나가고,
    //         후착은 version 불일치(0행) → OptimisticLockException → 롤백(메서드 밖, 커밋 단계).
    slot.setStatus(VisitSlotStatus.RESERVED);
    slotRepository.save(slot);

    // === CP2: 점유 직후 — (메서드 안에선) 모든 스레드 도달. 슬롯 스냅샷. ===
    logSlotSnapshot("CP2-AFTER-SLOT-UPDATE", slot.getSlotId());

    // 예약 INSERT — 후착도 여기까지 도달(헛 INSERT, 커밋 때 함께 롤백).
    VisitReservationEntity reservation = VisitReservationEntity.builder()
            .slotId(slot.getSlotId()).searcherUserId(userId)
            .status(VisitReservationStatus.CONFIRMED).confirmedAt(now).build();
    VisitReservationEntity savedReservation = reservationRepository.save(reservation);

    // === CP3: 예약 INSERT 직후 — (메서드 안에선) 모든 스레드 도달. 예약 목록. ===
    logReservationsOnSlot("CP3-AFTER-RESERVATION-INSERT", slot.getSlotId());

    subscriptionRepository.findBySlotIdAndSearcherUserIdAndStatus(slot.getSlotId(), userId, SubscriptionStatus.ACTIVE)
            .ifPresent(sub -> {
                sub.setStatus(SubscriptionStatus.FULFILLED);
                sub.setTerminatedAt(now);
                sub.setTerminationReason(SubscriptionTerminationReason.RESERVED);
                subscriptionRepository.save(sub);
            });

    notificationService.notify(owner.registeredUserId, NotificationType.SLOT_RESERVED,
            slot.getSlotId(), savedReservation.getReservationId(), window.getPropertyId(),
            "회원님의 슬롯이 예약되었습니다.");

    log.info("[VISIT_RESERVATION_CONFIRMED] reservationId={}, slotId={}, searcher={}, registrant={}",
            savedReservation.getReservationId(), slot.getSlotId(), userId, owner.registeredUserId);

    return ReservationCreateResponseDto.builder()
            .reservationId(savedReservation.getReservationId()).slotId(slot.getSlotId())
            .propertyId(window.getPropertyId()).leaseType(window.getLeaseType().name())
            .startTime(slot.getStartTime()).endTime(slot.getEndTime())
            .status(savedReservation.getStatus().name()).confirmedAt(savedReservation.getConfirmedAt())
            .registrant(buildContact(owner.member)).build();
}

/* ── 동반 변경 1: VisitSlotEntity (import jakarta.persistence.Version;) ──
@Version
private Long version;
 *
 * ── 동반 변경 2: DB (SQL Developer) ──
 * ALTER TABLE VISIT_SLOT ADD VERSION NUMBER DEFAULT 0 NOT NULL;
 *
 * ── 측정 종료 후 복원 시 ──
 * 엔티티 version 필드 제거 + ALTER TABLE VISIT_SLOT DROP COLUMN VERSION;
─────────────────────────────────────────────────────────────────────────────── */

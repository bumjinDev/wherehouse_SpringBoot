/* ============================================================================
 * F004 시나리오 1 — ③ 비관적 락 (B, SELECT … FOR UPDATE)
 *
 * 슬롯 읽기  : findByIdForUpdate (읽기에서 행 X-락; 후착은 락 대기 후 RESERVED 를 읽음)
 * 슬롯 쓰기  : slot.setStatus(RESERVED) + save  (무조건; 락은 1단계에서 보유)
 * 동반 변경  : VisitSlotRepository.findByIdForUpdate 추가 (아래)
 *
 * 로깅(★ baseline 과 동일): logSlotSnapshot/logReservationsOnSlot 를 CP1/CP2/CP3 에 둔다.
 *   - CP1 은 락 획득·읽기 "직후"(AVAILABLE 검증 전)라 후착도 도달한다.
 *     → 후착의 CP1 슬롯상태=RESERVED 로 직렬화가 그대로 드러난다(이게 B 의 핵심 관측).
 *       (D 패자의 CP1=AVAILABLE 과 대비된다.)
 *   - 후착은 CP1 직후 2단계 AVAILABLE 검증에서 RESERVED 를 보고 거부 → CP2 미도달.
 * 예상(단계): CP1 5(후착 슬롯상태=RESERVED) / CP2 1 / CP3 1.
 *
 * ※ logSlotSnapshot/logReservationsOnSlot 헬퍼는 클래스에 이미 존재(공통).
 * ※ mock·단위테스트 아님. JMeter N=5 동일슬롯, 실 DB.
 * ※ 참조용(docs). VisitReservationWriteService.createReservation 을 이 메서드로 교체.
 * ========================================================================== */

@Transactional
public ReservationCreateResponseDto createReservation(ReservationCreateRequestDto dto, String userId) {
    Long slotId = dto.getSlotId();
    LocalDateTime now = LocalDateTime.now();

    // 1단계: 슬롯 read — FOR UPDATE 로 행 X-락. 선착만 즉시 통과, 후착 4건은 락 대기 후 RESERVED 를 읽는다.
    VisitSlotEntity slot = slotRepository.findByIdForUpdate(slotId)
            .orElseThrow(() -> new VisitSlotNotFoundException("슬롯을 찾을 수 없습니다. slotId=" + slotId));
    VisitWindowEntity window = windowRepository.findById(slot.getWindowId())
            .orElseThrow(() -> new VisitWindowNotFoundException("슬롯의 윈도우를 찾을 수 없습니다. windowId=" + slot.getWindowId()));

    // === CP1: 락 획득·읽기 직후 — 모든 스레드 도달. 후착의 슬롯상태=RESERVED 로 직렬화가 드러난다. ===
    logSlotSnapshot("CP1-AFTER-AVAILABLE-CHECK", slotId);
    logReservationsOnSlot("CP1-AFTER-AVAILABLE-CHECK", slotId);

    // 2단계: AVAILABLE 확인 — 후착은 RESERVED 를 보고 여기서 거부(CP2 미도달).
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

    // 6단계: 슬롯 점유 — 무조건 UPDATE (행 X-락은 1단계 SELECT FOR UPDATE 에서 이미 보유).
    slot.setStatus(VisitSlotStatus.RESERVED);
    slotRepository.save(slot);

    // === CP2: 점유 직후 — 승자만 도달. 슬롯 스냅샷. ===
    logSlotSnapshot("CP2-AFTER-SLOT-UPDATE", slot.getSlotId());

    // 예약 INSERT (승자만 도달)
    VisitReservationEntity reservation = VisitReservationEntity.builder()
            .slotId(slot.getSlotId()).searcherUserId(userId)
            .status(VisitReservationStatus.CONFIRMED).confirmedAt(now).build();
    VisitReservationEntity savedReservation = reservationRepository.save(reservation);

    // === CP3: 예약 INSERT 직후 — 승자만 도달. 예약 목록. ===
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

/* ── 동반 변경: VisitSlotRepository ──
 *   import jakarta.persistence.LockModeType;
 *   import org.springframework.data.jpa.repository.Lock;
 *
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM VisitSlotEntity s WHERE s.slotId = :slotId")
Optional<VisitSlotEntity> findByIdForUpdate(@Param("slotId") Long slotId);
─────────────────────────────────────────────────────────────────────────────── */

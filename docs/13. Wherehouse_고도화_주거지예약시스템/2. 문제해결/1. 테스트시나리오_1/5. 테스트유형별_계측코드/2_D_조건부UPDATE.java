/* ============================================================================
 * F004 시나리오 1 — ② 조건부 UPDATE (D, 앱-낙관 / 행-비관)
 *
 * 슬롯 읽기  : findById (락 없음)
 * 슬롯 쓰기  : UPDATE … SET RESERVED WHERE slotId=? AND status=AVAILABLE  (affected 0/1)
 * 동반 변경  : VisitSlotRepository.reserveSlotIfAvailable 주석 해제 (아래)
 * 사전검증   : AVAILABLE 사전 검증 없음(조건부 UPDATE 가 게이트). 패자는 6단계 affected=0 으로 차단.
 *
 * 로깅(★ baseline 과 동일): logSlotSnapshot/logReservationsOnSlot 를 CP1/CP2/CP3 에 둔다.
 *   - CP1-AFTER-AVAILABLE-CHECK : 읽기 직후. 모든 스레드 도달(슬롯 스냅샷 + 예약 목록).
 *   - CP2-AFTER-SLOT-UPDATE     : 점유 직후. 승자만(슬롯 스냅샷).
 *   - CP3-AFTER-RESERVATION-INSERT : 예약 INSERT 직후. 승자만(예약 목록).
 *   → report.py 가 그대로 4시트(요청별 15컬럼 · 원본로그 26컬럼 · 예상대조)를 산출.
 * 예상(단계): CP1 5 / CP2 1 / CP3 1 → CP1→CP2 끊김 = "쓰기(UPDATE)에서 차단". 패자 CP1 슬롯상태=AVAILABLE.
 *
 * ※ logSlotSnapshot/logReservationsOnSlot 헬퍼는 클래스에 이미 존재(공통, 한 번만 둠).
 * ※ mock·단위테스트 아님. JMeter N=5 동일슬롯, 실 DB.
 * ※ 참조용(docs). VisitReservationWriteService.createReservation 을 이 메서드로 교체.
 * ========================================================================== */

@Transactional
public ReservationCreateResponseDto createReservation(ReservationCreateRequestDto dto, String userId) {
    Long slotId = dto.getSlotId();
    LocalDateTime now = LocalDateTime.now();

    // 1단계: 슬롯 read (무락) + 윈도우
    VisitSlotEntity slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new VisitSlotNotFoundException("슬롯을 찾을 수 없습니다. slotId=" + slotId));
    VisitWindowEntity window = windowRepository.findById(slot.getWindowId())
            .orElseThrow(() -> new VisitWindowNotFoundException("슬롯의 윈도우를 찾을 수 없습니다. windowId=" + slot.getWindowId()));

    // === CP1: 진입(읽기) 직후 — 모든 스레드 도달. 슬롯 스냅샷 + 예약 목록 기록. ===
    logSlotSnapshot("CP1-AFTER-AVAILABLE-CHECK", slotId);
    logReservationsOnSlot("CP1-AFTER-AVAILABLE-CHECK", slotId);

    // 2단계: (조건부 UPDATE 회차) 사전 AVAILABLE 검증 없음 — AVAILABLE 게이트는 6단계 조건부 UPDATE 가 담당.
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

    // 6단계: 슬롯 점유 — 조건부 UPDATE(WHERE status=AVAILABLE). 패자는 affected=0 → 여기서 차단(CP2 미도달).
    int affected = slotRepository.reserveSlotIfAvailable(slotId);
    if (affected == 0) {
        throw SlotUnavailableException.alreadyReserved(window.getLeaseType(),
                findAvailableSlotItems(window.getPropertyId(), window.getLeaseType()));
    }

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

/* ── 동반 변경: VisitSlotRepository (import org.springframework.data.jpa.repository.Modifying;) ──
@Modifying
@Query("UPDATE VisitSlotEntity s " +
       "SET s.status = com.wherehouse.VisitReservation.entity.VisitSlotStatus.RESERVED " +
       "WHERE s.slotId = :slotId " +
       "AND s.status = com.wherehouse.VisitReservation.entity.VisitSlotStatus.AVAILABLE")
int reserveSlotIfAvailable(@Param("slotId") Long slotId);
─────────────────────────────────────────────────────────────────────────────── */

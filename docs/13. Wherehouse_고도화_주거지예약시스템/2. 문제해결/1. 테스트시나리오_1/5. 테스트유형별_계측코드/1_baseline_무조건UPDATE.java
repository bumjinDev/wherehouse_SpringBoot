/* ============================================================================
 * F004 시나리오 1 — ① baseline (무조건 UPDATE, 동시성 제어 없음)
 *
 * 슬롯 쓰기  : slot.setStatus(RESERVED) + save  (조건/락 없음)
 * 슬롯 읽기  : findById (락 없음)
 * 동반 변경  : 없음
 * 예상 로그  : CP0=5  CP1=5  CP2=5  CP3=5  REJECT=0  / Spring TX = 5 commit
 *            → 전부 통과·커밋 → 슬롯당 CONFIRMED 5 (정합성 붕괴)
 *
 * ※ mock·단위테스트 아님. JMeter N=5 동일슬롯으로 실 DB에 돌리는 실제 코드.
 * ※ 참조용(docs). VisitReservationWriteService.createReservation 을 이 메서드로 교체.
 *   cp() 헬퍼는 클래스에 한 번만 추가(4개 파일 공통).
 * ========================================================================== */

/** F004 측정용 경량 체크포인트 — 추가 DB 쿼리 없이 손에 있는 값만 찍는다. (4개 파일 공통) */
private void cp(String stage, String detail) {
    log.info("[F004-{}] thread={} nano={} {}",
             stage, Thread.currentThread().getName(), System.nanoTime(), detail);
}

@Transactional
public ReservationCreateResponseDto createReservation(ReservationCreateRequestDto dto, String userId) {
    Long slotId = dto.getSlotId();
    LocalDateTime now = LocalDateTime.now();
    cp("CP0-ENTER", "slot=" + slotId + " user=" + userId);

    // 1단계: 슬롯 read (무락) + 윈도우
    VisitSlotEntity slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new VisitSlotNotFoundException("슬롯을 찾을 수 없습니다. slotId=" + slotId));
    VisitWindowEntity window = windowRepository.findById(slot.getWindowId())
            .orElseThrow(() -> new VisitWindowNotFoundException("슬롯의 윈도우를 찾을 수 없습니다. windowId=" + slot.getWindowId()));

    // 2단계: AVAILABLE 확인
    if (slot.getStatus() != VisitSlotStatus.AVAILABLE) {
        cp("REJECT", "reason=ALREADY_RESERVED at=READ");
        throw SlotUnavailableException.alreadyReserved(window.getLeaseType(),
                findAvailableSlotItems(window.getPropertyId(), window.getLeaseType()));
    }
    cp("CP1-READ", "status=AVAILABLE");

    if (!slot.getStartTime().isAfter(now)) {
        cp("REJECT", "reason=START_EXPIRED at=READ");
        throw SlotUnavailableException.startTimeExpired(window.getLeaseType(),
                findAvailableSlotItems(window.getPropertyId(), window.getLeaseType()));
    }

    // 3단계: 자기 매물 제한
    PropertyOwner owner = lookupPropertyOwner(window.getPropertyId(), window.getLeaseType());
    if (userId.equals(owner.registeredUserId)) {
        cp("REJECT", "reason=SELF_PROPERTY at=READ");
        throw new SelfPropertyReservationException("자신이 등록한 매물의 슬롯은 예약할 수 없습니다.");
    }

    // 4·5단계: 중복 / 시간 겹침 (깨끗한 측정이면 0건이어야 정상)
    if (reservationRepository.countDuplicatePropertyReservation(userId, window.getPropertyId(), window.getLeaseType()) > 0) {
        cp("REJECT", "reason=DUPLICATE at=VALIDATE");
        throw new DuplicateReservationException("같은 매물에 대해 이미 다른 시간대 본인의 예약이 이미 있습니다.");
    }
    if (reservationRepository.countTimeOverlappingReservation(userId, slot.getStartTime(), slot.getEndTime()) > 0) {
        cp("REJECT", "reason=TIME_OVERLAP at=VALIDATE");
        throw new ReservationTimeOverlapException("현재 시도하신 예약 시간 대에 대한 동일한 시간 대의 다른 매물에 대한 예약이 이미 존재 합니다.");
    }

    // 6단계: 슬롯 상태 전이 — 무조건 UPDATE (baseline)
    slot.setStatus(VisitSlotStatus.RESERVED);
    slotRepository.save(slot);
    cp("CP2-WRITE", "RESERVED (무조건)");

    // 예약 INSERT
    VisitReservationEntity reservation = VisitReservationEntity.builder()
            .slotId(slot.getSlotId()).searcherUserId(userId)
            .status(VisitReservationStatus.CONFIRMED).confirmedAt(now).build();
    VisitReservationEntity savedReservation = reservationRepository.save(reservation);
    cp("CP3-INSERT", "resId=" + savedReservation.getReservationId());

    // 구독 마감
    subscriptionRepository.findBySlotIdAndSearcherUserIdAndStatus(slot.getSlotId(), userId, SubscriptionStatus.ACTIVE)
            .ifPresent(sub -> {
                sub.setStatus(SubscriptionStatus.FULFILLED);
                sub.setTerminatedAt(now);
                sub.setTerminationReason(SubscriptionTerminationReason.RESERVED);
                subscriptionRepository.save(sub);
            });

    // 7단계: 알림
    notificationService.notify(owner.registeredUserId, NotificationType.SLOT_RESERVED,
            slot.getSlotId(), savedReservation.getReservationId(), window.getPropertyId(),
            "회원님의 슬롯이 예약되었습니다.");

    log.info("[VISIT_RESERVATION_CONFIRMED] reservationId={}, slotId={}, searcher={}, registrant={}",
            savedReservation.getReservationId(), slot.getSlotId(), userId, owner.registeredUserId);

    // 8단계: 응답
    return ReservationCreateResponseDto.builder()
            .reservationId(savedReservation.getReservationId()).slotId(slot.getSlotId())
            .propertyId(window.getPropertyId()).leaseType(window.getLeaseType().name())
            .startTime(slot.getStartTime()).endTime(slot.getEndTime())
            .status(savedReservation.getStatus().name()).confirmedAt(savedReservation.getConfirmedAt())
            .registrant(buildContact(owner.member)).build();
}

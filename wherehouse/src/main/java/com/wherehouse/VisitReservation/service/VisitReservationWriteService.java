package com.wherehouse.VisitReservation.service;

import com.wherehouse.PropertyManagement.entity.PropertyCharterEntity;
import com.wherehouse.PropertyManagement.entity.PropertyMonthlyEntity;
import com.wherehouse.PropertyManagement.entity.PropertyStatus;
import com.wherehouse.PropertyManagement.repository.PropertyCharterRegistrationRepository;
import com.wherehouse.PropertyManagement.repository.PropertyMonthlyRegistrationRepository;
import com.wherehouse.VisitReservation.dto.*;
import com.wherehouse.VisitReservation.entity.*;
import com.wherehouse.VisitReservation.exception.customExceptions.*;
import com.wherehouse.VisitReservation.repository.ReopenSubscriptionRepository;
import com.wherehouse.VisitReservation.repository.VisitReservationRepository;
import com.wherehouse.VisitReservation.repository.VisitSlotRepository;
import com.wherehouse.VisitReservation.repository.VisitWindowRepository;
import com.wherehouse.members.dao.MemberEntityRepository;
import com.wherehouse.members.model.MembersEntity;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 방문 예약 쓰기 서비스 (설계 명세서 섹션 6.1 ~ 6.7).
 *
 * 윈도우 공개 (F001) / 철회 (F002) / 슬롯 예약 (F004) / 예약 취소 (F005) / 재개방 알림
 * 구독 신청·해제 (F006) / 방문 결과 분류 (F007) / 매물 비활성화 연동 일괄 철회를 처리한다.
 *
 * ────────────────────────────────────────────────────────────
 * 동시성 제어 위임 (섹션 8.4)
 * ────────────────────────────────────────────────────────────
 * 본 1차 구현은 처리 흐름을 단일 트랜잭션 안의 "읽기 → 검증 → 쓰기" 순서로만 작성한다.
 * 동시 예약 경합을 해결하는 동시성 제어 기법 (비관적 락, 낙관적 락, 분산 락 등) 의 선택과
 * 측정은 설계 명세서가 구현 단계로 명시 위임한 부분으로, 추후 실측 결과에 따라 별도
 * 작업으로 진행된다. 다만 데이터베이스 차원의 무결성 백스톱 (섹션 4.1.3 의 부분 유일
 * 인덱스 UQ_VISIT_RESERVATION_CONFIRMED_SLOT 등) 은 동시성 제어와 직교하여 항상
 * 동작하므로, INSERT 가 본 제약을 위반할 경우 DataIntegrityViolationException 이 발생하고
 * 글로벌 핸들러가 409 로 변환한다.
 *
 * ────────────────────────────────────────────────────────────
 * 알림 트랜잭션 참여
 * ────────────────────────────────────────────────────────────
 * 알림 생성 ({@link VisitNotificationService#notify}) 은 본 서비스의 메서드 트랜잭션에
 * 참여 (REQUIRED) 한다. 즉, 메인 상태 전이와 알림 INSERT 가 한 트랜잭션 안에서 함께
 * 커밋되거나 함께 롤백된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitReservationWriteService {

    private final VisitWindowRepository windowRepository;
    private final VisitSlotRepository slotRepository;
    private final VisitReservationRepository reservationRepository;
    private final ReopenSubscriptionRepository subscriptionRepository;

    private final PropertyCharterRegistrationRepository charterRepository;
    private final PropertyMonthlyRegistrationRepository monthlyRepository;
    private final MemberEntityRepository memberRepository;

    private final VisitNotificationService notificationService;

    // ====================================================================
    // F001 — 방문 윈도우 공개 (섹션 6.1)
    // ====================================================================

    @Transactional
    public WindowCreateResponseDto createWindow(WindowCreateRequestDto dto, String userId) {

        // 1단계: 요청 검증
        LeaseType leaseType = LeaseType.valueOf(dto.getLeaseType());
        int durationMinutes = dto.getSlotDurationMinutes() == null ? 30 : dto.getSlotDurationMinutes();
        LocalDateTime startTime = dto.getStartTime();
        LocalDateTime endTime = dto.getEndTime();
        LocalDateTime now = LocalDateTime.now();

        if (!endTime.isAfter(startTime)) {
            throw new InvalidRequestException("종료 시각은 시작 시각보다 늦어야 합니다.");
        }
        if (!startTime.isAfter(now)) {
            throw new InvalidRequestException("윈도우 시작 시각은 현재 시각 이후여야 합니다.");
        }
        long windowMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        if (windowMinutes <= 0 || windowMinutes % durationMinutes != 0) {
            throw new InvalidRequestException(
                    "윈도우 길이가 슬롯 분할 단위(" + durationMinutes + "분)의 정수배여야 합니다.");
        }

        // 2단계: 매물 유효성 확인
        PropertyOwner owner = lookupPropertyOwner(dto.getPropertyId(), leaseType);

        // 3단계: 등록자 권한 확인
        verifyRegistrant(owner, userId);

        // 4단계: 윈도우 시간 겹침 검증
        long overlapping = windowRepository.countOverlappingActiveWindows(
                dto.getPropertyId(), leaseType, startTime, endTime);
        if (overlapping > 0) {
            throw new WindowTimeOverlapException(
                    "같은 매물·임대 유형의 활성 윈도우와 시간이 겹칩니다.");
        }

        // 5단계: 윈도우 저장 및 슬롯 일괄 생성 (원자적)
        VisitWindowEntity window = VisitWindowEntity.builder()
                .propertyId(dto.getPropertyId())
                .leaseType(leaseType)
                .startTime(startTime)
                .endTime(endTime)
                .slotDurationMinutes(durationMinutes)
                .status(VisitWindowStatus.ACTIVE)
                .createdAt(now)
                .build();
        VisitWindowEntity savedWindow = windowRepository.save(window);

        List<VisitSlotEntity> slots = new ArrayList<>();
        LocalDateTime cursor = startTime;
        while (cursor.isBefore(endTime)) {
            LocalDateTime slotEnd = cursor.plusMinutes(durationMinutes);
            slots.add(VisitSlotEntity.builder()
                    .windowId(savedWindow.getWindowId())
                    .startTime(cursor)
                    .endTime(slotEnd)
                    .status(VisitSlotStatus.AVAILABLE)
                    .createdAt(now)
                    .build());
            cursor = slotEnd;
        }
        List<VisitSlotEntity> savedSlots = slotRepository.saveAll(slots);

        log.info("[VISIT_WINDOW_CREATED] windowId={}, propertyId={}, leaseType={}, slots={}",
                savedWindow.getWindowId(), dto.getPropertyId(), leaseType, savedSlots.size());

        // 6단계: 응답 구성
        List<WindowCreateResponseDto.SlotItem> slotItems = savedSlots.stream()
                .map(s -> WindowCreateResponseDto.SlotItem.builder()
                        .slotId(s.getSlotId())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .status(s.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        return WindowCreateResponseDto.builder()
                .windowId(savedWindow.getWindowId())
                .propertyId(savedWindow.getPropertyId())
                .leaseType(savedWindow.getLeaseType().name())
                .startTime(savedWindow.getStartTime())
                .endTime(savedWindow.getEndTime())
                .slotDurationMinutes(savedWindow.getSlotDurationMinutes())
                .slots(slotItems)
                .createdAt(savedWindow.getCreatedAt())
                .build();
    }

    // ====================================================================
    // F002 — 방문 윈도우 철회 (섹션 6.2)
    // ====================================================================

    @Transactional
    public WindowWithdrawResponseDto withdrawWindow(Long windowId, String userId) {

        // 1단계: 윈도우 존재·활성 확인
        VisitWindowEntity window = windowRepository.findById(windowId)
                .orElseThrow(() -> new VisitWindowNotFoundException(
                        "윈도우를 찾을 수 없습니다. windowId=" + windowId));
        if (window.getStatus() != VisitWindowStatus.ACTIVE) {
            throw new InvalidStateTransitionException(
                    "이미 철회된 윈도우입니다. windowId=" + windowId);
        }

        // 등록자 권한 확인
        PropertyOwner owner = lookupPropertyOwner(window.getPropertyId(), window.getLeaseType());
        verifyRegistrant(owner, userId);

        // 2~4단계: 원자적 상태 전이 + 알림
        return withdrawWindowInternal(window, NotificationType.RESERVATION_INVALIDATED,
                "윈도우 철회로 예약이 무효화되었습니다.");
    }

    // ====================================================================
    // 매물 비활성화 연동 — 활성 윈도우 일괄 철회 (섹션 2.1)
    // ====================================================================

    /**
     * 매물 상태가 비활성화 (COMPLETED/DELETED) 로 전이될 때 호출되는 연동 진입점.
     * 해당 매물의 두 임대 유형 (CHARTER/MONTHLY) 활성 윈도우를 모두 철회하고, 영향받은
     * 탐색자에게 PROPERTY_DEACTIVATED 통지를 발송한다.
     *
     * 권한 확인은 호출 측 (PropertyWriteService) 에서 이미 수행되었음을 전제로 본
     * 메서드는 등록자 검증을 생략한다.
     */
    @Transactional
    public void withdrawAllActiveWindowsForProperty(String propertyId) {
        List<VisitWindowEntity> windows = windowRepository.findAllActiveByPropertyId(propertyId);
        if (windows.isEmpty()) {
            return;
        }
        log.info("[VISIT_PROPERTY_DEACTIVATED] propertyId={}, activeWindows={}",
                propertyId, windows.size());

        for (VisitWindowEntity window : windows) {
            withdrawWindowInternal(window, NotificationType.PROPERTY_DEACTIVATED,
                    "매물 비활성화로 예약이 무효화되었습니다.");
        }
    }

    /**
     * 윈도우 1 건의 철회 내부 처리. F002 와 매물 비활성화 연동에서 공통 사용.
     *
     * 처리:
     *  1) 윈도우 → WITHDRAWN
     *  2) 소속 활성 슬롯 (AVAILABLE/RESERVED) → WITHDRAWN
     *  3) 소속 슬롯에 묶인 CONFIRMED 예약 → INVALIDATED
     *  4) 소속 슬롯에 묶인 활성 구독 → EXPIRED (SLOT_CLOSED 사유로 폐기)
     *  5) 무효화된 예약의 탐색자에게 통지 1 건씩
     */
    private WindowWithdrawResponseDto withdrawWindowInternal(VisitWindowEntity window,
                                                             NotificationType notifyType,
                                                             String notifyMessage) {
        LocalDateTime now = LocalDateTime.now();

        // 윈도우 철회
        window.setStatus(VisitWindowStatus.WITHDRAWN);
        window.setWithdrawnAt(now);
        windowRepository.save(window);

        // 소속 활성 슬롯 식별
        List<VisitSlotEntity> activeSlots = slotRepository.findByWindowIdAndStatusIn(
                window.getWindowId(),
                List.of(VisitSlotStatus.AVAILABLE, VisitSlotStatus.RESERVED));
        List<Long> slotIds = activeSlots.stream()
                .map(VisitSlotEntity::getSlotId)
                .collect(Collectors.toList());

        // 확정 예약 무효화
        List<WindowWithdrawResponseDto.InvalidatedReservation> invalidatedList = new ArrayList<>();
        if (!slotIds.isEmpty()) {
            List<VisitReservationEntity> confirmedReservations =
                    reservationRepository.findBySlotIdInAndStatus(slotIds,
                            VisitReservationStatus.CONFIRMED);
            for (VisitReservationEntity res : confirmedReservations) {
                res.setStatus(VisitReservationStatus.INVALIDATED);
                res.setInvalidatedAt(now);
                reservationRepository.save(res);

                invalidatedList.add(WindowWithdrawResponseDto.InvalidatedReservation.builder()
                        .reservationId(res.getReservationId())
                        .slotId(res.getSlotId())
                        .searcherUserId(res.getSearcherUserId())
                        .build());

                // 탐색자에게 통지
                notificationService.notify(
                        res.getSearcherUserId(), notifyType,
                        res.getSlotId(), res.getReservationId(), window.getPropertyId(),
                        notifyMessage);
            }

            // 활성 구독 폐기
            List<ReopenSubscriptionEntity> activeSubs =
                    subscriptionRepository.findBySlotIdInAndStatus(slotIds, SubscriptionStatus.ACTIVE);
            for (ReopenSubscriptionEntity sub : activeSubs) {
                sub.setStatus(SubscriptionStatus.EXPIRED);
                sub.setTerminatedAt(now);
                sub.setTerminationReason(SubscriptionTerminationReason.SLOT_CLOSED);
                subscriptionRepository.save(sub);
            }
        }

        // 슬롯 WITHDRAWN 전이
        for (VisitSlotEntity slot : activeSlots) {
            slot.setStatus(VisitSlotStatus.WITHDRAWN);
            slotRepository.save(slot);
        }

        log.info("[VISIT_WINDOW_WITHDRAWN] windowId={}, slots={}, invalidatedReservations={}",
                window.getWindowId(), activeSlots.size(), invalidatedList.size());

        return WindowWithdrawResponseDto.builder()
                .windowId(window.getWindowId())
                .status(window.getStatus().name())
                .withdrawnAt(window.getWithdrawnAt())
                .invalidatedReservations(invalidatedList)
                .build();
    }

    // ====================================================================
    // F004 — 방문 슬롯 예약 (섹션 6.4)
    // ====================================================================

    @Transactional
    public ReservationCreateResponseDto createReservation(ReservationCreateRequestDto dto,
                                                          String userId) {
        Long slotId = dto.getSlotId();
        LocalDateTime now = LocalDateTime.now();

        // 1·2단계: 슬롯 유효성 및 시점 확인
        VisitSlotEntity slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new VisitSlotNotFoundException(
                        "슬롯을 찾을 수 없습니다. slotId=" + slotId));

        VisitWindowEntity window = windowRepository.findById(slot.getWindowId())
                .orElseThrow(() -> new VisitWindowNotFoundException(
                        "슬롯의 윈도우를 찾을 수 없습니다. windowId=" + slot.getWindowId()));

        if (slot.getStatus() != VisitSlotStatus.AVAILABLE) {
            throw SlotUnavailableException.alreadyReserved(
                    window.getLeaseType(),
                    findAvailableSlotItems(window.getPropertyId(), window.getLeaseType()));
        }
        if (!slot.getStartTime().isAfter(now)) {
            throw SlotUnavailableException.startTimeExpired(
                    window.getLeaseType(),
                    findAvailableSlotItems(window.getPropertyId(), window.getLeaseType()));
        }

        // 3단계: 자기 매물 예약 제한
        PropertyOwner owner = lookupPropertyOwner(window.getPropertyId(), window.getLeaseType());
        if (userId.equals(owner.registeredUserId)) {
            throw new SelfPropertyReservationException(
                    "자신이 등록한 매물의 슬롯은 예약할 수 없습니다.");
        }

        // 4·5단계: 동일 매물 중복 예약 / 시간 겹침 검증
        List<VisitReservationEntity> activeReservations =
                reservationRepository.findBySearcherUserIdAndStatus(userId, VisitReservationStatus.CONFIRMED);

        for (VisitReservationEntity existing : activeReservations) {
            VisitSlotEntity existingSlot = slotRepository.findById(existing.getSlotId()).orElse(null);
            if (existingSlot == null) continue;
            VisitWindowEntity existingWindow = windowRepository.findById(existingSlot.getWindowId()).orElse(null);
            if (existingWindow == null) continue;

            if (existingWindow.getPropertyId().equals(window.getPropertyId())
                    && existingWindow.getLeaseType() == window.getLeaseType()) {
                throw new DuplicateReservationException(
                        "같은 매물에 이미 활성 예약이 있습니다.");
            }
            if (existingSlot.getStartTime().isBefore(slot.getEndTime())
                    && existingSlot.getEndTime().isAfter(slot.getStartTime())) {
                throw new ReservationTimeOverlapException(
                        "기존 활성 예약의 방문 시간과 겹칩니다.");
            }
        }

        // 6단계: 슬롯 상태 전이 + 예약 생성
        slot.setStatus(VisitSlotStatus.RESERVED);
        slotRepository.save(slot);

        VisitReservationEntity reservation = VisitReservationEntity.builder()
                .slotId(slot.getSlotId())
                .searcherUserId(userId)
                .status(VisitReservationStatus.CONFIRMED)
                .confirmedAt(now)
                .build();
        VisitReservationEntity savedReservation = reservationRepository.save(reservation);

        // 구독자가 본 슬롯에 활성 구독을 가지고 있었다면 FULFILLED 로 마감
        Optional<ReopenSubscriptionEntity> ownSubscription =
                subscriptionRepository.findBySlotIdAndSearcherUserIdAndStatus(
                        slot.getSlotId(), userId, SubscriptionStatus.ACTIVE);
        ownSubscription.ifPresent(sub -> {
            sub.setStatus(SubscriptionStatus.FULFILLED);
            sub.setTerminatedAt(now);
            sub.setTerminationReason(SubscriptionTerminationReason.RESERVED);
            subscriptionRepository.save(sub);
        });

        // 7단계: 알림 생성 (등록자에게)
        notificationService.notify(owner.registeredUserId, NotificationType.SLOT_RESERVED,
                slot.getSlotId(), savedReservation.getReservationId(), window.getPropertyId(),
                "회원님의 슬롯이 예약되었습니다.");

        log.info("[VISIT_RESERVATION_CONFIRMED] reservationId={}, slotId={}, searcher={}, registrant={}",
                savedReservation.getReservationId(), slot.getSlotId(), userId, owner.registeredUserId);

        // 8단계: 응답 — 등록자 연락 정보 포함
        return ReservationCreateResponseDto.builder()
                .reservationId(savedReservation.getReservationId())
                .slotId(slot.getSlotId())
                .propertyId(window.getPropertyId())
                .leaseType(window.getLeaseType().name())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(savedReservation.getStatus().name())
                .confirmedAt(savedReservation.getConfirmedAt())
                .registrant(buildContact(owner.member))
                .build();
    }

    // ====================================================================
    // F005 — 방문 예약 취소 (섹션 6.5)
    // ====================================================================

    @Transactional
    public ReservationCancelResponseDto cancelReservation(Long reservationId, String userId) {
        // 1단계: 요청 검증 및 권한 확인
        VisitReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new VisitReservationNotFoundException(
                        "예약을 찾을 수 없습니다. reservationId=" + reservationId));

        if (reservation.getStatus() != VisitReservationStatus.CONFIRMED) {
            throw new InvalidStateTransitionException(
                    "이미 취소/무효화/종료된 예약입니다.");
        }
        if (!userId.equals(reservation.getSearcherUserId())) {
            throw new UnauthorizedAccessException("본인 예약만 취소할 수 있습니다.");
        }

        // 2단계: 취소 가능 시점 검증
        VisitSlotEntity slot = slotRepository.findById(reservation.getSlotId())
                .orElseThrow(() -> new VisitSlotNotFoundException(
                        "예약의 슬롯을 찾을 수 없습니다. slotId=" + reservation.getSlotId()));
        LocalDateTime now = LocalDateTime.now();
        if (!slot.getStartTime().isAfter(now)) {
            throw new CannotCancelException(
                    "슬롯 시작 시각이 지난 예약은 취소할 수 없습니다.");
        }

        // 3단계: 원자적 상태 전이
        reservation.setStatus(VisitReservationStatus.CANCELLED);
        reservation.setCancelledAt(now);
        reservationRepository.save(reservation);

        slot.setStatus(VisitSlotStatus.AVAILABLE);
        slotRepository.save(slot);

        // 4단계: 재개방 알림 — 활성 구독자에게 (구독 자체는 유지)
        List<ReopenSubscriptionEntity> subscribers =
                subscriptionRepository.findBySlotIdAndStatus(slot.getSlotId(), SubscriptionStatus.ACTIVE);
        VisitWindowEntity window = windowRepository.findById(slot.getWindowId()).orElse(null);
        String propertyIdForNotify = window == null ? null : window.getPropertyId();
        for (ReopenSubscriptionEntity sub : subscribers) {
            notificationService.notify(sub.getSearcherUserId(), NotificationType.SLOT_REOPENED,
                    slot.getSlotId(), null, propertyIdForNotify,
                    "구독 중인 슬롯이 다시 열렸습니다.");
        }

        log.info("[VISIT_RESERVATION_CANCELLED] reservationId={}, slotId={}, notifiedSubscribers={}",
                reservation.getReservationId(), slot.getSlotId(), subscribers.size());

        return ReservationCancelResponseDto.builder()
                .reservationId(reservation.getReservationId())
                .status(reservation.getStatus().name())
                .cancelledAt(reservation.getCancelledAt())
                .reopenedSlot(ReservationCancelResponseDto.ReopenedSlot.builder()
                        .slotId(slot.getSlotId())
                        .status(slot.getStatus().name())
                        .build())
                .build();
    }

    // ====================================================================
    // F006 — 재개방 알림 구독 신청·해제 (섹션 6.6)
    // ====================================================================

    @Transactional
    public SubscriptionResponseDto subscribeToSlot(Long slotId, String userId) {
        // 1단계: 슬롯 존재·예약됨 확인
        VisitSlotEntity slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new VisitSlotNotFoundException(
                        "슬롯을 찾을 수 없습니다. slotId=" + slotId));
        if (slot.getStatus() != VisitSlotStatus.RESERVED) {
            throw SlotUnavailableException.notAvailableStatus(null, Collections.emptyList());
        }

        // 2단계: 자기 매물 구독 제한
        VisitWindowEntity window = windowRepository.findById(slot.getWindowId())
                .orElseThrow(() -> new VisitWindowNotFoundException(
                        "슬롯의 윈도우를 찾을 수 없습니다."));
        PropertyOwner owner = lookupPropertyOwner(window.getPropertyId(), window.getLeaseType());
        if (userId.equals(owner.registeredUserId)) {
            throw new SelfPropertySubscriptionException(
                    "자신이 등록한 매물의 슬롯은 구독할 수 없습니다.");
        }

        // 3단계: 자기 확정 예약 슬롯 구독 제한
        Optional<VisitReservationEntity> confirmedOnSlot =
                reservationRepository.findBySlotIdAndStatus(slotId, VisitReservationStatus.CONFIRMED);
        if (confirmedOnSlot.isPresent() && userId.equals(confirmedOnSlot.get().getSearcherUserId())) {
            throw new SelfReservationSubscriptionException(
                    "자신이 예약한 슬롯은 구독할 수 없습니다.");
        }

        // 4단계: 중복 구독 검증
        Optional<ReopenSubscriptionEntity> existing =
                subscriptionRepository.findBySlotIdAndSearcherUserIdAndStatus(
                        slotId, userId, SubscriptionStatus.ACTIVE);
        if (existing.isPresent()) {
            throw new DuplicateSubscriptionException("이미 본 슬롯에 활성 구독이 있습니다.");
        }

        // 5단계: 구독 생성
        LocalDateTime now = LocalDateTime.now();
        ReopenSubscriptionEntity subscription = ReopenSubscriptionEntity.builder()
                .slotId(slotId)
                .searcherUserId(userId)
                .status(SubscriptionStatus.ACTIVE)
                .subscribedAt(now)
                .build();
        ReopenSubscriptionEntity saved = subscriptionRepository.save(subscription);

        log.info("[VISIT_SUBSCRIPTION_CREATED] subscriptionId={}, slotId={}, searcher={}",
                saved.getSubscriptionId(), slotId, userId);

        return SubscriptionResponseDto.builder()
                .subscriptionId(saved.getSubscriptionId())
                .slotId(saved.getSlotId())
                .status(saved.getStatus().name())
                .subscribedAt(saved.getSubscribedAt())
                .build();
    }

    @Transactional
    public SubscriptionResponseDto unsubscribeFromSlot(Long slotId, String userId) {
        // 슬롯 존재 확인 (404 분기를 위해 우선 검증)
        if (!slotRepository.existsById(slotId)) {
            throw new VisitSlotNotFoundException("슬롯을 찾을 수 없습니다. slotId=" + slotId);
        }

        ReopenSubscriptionEntity sub = subscriptionRepository
                .findBySlotIdAndSearcherUserIdAndStatus(slotId, userId, SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ActiveSubscriptionNotFoundException(
                        "활성 구독이 없습니다. slotId=" + slotId));

        LocalDateTime now = LocalDateTime.now();
        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setTerminatedAt(now);
        sub.setTerminationReason(SubscriptionTerminationReason.USER_CANCELLED);
        subscriptionRepository.save(sub);

        log.info("[VISIT_SUBSCRIPTION_CANCELLED] subscriptionId={}, slotId={}, searcher={}",
                sub.getSubscriptionId(), slotId, userId);

        return SubscriptionResponseDto.builder()
                .slotId(sub.getSlotId())
                .status(sub.getStatus().name())
                .terminatedAt(sub.getTerminatedAt())
                .build();
    }

    // ====================================================================
    // F007 — 방문 결과 분류 (섹션 6.7 등록자 주도 부분)
    // ====================================================================

    @Transactional
    public ResultClassifyResponseDto classifyVisitResult(Long reservationId,
                                                         ResultClassifyRequestDto dto,
                                                         String userId) {
        VisitReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new VisitReservationNotFoundException(
                        "예약을 찾을 수 없습니다. reservationId=" + reservationId));

        if (reservation.getStatus() != VisitReservationStatus.COMPLETED) {
            throw new CannotClassifyException("종료 상태의 예약만 분류할 수 있습니다.");
        }
        if (reservation.getVisitResult() != null) {
            throw new CannotClassifyException("이미 분류된 예약입니다.");
        }

        VisitSlotEntity slot = slotRepository.findById(reservation.getSlotId())
                .orElseThrow(() -> new VisitSlotNotFoundException(
                        "예약의 슬롯을 찾을 수 없습니다."));
        VisitWindowEntity window = windowRepository.findById(slot.getWindowId())
                .orElseThrow(() -> new VisitWindowNotFoundException(
                        "슬롯의 윈도우를 찾을 수 없습니다."));

        PropertyOwner owner = lookupPropertyOwnerLenient(window.getPropertyId(), window.getLeaseType());
        if (!userId.equals(owner.registeredUserId)) {
            throw new UnauthorizedAccessException("매물의 등록자만 결과를 분류할 수 있습니다.");
        }

        VisitResult result = VisitResult.valueOf(dto.getVisitResult());
        LocalDateTime now = LocalDateTime.now();
        reservation.setVisitResult(result);
        reservation.setResultClassifiedAt(now);
        reservationRepository.save(reservation);

        log.info("[VISIT_RESULT_CLASSIFIED] reservationId={}, result={}, by={}",
                reservationId, result, userId);

        return ResultClassifyResponseDto.builder()
                .reservationId(reservationId)
                .visitResult(result.name())
                .resultClassifiedAt(now)
                .build();
    }

    // ====================================================================
    // 헬퍼
    // ====================================================================

    /**
     * 매물 (PROPERTIES_CHARTER 또는 PROPERTIES_MONTHLY) 을 조회하여 등록자 정보를 함께
     * 반환한다. F001·F004 처럼 매물이 ACTIVE 이어야 하는 경로에서 사용한다 — 비활성/부재
     * 매물은 E7204 로 거부.
     */
    private PropertyOwner lookupPropertyOwner(String propertyId, LeaseType leaseType) {
        if (leaseType == LeaseType.CHARTER) {
            PropertyCharterEntity p = charterRepository.findById(propertyId)
                    .orElseThrow(() -> new PropertyNotFoundOrInactiveException(
                            "매물을 찾을 수 없습니다. propertyId=" + propertyId));
            if (p.getStatus() != PropertyStatus.ACTIVE) {
                throw new PropertyNotFoundOrInactiveException("매물이 활성 상태가 아닙니다.");
            }
            if (p.getRegisteredUserId() == null) {
                throw new PropertyNotFoundOrInactiveException("등록자가 없는 매물입니다.");
            }
            MembersEntity member = memberRepository.findById(p.getRegisteredUserId()).orElse(null);
            return new PropertyOwner(p.getRegisteredUserId(), member);
        } else {
            PropertyMonthlyEntity p = monthlyRepository.findById(propertyId)
                    .orElseThrow(() -> new PropertyNotFoundOrInactiveException(
                            "매물을 찾을 수 없습니다. propertyId=" + propertyId));
            if (p.getStatus() != PropertyStatus.ACTIVE) {
                throw new PropertyNotFoundOrInactiveException("매물이 활성 상태가 아닙니다.");
            }
            if (p.getRegisteredUserId() == null) {
                throw new PropertyNotFoundOrInactiveException("등록자가 없는 매물입니다.");
            }
            MembersEntity member = memberRepository.findById(p.getRegisteredUserId()).orElse(null);
            return new PropertyOwner(p.getRegisteredUserId(), member);
        }
    }

    /**
     * 매물 비활성 여부와 무관하게 등록자 정보만 조회한다. F007 결과 분류처럼 매물이
     * COMPLETED/DELETED 로 전이된 이후에도 종료된 예약에 대한 분류가 가능해야 하는
     * 경로에서 사용한다.
     */
    private PropertyOwner lookupPropertyOwnerLenient(String propertyId, LeaseType leaseType) {
        String registeredUserId;
        if (leaseType == LeaseType.CHARTER) {
            PropertyCharterEntity p = charterRepository.findById(propertyId)
                    .orElseThrow(() -> new PropertyNotFoundOrInactiveException(
                            "매물을 찾을 수 없습니다. propertyId=" + propertyId));
            registeredUserId = p.getRegisteredUserId();
        } else {
            PropertyMonthlyEntity p = monthlyRepository.findById(propertyId)
                    .orElseThrow(() -> new PropertyNotFoundOrInactiveException(
                            "매물을 찾을 수 없습니다. propertyId=" + propertyId));
            registeredUserId = p.getRegisteredUserId();
        }
        if (registeredUserId == null) {
            throw new UnauthorizedAccessException("등록자가 없는 매물입니다.");
        }
        MembersEntity member = memberRepository.findById(registeredUserId).orElse(null);
        return new PropertyOwner(registeredUserId, member);
    }

    private void verifyRegistrant(PropertyOwner owner, String userId) {
        if (!userId.equals(owner.registeredUserId)) {
            throw new UnauthorizedAccessException("매물의 등록자만 수행할 수 있는 작업입니다.");
        }
    }

    /**
     * E7007/E7008/E7013 거부 응답에 동봉할 대체 슬롯 목록.
     * 같은 매물·같은 임대 유형의 현재 예약 가능 (AVAILABLE) 슬롯만 시작 시각 순.
     */
    private List<SlotQueryResponseDto.SlotItem> findAvailableSlotItems(String propertyId,
                                                                       LeaseType leaseType) {
        List<VisitWindowEntity> activeWindows = windowRepository
                .findByPropertyIdAndLeaseTypeAndStatus(propertyId, leaseType, VisitWindowStatus.ACTIVE);
        if (activeWindows.isEmpty()) return Collections.emptyList();

        List<Long> windowIds = activeWindows.stream()
                .map(VisitWindowEntity::getWindowId)
                .collect(Collectors.toList());
        List<VisitSlotEntity> slots = slotRepository.findByWindowIdInAndStatusIn(
                windowIds, List.of(VisitSlotStatus.AVAILABLE));
        return slots.stream()
                .sorted((a, b) -> a.getStartTime().compareTo(b.getStartTime()))
                .map(s -> SlotQueryResponseDto.SlotItem.builder()
                        .slotId(s.getSlotId())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .status(s.getStatus().name())
                        .build())
                .collect(Collectors.toList());
    }

    private ContactInfoDto buildContact(MembersEntity member) {
        if (member == null) return null;
        return ContactInfoDto.builder()
                .userId(member.getId())
                .username(member.getNickName())
                .contact(member.getTel())
                .build();
    }

    /** 매물 등록자 식별자와 회원 엔티티를 함께 운반하는 내부 holder. */
    @Value
    private static class PropertyOwner {
        String registeredUserId;
        MembersEntity member;
    }
}

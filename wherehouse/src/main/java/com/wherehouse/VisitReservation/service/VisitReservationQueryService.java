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
import com.wherehouse.VisitReservation.repository.VisitNotificationRepository;
import com.wherehouse.VisitReservation.repository.VisitReservationRepository;
import com.wherehouse.VisitReservation.repository.VisitSlotRepository;
import com.wherehouse.VisitReservation.repository.VisitWindowRepository;
import com.wherehouse.members.dao.MemberEntityRepository;
import com.wherehouse.members.model.MembersEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 방문 예약 조회 서비스 (설계 명세서 섹션 6.3, 6.8, 7.12).
 *
 * F003 슬롯 조회 / F008 탐색자 예약·구독 현황 / 등록자 슬롯 현황 / 알림 조회·읽음 처리를
 * 담당한다. 모든 조회는 Oracle 에서 직접 응답하며 별도의 캐시 저장소를 두지 않는다
 * (섹션 2.1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitReservationQueryService {

    private final VisitWindowRepository windowRepository;
    private final VisitSlotRepository slotRepository;
    private final VisitReservationRepository reservationRepository;
    private final ReopenSubscriptionRepository subscriptionRepository;
    private final VisitNotificationRepository notificationRepository;

    private final PropertyCharterRegistrationRepository charterRepository;
    private final PropertyMonthlyRegistrationRepository monthlyRepository;
    private final MemberEntityRepository memberRepository;

    private static final int NOTIFICATION_MAX_LIMIT = 100;
    private static final int NOTIFICATION_DEFAULT_LIMIT = 20;

    // ====================================================================
    // F003 — 방문 슬롯 조회 (섹션 6.3, 7.3)
    // ====================================================================

    @Transactional(readOnly = true)
    public SlotQueryResponseDto findSlots(String propertyId, LeaseType leaseType) {
        // 매물 존재·활성 검증 — 임대 유형이 지정된 경우 그 유형의 매물,
        // 미지정인 경우 둘 중 하나라도 활성이면 통과.
        if (leaseType != null) {
            ensurePropertyActive(propertyId, leaseType);
        } else {
            if (!isAnyActive(propertyId)) {
                throw new PropertyNotFoundOrInactiveException(
                        "매물을 찾을 수 없습니다. propertyId=" + propertyId);
            }
        }

        SlotQueryResponseDto.SlotQueryResponseDtoBuilder builder = SlotQueryResponseDto.builder()
                .propertyId(propertyId);

        if (leaseType == null || leaseType == LeaseType.CHARTER) {
            List<SlotQueryResponseDto.SlotItem> charter =
                    findActiveSlotItemsByPropertyAndType(propertyId, LeaseType.CHARTER);
            if (!charter.isEmpty()) builder.charter(charter);
        }
        if (leaseType == null || leaseType == LeaseType.MONTHLY) {
            List<SlotQueryResponseDto.SlotItem> monthly =
                    findActiveSlotItemsByPropertyAndType(propertyId, LeaseType.MONTHLY);
            if (!monthly.isEmpty()) builder.monthly(monthly);
        }
        return builder.build();
    }

    private List<SlotQueryResponseDto.SlotItem> findActiveSlotItemsByPropertyAndType(
            String propertyId, LeaseType leaseType) {
        List<VisitWindowEntity> windows = windowRepository
                .findByPropertyIdAndLeaseTypeAndStatus(propertyId, leaseType, VisitWindowStatus.ACTIVE);
        if (windows.isEmpty()) return Collections.emptyList();

        List<Long> windowIds = windows.stream()
                .map(VisitWindowEntity::getWindowId)
                .collect(Collectors.toList());
        List<VisitSlotEntity> slots = slotRepository.findByWindowIdInAndStatusIn(
                windowIds, List.of(VisitSlotStatus.AVAILABLE, VisitSlotStatus.RESERVED));

        return slots.stream()
                .sorted(Comparator.comparing(VisitSlotEntity::getStartTime))
                .map(s -> SlotQueryResponseDto.SlotItem.builder()
                        .slotId(s.getSlotId())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .status(s.getStatus().name())
                        .build())
                .collect(Collectors.toList());
    }

    // ====================================================================
    // F008 — 탐색자 예약 현황 (섹션 6.8, 7.9)
    // ====================================================================

    @Transactional(readOnly = true)
    public SearcherReservationListDto findSearcherReservations(String userId) {
        List<VisitReservationEntity> reservations =
                reservationRepository.findBySearcherUserIdOrderByConfirmedAtDesc(userId);

        List<SearcherReservationListDto.ReservationItem> items = reservations.stream()
                .map(this::toSearcherReservationItem)
                .collect(Collectors.toList());

        return SearcherReservationListDto.builder().reservations(items).build();
    }

    private SearcherReservationListDto.ReservationItem toSearcherReservationItem(VisitReservationEntity r) {

        VisitSlotEntity slot = slotRepository.findById(r.getSlotId()).orElse(null);
        VisitWindowEntity window = slot == null ? null
                : windowRepository.findById(slot.getWindowId()).orElse(null);

        // 예약 등록자 연락 정보 — 슬롯에 대한 예약 내역(테이블 VISIT_RESRVATION)이 CONFIRMED 또는 COMPLETED 일 때만 노출
        ContactInfoDto registrant = null;

        /* 윈도우(테이블 VISIT_WINDOW) 내 포함된 매물 정보를 따라 매물 등록자 정보 찾기 - 매물 예약 정보는 매물 정보 뿐만 아니라 매물 등록자 정보(유저 ID, 유저 이름, 유저 전화번호) 포함 응답. */
        if (window != null && shouldExposeContact(r.getStatus())) {

            String registrantId = findRegistrantUserId(window.getPropertyId(), window.getLeaseType());
            if (registrantId != null) {
                MembersEntity m = memberRepository.findById(registrantId).orElse(null);
                registrant = toContactInfo(m);
            }
        }

        return SearcherReservationListDto.ReservationItem.builder()
                .reservationId(r.getReservationId())
                .slotId(r.getSlotId())
                .propertyId(window == null ? null : window.getPropertyId())
                .leaseType(window == null ? null : window.getLeaseType().name())
                .startTime(slot == null ? null : slot.getStartTime())
                .endTime(slot == null ? null : slot.getEndTime())
                .status(r.getStatus().name())
                .visitResult(r.getVisitResult() == null ? null : r.getVisitResult().name())
                .confirmedAt(r.getConfirmedAt())
                .registrant(registrant)
                .build();
    }

    // ====================================================================
    // F008 — 탐색자 구독 현황 (섹션 6.8, 7.10)
    // ====================================================================

    @Transactional(readOnly = true)
    public SearcherSubscriptionListDto findSearcherSubscriptions(String userId) {
        List<ReopenSubscriptionEntity> subscriptions =
                subscriptionRepository.findBySearcherUserIdOrderBySubscribedAtDesc(userId);

        List<SearcherSubscriptionListDto.SubscriptionItem> items = subscriptions.stream()
                .map(s -> {

                    VisitSlotEntity slot = slotRepository.findById(s.getSlotId()).orElse(null);
                    VisitWindowEntity window = slot == null ? null
                            : windowRepository.findById(slot.getWindowId()).orElse(null);

                    return SearcherSubscriptionListDto.SubscriptionItem.builder()
                            .subscriptionId(s.getSubscriptionId())
                            .slotId(s.getSlotId())
                            .propertyId(window == null ? null : window.getPropertyId())
                            .leaseType(window == null ? null : window.getLeaseType().name())
                            .startTime(slot == null ? null : slot.getStartTime())
                            .endTime(slot == null ? null : slot.getEndTime())
                            .slotStatus(slot == null ? null : slot.getStatus().name())
                            .status(s.getStatus().name())
                            .subscribedAt(s.getSubscribedAt())
                            .build();
                })
                .collect(Collectors.toList());

        return SearcherSubscriptionListDto.builder().subscriptions(items).build();
    }

    // ====================================================================
    // F008 — 등록자 슬롯 현황 (섹션 6.8, 7.11)
    // ====================================================================

    @Transactional(readOnly = true)
    public RegistrantSlotListDto findRegistrantSlots(String propertyId, LeaseType leaseType, String userId) {
        // 권한 확인: 매물의 등록자 본인인지 검증.
        // 비활성 매물도 조회 허용 (등록자가 자신의 종료된 매물의 과거 슬롯을 조회하는 경우).
        boolean isRegistrant = false;
        if (leaseType == null || leaseType == LeaseType.CHARTER) {
            isRegistrant = isRegistrantOfCharter(propertyId, userId) || isRegistrant;
        }
        if (leaseType == null || leaseType == LeaseType.MONTHLY) {
            isRegistrant = isRegistrantOfMonthly(propertyId, userId) || isRegistrant;
        }
        if (!isRegistrant) {
            throw new UnauthorizedAccessException("매물의 등록자만 조회할 수 있습니다.");
        }

        RegistrantSlotListDto.RegistrantSlotListDtoBuilder builder = RegistrantSlotListDto.builder()
                .propertyId(propertyId);

        if (leaseType == null || leaseType == LeaseType.CHARTER) {
            List<RegistrantSlotListDto.SlotItem> charter =
                    findAllSlotItemsByPropertyAndTypeForRegistrant(propertyId, LeaseType.CHARTER);
            if (!charter.isEmpty()) builder.charter(charter);
        }
        if (leaseType == null || leaseType == LeaseType.MONTHLY) {
            List<RegistrantSlotListDto.SlotItem> monthly =
                    findAllSlotItemsByPropertyAndTypeForRegistrant(propertyId, LeaseType.MONTHLY);
            if (!monthly.isEmpty()) builder.monthly(monthly);
        }
        return builder.build();
    }

    private List<RegistrantSlotListDto.SlotItem> findAllSlotItemsByPropertyAndTypeForRegistrant(
            String propertyId, LeaseType leaseType) {
        // 등록자에게는 모든 상태의 윈도우(활성·철회) 와 그에 묶인 모든 슬롯을 보여야 함.
        List<VisitWindowEntity> activeWindows = windowRepository
                .findByPropertyIdAndLeaseTypeAndStatus(propertyId, leaseType, VisitWindowStatus.ACTIVE);
        List<VisitWindowEntity> withdrawnWindows = windowRepository
                .findByPropertyIdAndLeaseTypeAndStatus(propertyId, leaseType, VisitWindowStatus.WITHDRAWN);

        List<VisitWindowEntity> allWindows = new ArrayList<>();
        allWindows.addAll(activeWindows);
        allWindows.addAll(withdrawnWindows);
        if (allWindows.isEmpty()) return Collections.emptyList();

        List<Long> windowIds = allWindows.stream()
                .map(VisitWindowEntity::getWindowId)
                .collect(Collectors.toList());
        List<VisitSlotEntity> slots = slotRepository.findByWindowIdIn(windowIds);

        return slots.stream()
                .sorted(Comparator.comparing(VisitSlotEntity::getStartTime))
                .map(s -> {
                    RegistrantSlotListDto.SlotItem.SlotItemBuilder b = RegistrantSlotListDto.SlotItem.builder()
                            .slotId(s.getSlotId())
                            .windowId(s.getWindowId())
                            .startTime(s.getStartTime())
                            .endTime(s.getEndTime())
                            .status(s.getStatus().name());

                    // 슬롯에 묶인 가장 최신 확정·종료 예약을 표시 (활성·종료 둘 다 의미가 있음)
                    List<VisitReservationEntity> resOnSlot =
                            reservationRepository.findBySlotIdOrderByConfirmedAtDesc(s.getSlotId());
                    if (!resOnSlot.isEmpty()) {
                        VisitReservationEntity latest = resOnSlot.get(0);
                        ContactInfoDto searcher = null;
                        if (shouldExposeContact(latest.getStatus())) {
                            MembersEntity m = memberRepository.findById(latest.getSearcherUserId()).orElse(null);
                            searcher = toContactInfo(m);
                        }
                        b.reservation(RegistrantSlotListDto.ReservationInfo.builder()
                                .reservationId(latest.getReservationId())
                                .status(latest.getStatus().name())
                                .visitResult(latest.getVisitResult() == null ? null
                                        : latest.getVisitResult().name())
                                .searcher(searcher)
                                .build());
                    }
                    return b.build();
                })
                .collect(Collectors.toList());
    }

    // ====================================================================
    // 알림 조회 / 읽음 처리 (섹션 7.12, 7.13)
    // ====================================================================

    @Transactional(readOnly = true)
    public NotificationListDto findNotifications(String userId, boolean unreadOnly, Integer limit, Long before) {
        int pageSize = limit == null ? NOTIFICATION_DEFAULT_LIMIT : Math.min(limit, NOTIFICATION_MAX_LIMIT);
        long beforeCursor = before == null ? Long.MAX_VALUE : before;
        Pageable pageable = PageRequest.of(0, pageSize);

        List<VisitNotificationEntity> rows = unreadOnly
                ? notificationRepository.findUnreadByUserIdPaged(userId, beforeCursor, pageable)
                : notificationRepository.findByUserIdPaged(userId, beforeCursor, pageable);

        List<NotificationListDto.NotificationItem> items = rows.stream()
                .map(n -> NotificationListDto.NotificationItem.builder()
                        .notificationId(n.getNotificationId())
                        .notificationType(n.getNotificationType().name())
                        .message(n.getMessage())
                        .isRead("Y".equals(n.getIsRead()))
                        .createdAt(n.getCreatedAt())
                        .relatedPropertyId(n.getRelatedPropertyId())
                        .relatedSlotId(n.getRelatedSlotId())
                        .relatedReservationId(n.getRelatedReservationId())
                        .build())
                .collect(Collectors.toList());

        Long nextBefore = items.size() < pageSize
                ? null
                : rows.get(rows.size() - 1).getNotificationId();

        return NotificationListDto.builder()
                .notifications(items)
                .nextBefore(nextBefore)
                .build();
    }

    @Transactional
    public NotificationReadResponseDto markNotificationRead(Long notificationId, String userId) {
        VisitNotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new VisitNotificationNotFoundException(
                        "알림을 찾을 수 없습니다. notificationId=" + notificationId));
        if (!userId.equals(notification.getUserId())) {
            throw new UnauthorizedAccessException("본인 알림만 읽음 처리할 수 있습니다.");
        }
        if (!"Y".equals(notification.getIsRead())) {
            notification.setIsRead("Y");
            notificationRepository.save(notification);
        }
        return NotificationReadResponseDto.builder()
                .notificationId(notificationId)
                .isRead(true)
                .build();
    }

    // ====================================================================
    // 헬퍼
    // ====================================================================

    private boolean shouldExposeContact(VisitReservationStatus status) {
        return status == VisitReservationStatus.CONFIRMED
                || status == VisitReservationStatus.COMPLETED;
    }

    private ContactInfoDto toContactInfo(MembersEntity m) {
        if (m == null) return null;
        return ContactInfoDto.builder()
                .userId(m.getId())
                .username(m.getNickName())
                .contact(m.getTel())
                .build();
    }

    private void ensurePropertyActive(String propertyId, LeaseType leaseType) {
        if (leaseType == LeaseType.CHARTER) {
            PropertyCharterEntity p = charterRepository.findById(propertyId)
                    .orElseThrow(() -> new PropertyNotFoundOrInactiveException(
                            "매물을 찾을 수 없습니다. propertyId=" + propertyId));
            if (p.getStatus() != PropertyStatus.ACTIVE) {
                throw new PropertyNotFoundOrInactiveException("매물이 활성 상태가 아닙니다.");
            }
        } else {
            PropertyMonthlyEntity p = monthlyRepository.findById(propertyId)
                    .orElseThrow(() -> new PropertyNotFoundOrInactiveException(
                            "매물을 찾을 수 없습니다. propertyId=" + propertyId));
            if (p.getStatus() != PropertyStatus.ACTIVE) {
                throw new PropertyNotFoundOrInactiveException("매물이 활성 상태가 아닙니다.");
            }
        }
    }

    private boolean isAnyActive(String propertyId) {
        Optional<PropertyCharterEntity> c = charterRepository.findById(propertyId);
        if (c.isPresent() && c.get().getStatus() == PropertyStatus.ACTIVE) return true;
        Optional<PropertyMonthlyEntity> m = monthlyRepository.findById(propertyId);
        return m.isPresent() && m.get().getStatus() == PropertyStatus.ACTIVE;
    }

    private boolean isRegistrantOfCharter(String propertyId, String userId) {
        return charterRepository.findById(propertyId)
                .filter(p -> userId.equals(p.getRegisteredUserId()))
                .isPresent();
    }

    private boolean isRegistrantOfMonthly(String propertyId, String userId) {
        return monthlyRepository.findById(propertyId)
                .filter(p -> userId.equals(p.getRegisteredUserId()))
                .isPresent();
    }

    private String findRegistrantUserId(String propertyId, LeaseType leaseType) {
        if (leaseType == LeaseType.CHARTER) {
            return charterRepository.findById(propertyId)
                    .map(PropertyCharterEntity::getRegisteredUserId).orElse(null);
        }
        return monthlyRepository.findById(propertyId)
                .map(PropertyMonthlyEntity::getRegisteredUserId).orElse(null);
    }
}

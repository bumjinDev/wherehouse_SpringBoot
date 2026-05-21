package com.wherehouse.VisitReservation.controller;

import com.wherehouse.VisitReservation.dto.*;
import com.wherehouse.VisitReservation.entity.LeaseType;
import com.wherehouse.VisitReservation.service.VisitReservationQueryService;
import com.wherehouse.VisitReservation.service.VisitReservationWriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 방문 예약 API 컨트롤러 (설계 명세서 섹션 7).
 *
 * 13 개 엔드포인트를 단일 컨트롤러에 집중한다 (섹션 10.2 컴포넌트 명칭).
 *
 * 인증 정책 (섹션 3.1):
 *   GET /api/v1/visit/properties/{propertyId}/slots 는 선택적 인증으로 비인증 통과를
 *   허용하지만, 그 외 모든 경로는 SecurityConfig 의 신규 필터 체인에서 401 로 차단된다.
 *
 * 인증된 요청자 식별자는 {@code @AuthenticationPrincipal String userId} 로 추출된다 —
 * 기존 도메인 컨트롤러 (PropertyWriteController) 와 동일한 패턴.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/visit")
@RequiredArgsConstructor
@Validated
public class VisitReservationController {

    private final VisitReservationWriteService writeService;
    private final VisitReservationQueryService queryService;

    // ================================================================
    // F001 — 방문 윈도우 공개
    // ================================================================

    @PostMapping("/windows")
    public ResponseEntity<WindowCreateResponseDto> createWindow(
            @Valid @RequestBody WindowCreateRequestDto request,
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_WINDOW_CREATE_REQ] userId={}, propertyId={}, leaseType={}",
                userId, request.getPropertyId(), request.getLeaseType());
        WindowCreateResponseDto response = writeService.createWindow(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ================================================================
    // F002 — 방문 윈도우 철회
    // ================================================================

    @DeleteMapping("/windows/{windowId}")
    public ResponseEntity<WindowWithdrawResponseDto> withdrawWindow(
            @PathVariable Long windowId,
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_WINDOW_WITHDRAW_REQ] userId={}, windowId={}", userId, windowId);
        WindowWithdrawResponseDto response = writeService.withdrawWindow(windowId, userId);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // F003 — 방문 슬롯 조회 (선택적 인증)
    // ================================================================

    @GetMapping("/properties/{propertyId}/slots")
    public ResponseEntity<SlotQueryResponseDto> findSlots(
            @PathVariable String propertyId,
            @RequestParam(name = "lease_type", required = false) String leaseTypeRaw) {
        LeaseType leaseType = parseLeaseTypeOptional(leaseTypeRaw);
        log.info("[VISIT_SLOT_QUERY_REQ] propertyId={}, leaseType={}", propertyId, leaseType);
        SlotQueryResponseDto response = queryService.findSlots(propertyId, leaseType);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // F004 — 방문 슬롯 예약
    // ================================================================

    @PostMapping("/reservations")
    public ResponseEntity<ReservationCreateResponseDto> createReservation(
            @Valid @RequestBody ReservationCreateRequestDto request,
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_RESERVATION_CREATE_REQ] userId={}, slotId={}",
                userId, request.getSlotId());
        ReservationCreateResponseDto response = writeService.createReservation(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ================================================================
    // F005 — 방문 예약 취소
    // ================================================================

    @DeleteMapping("/reservations/{reservationId}")
    public ResponseEntity<ReservationCancelResponseDto> cancelReservation(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_RESERVATION_CANCEL_REQ] userId={}, reservationId={}",
                userId, reservationId);
        ReservationCancelResponseDto response = writeService.cancelReservation(reservationId, userId);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // F006 — 재개방 알림 구독 신청 / 해제
    // ================================================================

    @PostMapping("/slots/{slotId}/subscriptions")
    public ResponseEntity<SubscriptionResponseDto> subscribe(
            @PathVariable Long slotId,
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_SUBSCRIBE_REQ] userId={}, slotId={}", userId, slotId);
        SubscriptionResponseDto response = writeService.subscribeToSlot(slotId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/slots/{slotId}/subscriptions")
    public ResponseEntity<SubscriptionResponseDto> unsubscribe(
            @PathVariable Long slotId,
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_UNSUBSCRIBE_REQ] userId={}, slotId={}", userId, slotId);
        SubscriptionResponseDto response = writeService.unsubscribeFromSlot(slotId, userId);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // F007 — 방문 결과 분류
    // ================================================================

    @PatchMapping("/reservations/{reservationId}/result")
    public ResponseEntity<ResultClassifyResponseDto> classifyResult(
            @PathVariable Long reservationId,
            @Valid @RequestBody ResultClassifyRequestDto request,
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_RESULT_CLASSIFY_REQ] userId={}, reservationId={}, result={}",
                userId, reservationId, request.getVisitResult());
        ResultClassifyResponseDto response =
                writeService.classifyVisitResult(reservationId, request, userId);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // F008 — 탐색자 예약 현황
    // ================================================================

    @GetMapping("/searcher/reservations")
    public ResponseEntity<SearcherReservationListDto> findSearcherReservations(
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_SEARCHER_RESERVATIONS_REQ] userId={}", userId);
        return ResponseEntity.ok(queryService.findSearcherReservations(userId));
    }

    // ================================================================
    // F008 — 탐색자 구독 현황
    // ================================================================

    @GetMapping("/searcher/subscriptions")
    public ResponseEntity<SearcherSubscriptionListDto> findSearcherSubscriptions(
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_SEARCHER_SUBSCRIPTIONS_REQ] userId={}", userId);
        return ResponseEntity.ok(queryService.findSearcherSubscriptions(userId));
    }

    // ================================================================
    // F008 — 등록자 슬롯 현황
    // ================================================================

    @GetMapping("/registrant/properties/{propertyId}/slots")
    public ResponseEntity<RegistrantSlotListDto> findRegistrantSlots(
            @PathVariable String propertyId,
            @RequestParam(name = "lease_type", required = false) String leaseTypeRaw,
            @AuthenticationPrincipal String userId) {
        LeaseType leaseType = parseLeaseTypeOptional(leaseTypeRaw);
        log.info("[VISIT_REGISTRANT_SLOTS_REQ] userId={}, propertyId={}, leaseType={}",
                userId, propertyId, leaseType);
        return ResponseEntity.ok(queryService.findRegistrantSlots(propertyId, leaseType, userId));
    }

    // ================================================================
    // 알림 조회 / 읽음 처리 (섹션 7.12, 7.13)
    // ================================================================

    @GetMapping("/notifications")
    public ResponseEntity<NotificationListDto> findNotifications(
            @RequestParam(name = "unread_only", required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "before", required = false) Long before,
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_NOTIFICATIONS_REQ] userId={}, unreadOnly={}, limit={}, before={}",
                userId, unreadOnly, limit, before);
        return ResponseEntity.ok(queryService.findNotifications(userId, unreadOnly, limit, before));
    }

    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<NotificationReadResponseDto> markNotificationRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal String userId) {
        log.info("[VISIT_NOTIFICATION_READ_REQ] userId={}, notificationId={}", userId, notificationId);
        return ResponseEntity.ok(queryService.markNotificationRead(notificationId, userId));
    }

    // ================================================================
    // 헬퍼
    // ================================================================

    /** 쿼리 매개변수의 lease_type 을 LeaseType 으로 변환. 미지정·null 은 null. */
    private LeaseType parseLeaseTypeOptional(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LeaseType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new com.wherehouse.VisitReservation.exception.customExceptions
                    .InvalidRequestException(
                    "lease_type 은 CHARTER 또는 MONTHLY 입니다. value=" + raw);
        }
    }
}

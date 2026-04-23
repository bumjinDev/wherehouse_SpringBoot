package com.wherehouse.PropertyManagement.controller;

import com.wherehouse.PropertyManagement.dto.*;
import com.wherehouse.PropertyManagement.service.CharterPropertyWriteService;
import com.wherehouse.PropertyManagement.service.MonthlyPropertyWriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 매물 쓰기 API 컨트롤러 — F001·F002·F003.
 *
 * 전세·월세 엔드포인트 물리 분리 구조:
 *   POST  /api/v1/properties/charter                        → 전세 등록
 *   POST  /api/v1/properties/monthly                        → 월세 등록
 *   PATCH /api/v1/properties/charter/{propertyId}           → 전세 수정
 *   PATCH /api/v1/properties/monthly/{propertyId}           → 월세 수정
 *   PATCH /api/v1/properties/charter/{propertyId}/status    → 전세 상태 변경
 *   PATCH /api/v1/properties/monthly/{propertyId}/status    → 월세 상태 변경
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Validated
public class PropertyWriteController {

    private final CharterPropertyWriteService charterPropertyWriteService;
    private final MonthlyPropertyWriteService monthlyPropertyWriteService;

    // ============================================================
    // F001 매물 등록
    // ============================================================

    @PostMapping("/charter")
    public ResponseEntity<PropertyCreateResponseDto> createCharterProperty(
            @Valid @RequestBody CharterCreateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("전세 매물 등록 요청: userId={}, aptNm={}, sggCd={}",
                userId, requestDto.getAptNm(), requestDto.getSggCd());

        PropertyCreateResponseDto response =
                charterPropertyWriteService.createProperty(requestDto, userId);

        log.info("전세 매물 등록 완료: propertyId={}", response.getPropertyId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/monthly")
    public ResponseEntity<PropertyCreateResponseDto> createMonthlyProperty(
            @Valid @RequestBody MonthlyCreateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("월세 매물 등록 요청: userId={}, aptNm={}, sggCd={}",
                userId, requestDto.getAptNm(), requestDto.getSggCd());

        PropertyCreateResponseDto response =
                monthlyPropertyWriteService.createProperty(requestDto, userId);

        log.info("월세 매물 등록 완료: propertyId={}", response.getPropertyId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ============================================================
    // F002 매물 수정
    // ============================================================

    @PatchMapping("/charter/{propertyId}")
    public ResponseEntity<PropertyUpdateResponseDto> updateCharterProperty(
            @PathVariable String propertyId,
            @Valid @RequestBody CharterUpdateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("전세 매물 수정 요청: userId={}, propertyId={}", userId, propertyId);

        PropertyUpdateResponseDto response =
                charterPropertyWriteService.updateProperty(propertyId, requestDto, userId);

        log.info("전세 매물 수정 완료: propertyId={}, changedFields={}",
                response.getPropertyId(), response.getChangedFields());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/monthly/{propertyId}")
    public ResponseEntity<PropertyUpdateResponseDto> updateMonthlyProperty(
            @PathVariable String propertyId,
            @Valid @RequestBody MonthlyUpdateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("월세 매물 수정 요청: userId={}, propertyId={}", userId, propertyId);

        PropertyUpdateResponseDto response =
                monthlyPropertyWriteService.updateProperty(propertyId, requestDto, userId);

        log.info("월세 매물 수정 완료: propertyId={}, changedFields={}",
                response.getPropertyId(), response.getChangedFields());
        return ResponseEntity.ok(response);
    }

    // ============================================================
    // F003 매물 상태 변경
    // ============================================================

    /**
     * 전세 매물 상태 변경.
     *
     * 엔드포인트: PATCH /api/v1/properties/charter/{propertyId}/status
     *
     * 허용 전이 (설계 섹션 6.2):
     *   ACTIVE → COMPLETED (거래완료)
     *   ACTIVE → DELETED   (삭제)
     *
     * Redis 동기화 상태별 분기 (설계 섹션 9.3.5):
     *   COMPLETED: Sorted Set Member 제거 + 매물 Hash status 필드만 갱신
     *   DELETED:   Sorted Set Member 제거 + 매물 Hash 전면 제거
     */
    @PatchMapping("/charter/{propertyId}/status")
    public ResponseEntity<PropertyStatusUpdateResponseDto> changeCharterPropertyStatus(
            @PathVariable String propertyId,
            @Valid @RequestBody PropertyStatusUpdateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("전세 매물 상태 변경 요청: userId={}, propertyId={}, targetStatus={}",
                userId, propertyId, requestDto.getTargetStatus());

        PropertyStatusUpdateResponseDto response =
                charterPropertyWriteService.changeStatus(propertyId, requestDto, userId);

        log.info("전세 매물 상태 변경 완료: propertyId={}, {} → {}",
                response.getPropertyId(), response.getPreviousStatus(), response.getCurrentStatus());
        return ResponseEntity.ok(response);
    }

    /**
     * 월세 매물 상태 변경.
     *
     * 엔드포인트: PATCH /api/v1/properties/monthly/{propertyId}/status
     *
     * 전세와 동일한 전이 규칙. Redis 동기화 시 인덱스 제거 대상이 다름:
     *   전세: idx:charterPrice + idx:area (2개 제거)
     *   월세: idx:deposit + idx:monthlyRent + idx:area (3개 제거)
     */
    @PatchMapping("/monthly/{propertyId}/status")
    public ResponseEntity<PropertyStatusUpdateResponseDto> changeMonthlyPropertyStatus(
            @PathVariable String propertyId,
            @Valid @RequestBody PropertyStatusUpdateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("월세 매물 상태 변경 요청: userId={}, propertyId={}, targetStatus={}",
                userId, propertyId, requestDto.getTargetStatus());

        PropertyStatusUpdateResponseDto response =
                monthlyPropertyWriteService.changeStatus(propertyId, requestDto, userId);

        log.info("월세 매물 상태 변경 완료: propertyId={}, {} → {}",
                response.getPropertyId(), response.getPreviousStatus(), response.getCurrentStatus());
        return ResponseEntity.ok(response);
    }
}
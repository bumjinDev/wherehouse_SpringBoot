package com.wherehouse.PropertyManagement.controller;

import com.wherehouse.PropertyManagement.dto.PropertyCreateRequestDto;
import com.wherehouse.PropertyManagement.dto.PropertyCreateResponseDto;
import com.wherehouse.PropertyManagement.dto.PropertyUpdateRequestDto;
import com.wherehouse.PropertyManagement.dto.PropertyUpdateResponseDto;
import com.wherehouse.PropertyManagement.dto.PropertyStatusUpdateRequestDto;
import com.wherehouse.PropertyManagement.dto.PropertyStatusUpdateResponseDto;
import com.wherehouse.PropertyManagement.service.PropertyWriteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 매물 쓰기 API 컨트롤러 (F001·F002·F003)
 *
 * 설계 명세서 참조:
 *   F001 매물 등록:       POST  /api/v1/properties              (섹션 7.2)
 *   F002 매물 수정:       PATCH /api/v1/properties/{propertyId} (섹션 7.3)
 *   F003 매물 상태 변경:  PATCH /api/v1/properties/{propertyId}/status (섹션 7.4)
 *
 * 인증 요구: 세 API 모두 필수 (섹션 3.2 Protected)
 * 권한 검증: F002·F003은 서비스 계층 내부에서 3단계 로직 수행 (섹션 9.2.3)
 *           - 1단계: 매물 존재 확인 → 실패 시 E4201
 *           - 2단계: 등록자 존재 확인 (배치 매물이면 NULL) → 실패 시 E4003
 *           - 3단계: 본인 일치 확인 → 실패 시 E4003
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Validated
public class PropertyWriteController {

    private final PropertyWriteService propertyWriteService;

    /**
     * F001 매물 등록 (섹션 7.2, 9.1)
     *
     * 처리 단계(R-F001-01 ~ R-F001-09):
     *   인증 컨텍스트 확인 → 1차 유효성 검증 → 식별자 생성 → 중복 감지(F006)
     *   → RDB 저장 → Redis 동기화(F008) → bounds 변경 시 점수 재계산(F009)
     *
     * @param requestDto 매물 등록 요청 (섹션 7.2.1)
     * @param userId     JWT 필터가 주입한 인증 사용자 식별자
     * @return 201 Created (섹션 7.2.2)
     */
    @PostMapping
    public ResponseEntity<PropertyCreateResponseDto> createProperty(
            @Valid @RequestBody PropertyCreateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("매물 등록 요청: userId={}, leaseType={}, aptNm={}, sggCd={}",
                userId, requestDto.getLeaseType(), requestDto.getAptNm(), requestDto.getSggCd());

        PropertyCreateResponseDto response = propertyWriteService.createProperty(requestDto, userId);

        log.info("매물 등록 완료: propertyId={}, dataSource={}, status={}",
                response.getPropertyId(), response.getDataSource(), response.getStatus());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * F002 매물 수정 (섹션 7.3, 9.2)
     *
     * PATCH 시맨틱: 요청 본문에 변경 필드만 포함 (섹션 7.3.1).
     * 불변 속성(sggCd, jibun, aptNm, floor, excluUseAr)은 1차 검증에서 차단 → E4001.
     * 종료 상태(COMPLETED/DELETED) 매물 수정 시 E4106 (R-F002-04).
     *
     * @param propertyId MD5 해시 32자
     * @param requestDto 수정 요청 (가변 속성만)
     * @param userId     인증 사용자 식별자
     * @return 200 OK (섹션 7.3.2)
     */
    @PatchMapping("/{propertyId}")
    public ResponseEntity<PropertyUpdateResponseDto> updateProperty(
            @PathVariable String propertyId,
            @Valid @RequestBody PropertyUpdateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("매물 수정 요청: userId={}, propertyId={}", userId, propertyId);

        PropertyUpdateResponseDto response =
                propertyWriteService.updateProperty(propertyId, requestDto, userId);

        log.info("매물 수정 완료: propertyId={}, changedFields={}",
                response.getPropertyId(), response.getChangedFields());

        return ResponseEntity.ok(response);
    }

    /**
     * F003 매물 상태 변경 (섹션 7.4, 9.3)
     *
     * 허용 전이(섹션 6.2):
     *   ACTIVE → COMPLETED (거래완료)
     *   ACTIVE → DELETED   (삭제)
     * 금지 전이 시 E4002.
     *
     * Redis 동기화 상태별 분기(섹션 9.3.5):
     *   COMPLETED: Sorted Set Member 제거 + 매물 Hash의 status 필드만 갱신
     *   DELETED:   Sorted Set Member 제거 + 매물 Hash 전면 제거
     *
     * @param propertyId 대상 매물 식별자
     * @param requestDto targetStatus 포함 (COMPLETED 또는 DELETED만 허용)
     * @param userId     인증 사용자 식별자
     * @return 200 OK (섹션 7.4.2)
     */
    @PatchMapping("/{propertyId}/status")
    public ResponseEntity<PropertyStatusUpdateResponseDto> changePropertyStatus(
            @PathVariable String propertyId,
            @Valid @RequestBody PropertyStatusUpdateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("매물 상태 변경 요청: userId={}, propertyId={}, targetStatus={}",
                userId, propertyId, requestDto.getTargetStatus());

        PropertyStatusUpdateResponseDto response =
                propertyWriteService.changeStatus(propertyId, requestDto, userId);

        log.info("매물 상태 변경 완료: propertyId={}, {} -> {}",
                response.getPropertyId(), response.getPreviousStatus(), response.getCurrentStatus());

        return ResponseEntity.ok(response);
    }
}

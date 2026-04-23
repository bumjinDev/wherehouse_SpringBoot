package com.wherehouse.PropertyManagement.controller;

import com.wherehouse.PropertyManagement.dto.PropertyDetailDto;
import com.wherehouse.PropertyManagement.dto.PropertyListRequestDto;
import com.wherehouse.PropertyManagement.dto.PropertyListResponseDto;
import com.wherehouse.PropertyManagement.service.PropertyQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 매물 조회 API 컨트롤러 (F004)
 *
 * 설계 명세서 참조:
 *   F004 매물 목록 조회: GET /api/v1/properties              (섹션 7.5, 9.4.1)
 *   F004 매물 상세 조회: GET /api/v1/properties/{propertyId} (섹션 7.6, 9.4.2)
 *
 * 인증: 불필요 (Public, 섹션 3.2)
 * 데이터 경로: Oracle RDB 직접 조회, Redis 미경유 (섹션 5.3).
 *             쓰기 경로의 Redis 동기화 상태와 무관하게 일관된 응답 보장 (섹션 9.4.3).
 *
 * 기존 PropertySearchController(/api/v1/properties/search)와 base path 공유하나
 * Spring MVC의 리터럴 경로 우선 매칭 규칙상 충돌 없음.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Validated
public class PropertyQueryController {

    private final PropertyQueryService propertyQueryService;

    /**
     * F004 매물 목록 조회 (섹션 7.5, 9.4.1)
     *
     * Query Parameters (섹션 7.5.1):
     *   leaseType, district, status(기본 ACTIVE), dataSource,
     *   keyword, page(기본 0), size(기본 20), sort(기본 latest)
     *
     * DELETED 상태는 status=DELETED로 명시해도 응답에 포함되지 않음 (섹션 6.4).
     * leaseType 미지정 시 전세·월세 두 테이블 조회 후 병합 (섹션 9.4.1).
     *
     * @param requestDto @ModelAttribute 로 Query String 바인딩
     * @return 200 OK (섹션 7.5.2)
     */
    @GetMapping
    public ResponseEntity<PropertyListResponseDto> getProperties(
            @Valid @ModelAttribute PropertyListRequestDto requestDto) {

        log.info("매물 목록 조회 요청: leaseType={}, district={}, status={}, keyword={}, page={}, sort={}",
                requestDto.getLeaseType(),
                requestDto.getDistrict(),
                requestDto.getStatus(),
                requestDto.getKeyword(),
                requestDto.getPage(),
                requestDto.getSort());

        PropertyListResponseDto response = propertyQueryService.getProperties(requestDto);

        log.info("매물 목록 조회 완료: totalElements={}, currentPage={}",
                response.getTotalElements(), response.getCurrentPage());

        return ResponseEntity.ok(response);
    }

    /**
     * F004 매물 상세 조회 (섹션 7.6, 9.4.2)
     *
     * 처리 단계(R-F004D-01 ~ R-F004D-06):
     *   Path Param 바인딩 → 임대 유형 탐색(두 테이블 각각 조회)
     *   → 상태 확인(DELETED/미존재 시 E4201) → 리뷰 통계 병합 → 응답 매핑
     *
     * COMPLETED 상태는 조회 허용 (섹션 6.4, "거래완료된 매물입니다" 안내 UI 지원 목적).
     *
     * @param propertyId MD5 해시 32자
     * @return 200 OK (섹션 7.6.2)
     */
    @GetMapping("/{propertyId}")
    public ResponseEntity<PropertyDetailDto> getPropertyDetail(
            @PathVariable String propertyId) {

        log.info("매물 상세 조회 요청: propertyId={}", propertyId);

        PropertyDetailDto response = propertyQueryService.getPropertyDetail(propertyId);

        log.info("매물 상세 조회 완료: propertyId={}, status={}, dataSource={}",
                response.getPropertyId(), response.getStatus(), response.getDataSource());

        return ResponseEntity.ok(response);
    }
}

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

import java.util.List;

/**
 * 매물 조회 API 컨트롤러 (F004).
 *
 * F004 매물 목록 조회: GET /api/v1/properties              (섹션 7.5, 9.4.1)
 * F004 매물 상세 조회: GET /api/v1/properties/{propertyId} (섹션 7.6, 9.4.2)
 *
 * 인증: 불필요 (Public, 섹션 3.2).
 * 데이터 경로: Oracle RDB 직접 조회, Redis 미경유 (섹션 5.3).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Validated
public class PropertyQueryController {

    private final PropertyQueryService propertyQueryService;

    /**
     * F004 매물 목록 조회 (섹션 7.5, 9.4.1).
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
     * F004 매물 상세 조회 (섹션 7.6, 9.4.2).
     *
     * 응답: List&lt;PropertyDetailDto&gt; (최소 1건, 최대 2건).
     *
     * MD5 해시 식별자 생성 입력에 leaseType 이 포함되지 않으므로 (설계 섹션 9.1.1),
     * 동일 propertyId 가 PROPERTIES_CHARTER 와 PROPERTIES_MONTHLY 양쪽 테이블에
     * 각각 존재할 수 있다. 양쪽을 모두 조회하여 DELETED 가 아닌 유효 레코드를
     * 전부 배열로 반환한다.
     *
     * COMPLETED 상태는 조회 허용 (설계 섹션 6.4).
     * 양쪽 모두 미존재 또는 DELETED 이면 404 (E4201).
     */
    @GetMapping("/{propertyId}")
    public ResponseEntity<List<PropertyDetailDto>> getPropertyDetail(
            @PathVariable String propertyId) {

        log.info("매물 상세 조회 요청: propertyId={}", propertyId);

        List<PropertyDetailDto> response = propertyQueryService.getPropertyDetail(propertyId);

        log.info("매물 상세 조회 완료: propertyId={}, 조회건수={}",
                propertyId, response.size());

        return ResponseEntity.ok(response);
    }
}
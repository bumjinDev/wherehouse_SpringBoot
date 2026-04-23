package com.wherehouse.PropertyManagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * F004 매물 목록 조회 응답 DTO (설계 명세서 섹션 7.5.2).
 *
 * 페이지네이션 기반 매물 요약 목록과 전체 메타 정보를 포함.
 *
 * 필드 구성 근거:
 *   properties      - 현재 페이지의 PropertySummaryDto 배열
 *   totalElements   - 필터 조건을 만족하는 전체 매물 수
 *   totalPages      - 전체 페이지 수 (ceil(totalElements / size))
 *   currentPage     - 현재 페이지 번호 (0-indexed)
 *   size            - 요청된 페이지 크기
 *
 * totalPages 와 currentPage 분리 노출 근거:
 *   클라이언트가 페이지네이션 UI (예: "1 2 3 ... 10 / 현재 3페이지")를 구성하는 데
 *   두 값이 모두 필요. totalElements 만으로는 클라이언트가 size 로 나누어 계산해야 하나,
 *   서버가 계산한 값을 그대로 노출하면 클라이언트 로직 단순화 및 size 해석 불일치 위험 제거.
 *
 * 임대 유형 미지정 시 병합 처리와의 연결 (섹션 9.4.1):
 *   leaseType 쿼리 파라미터가 미지정되면 전세·월세 두 테이블 각각에 동일 필터를 적용한
 *   결과 집합을 합쳐 페이지네이션. 따라서 properties 배열에는 두 유형의 매물이 혼재 가능하며,
 *   각 매물의 leaseType 필드로 구분 가능하다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyListResponseDto {

    /**
     * 현재 페이지에 포함된 매물 요약 목록.
     */
    private List<PropertySummaryDto> properties;

    /**
     * 필터 조건을 만족하는 전체 매물 수.
     */
    private Long totalElements;

    /**
     * 전체 페이지 수. ceil(totalElements / size) 로 계산.
     */
    private Integer totalPages;

    /**
     * 현재 페이지 번호 (0-indexed).
     */
    private Integer currentPage;

    /**
     * 페이지 크기.
     */
    private Integer size;
}
package com.wherehouse.PropertyManagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * F004 매물 목록 조회 Query Parameter 바인딩 DTO (설계 명세서 섹션 7.5.1, 9.4.1).
 *
 * GET /api/v1/properties?leaseType=...&district=...&... 의 쿼리 문자열을
 * PropertyQueryController 가 @ModelAttribute 로 받아 본 DTO 로 바인딩.
 *
 * @ModelAttribute 바인딩 요구사항:
 *   Spring MVC 가 쿼리 파라미터를 DTO 필드에 매핑하려면 기본 생성자와 Setter 가 필요.
 *   Builder 패턴과 병행 사용이 가능하며 기존 리뷰 도메인의 ReviewListRequestDto 와 동일한 관례.
 *
 * 필드별 검증·기본값 정책 (섹션 7.5.1):
 *   leaseType   : 선택. 미지정 시 전세·월세 두 테이블 통합 조회(섹션 9.4.1)
 *   district    : 선택. 미지정 시 25개 자치구 통합
 *   status      : 선택. 기본 ACTIVE. DELETED 지정 시에도 응답 제외(섹션 6.4 강제 규칙)
 *   dataSource  : 선택. 미지정 시 BATCH/USER/MERGED 전체
 *   keyword     : 선택. 아파트명·지역구명 부분 일치 검색
 *   page        : 선택. 기본 0 (0-indexed)
 *   size        : 선택. 기본 20. 과도한 크기는 DB 부하 유발 → 상한 100
 *   sort        : 선택. 기본 latest
 *
 * 정렬 기준 허용 값 (섹션 7.5.1):
 *   latest     - 최신 갱신 내림차순 (배치 LAST_UPDATED, 사용자 COALESCE(MODIFIED_AT, REGISTERED_AT))
 *   priceDesc  - 가격 내림차순 (전세: 전세금, 월세: 보증금)
 *   priceAsc   - 가격 오름차순
 *   areaDesc   - 전용면적 내림차순
 *   areaAsc    - 전용면적 오름차순
 *
 * 기본값 초기화 방식:
 *   필드 선언 시 default 값을 지정했으나 @ModelAttribute 바인딩 시 쿼리 파라미터가 부재하면
 *   Setter 가 호출되지 않아 default 값이 그대로 유지됨. 쿼리 파라미터가 빈 문자열("")로 전달되는
 *   경우(예: "?status=")는 Setter 가 호출되어 빈 문자열로 덮어써지므로, 이러한 엣지 케이스의
 *   처리는 서비스 계층에서 빈 문자열 → 기본값 정규화로 보완한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyListRequestDto {

    /**
     * 임대 유형 필터. CHARTER/MONTHLY.
     * 미지정 시 두 테이블 통합 조회.
     */
    @Pattern(regexp = "^(CHARTER|MONTHLY)?$",
            message = "임대 유형은 CHARTER 또는 MONTHLY만 허용됩니다")
    private String leaseType;

    /**
     * 지역구명 필터. 예: "강남구". 미지정 시 25개 자치구 통합.
     */
    private String district;

    /**
     * 매물 상태 필터. 기본 ACTIVE.
     * DELETED 지정 시에도 응답에 포함되지 않음(섹션 6.4).
     */
    @Pattern(regexp = "^(ACTIVE|COMPLETED|DELETED)?$",
            message = "상태는 ACTIVE, COMPLETED, DELETED 중 하나여야 합니다")
    private String status = "ACTIVE";

    /**
     * 데이터 출처 필터. BATCH/USER/MERGED. 미지정 시 전체.
     */
    @Pattern(regexp = "^(BATCH|USER|MERGED)?$",
            message = "데이터 출처는 BATCH, USER, MERGED 중 하나여야 합니다")
    private String dataSource;

    /**
     * 검색 키워드. 아파트명 또는 지역구명 부분 일치.
     */
    private String keyword;

    /**
     * 페이지 번호(0-indexed). 기본 0.
     */
    @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다")
    private Integer page = 0;

    /**
     * 페이지 크기. 기본 20, 상한 100.
     */
    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
    private Integer size = 20;

    /**
     * 정렬 기준. 기본 latest.
     */
    @Pattern(regexp = "^(latest|priceDesc|priceAsc|areaDesc|areaAsc)?$",
            message = "정렬 기준이 유효하지 않습니다")
    private String sort = "latest";
}
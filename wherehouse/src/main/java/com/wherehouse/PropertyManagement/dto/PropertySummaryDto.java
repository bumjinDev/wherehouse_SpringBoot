package com.wherehouse.PropertyManagement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * F004 매물 목록의 개별 매물 요약 DTO (설계 명세서 섹션 7.5.2).
 *
 * PropertyListResponseDto.properties 배열의 원소 타입.
 * 목록 화면에 표시될 핵심 정보만 포함하며, 상세 정보는 PropertyDetailDto 에서 확장된다.
 *
 * 파생값 필드 규약:
 *   address      - 시군구명 + umdNm + jibun 조합 (서비스 계층이 구성)
 *   areaInPyeong - excluUseAr 를 평으로 변환 (1평 = 3.305785㎡)
 *   districtName - sggCd 에 대응하는 지역구명 (예: 11680 → "강남구")
 *
 * NULL 허용 필드 규약 (섹션 8.1.3 배치·사용자 매물 컬럼 값 조합):
 *   monthlyRent  - 전세 매물은 null, 월세 매물만 값 보유
 *   registeredAt - 배치 매물은 null, 사용자 매물만 값 보유
 *   배치 매물에서 null 이 될 수 있는 필드는 JSON 응답에서 null 로 직렬화된다.
 *
 * dataSource·status 필드의 String 표현:
 *   Enum 대신 String 사용 근거는 섹션 8.3 "Enum 값 저장 표기의 일관성" 정책.
 *   JSON 직렬화 단계에서 Enum 의 name() 과 String 이 동일하게 표현되며,
 *   클라이언트는 두 방식을 구분할 수 없다. Enum 도입 시점에 DTO 는 그대로 유지 가능.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertySummaryDto {

    /**
     * 매물 식별자. MD5 해시 32자 Hex String.
     */
    private String propertyId;

    /**
     * 임대 유형. CHARTER 또는 MONTHLY.
     */
    private String leaseType;

    /**
     * 아파트·건물명.
     */
    private String aptNm;

    /**
     * 지역구명. 예: "강남구".
     */
    private String districtName;

    /**
     * 전체 주소 (파생값). 시군구명 + 법정동 + 지번 조합.
     */
    private String address;

    /**
     * 층수.
     */
    private Integer floor;

    /**
     * 전용면적(㎡).
     */
    private BigDecimal excluUseAr;

    /**
     * 전용면적(평, 파생값).
     */
    private BigDecimal areaInPyeong;

    /**
     * 전세금(전세) 또는 보증금(월세). 단위: 만원.
     */
    private Integer deposit;

    /**
     * 월세금(만원). 전세 매물은 null.
     */
    private Integer monthlyRent;

    /**
     * 건축연도.
     */
    private Integer buildYear;

    /**
     * 데이터 출처. BATCH/USER/MERGED.
     */
    private String dataSource;

    /**
     * 매물 상태. ACTIVE/COMPLETED. DELETED 는 응답에 포함되지 않음(섹션 6.4).
     */
    private String status;

    /**
     * 사용자 등록 시각. 배치 매물은 null.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime registeredAt;

    /**
     * 최종 갱신 시각.
     * 사용자 매물: COALESCE(MODIFIED_AT, REGISTERED_AT)
     * 배치 매물: LAST_UPDATED
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUpdated;
}
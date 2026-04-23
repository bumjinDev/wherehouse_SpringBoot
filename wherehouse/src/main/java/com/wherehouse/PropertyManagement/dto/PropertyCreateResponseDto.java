package com.wherehouse.PropertyManagement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * F001 매물 등록 응답 DTO (설계 명세서 섹션 7.2.2).
 *
 * HTTP 201 Created 와 함께 반환되는 응답 본문.
 * 신규 등록된 매물의 식별자와 초기 상태 정보를 클라이언트에 제공한다.
 *
 * 필드 값 규칙 (섹션 7.2.2):
 *   dataSource: 항상 "USER" 고정 (사용자 등록 경로이므로)
 *   status:     항상 "ACTIVE" 고정 (F001 매물 등록의 초기 상태, 섹션 6.1)
 *
 * Enum 표현 방식 (섹션 8.3):
 *   dataSource, status는 String 타입으로 직렬화하여 응답 JSON 호환성 확보.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyCreateResponseDto {

    /**
     * 생성된 매물 식별자. MD5 해시 32자 Hex String.
     * 불변 속성 5개(시군구코드·지번·아파트명·층·전용면적) 조합으로 생성(섹션 9.1.1).
     */
    private String propertyId;

    /**
     * 등록된 임대 유형. CHARTER 또는 MONTHLY.
     */
    private String leaseType;

    /**
     * 데이터 출처. F001 응답에서는 항상 "USER".
     */
    private String dataSource;

    /**
     * 매물 상태. F001 응답에서는 항상 "ACTIVE".
     */
    private String status;

    /**
     * 등록자 식별자. 인증 컨텍스트에서 추출된 값(JWT 필터가 주입).
     */
    private String registeredUserId;

    /**
     * 등록 시각. ISO 8601 포맷(yyyy-MM-ddTHH:mm:ss).
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime registeredAt;
}
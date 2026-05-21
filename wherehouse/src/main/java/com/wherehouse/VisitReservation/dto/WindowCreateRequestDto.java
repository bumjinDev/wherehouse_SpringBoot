package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * F001 윈도우 공개 요청 DTO (설계 명세서 섹션 7.1).
 *
 * 1차 계층 유효성 검증 (Bean Validation):
 *   - 필수 필드 존재
 *   - 매물 식별자 32자
 *   - 임대 유형 CHARTER/MONTHLY (Enum 변환 실패 시 메시지 가공은 글로벌 핸들러)
 *   - 슬롯 분할 단위 양수
 *
 * 2차 계층 검증 (서비스):
 *   - end_time &gt; start_time
 *   - start_time 이 현재 시각 이후
 *   - 윈도우 길이가 슬롯 분할 단위의 정수배
 *   - 매물 존재·활성, 등록자 본인, 시간 겹침 없음
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WindowCreateRequestDto {

    /** 대상 매물 식별자. CHAR(32) MD5 해시. */
    @JsonProperty("property_id")
    @NotBlank(message = "매물 식별자는 필수입니다")
    @Size(min = 32, max = 32, message = "매물 식별자는 32자입니다")
    private String propertyId;

    /** 임대 유형. CHARTER 또는 MONTHLY. */
    @JsonProperty("lease_type")
    @NotBlank(message = "임대 유형은 필수입니다")
    @Pattern(regexp = "^(CHARTER|MONTHLY)$", message = "임대 유형은 CHARTER 또는 MONTHLY 입니다")
    private String leaseType;

    /** 윈도우 시작 시각. ISO 8601 로컬 시각 (KST). */
    @JsonProperty("start_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @NotNull(message = "시작 시각은 필수입니다")
    private LocalDateTime startTime;

    /** 윈도우 종료 시각. */
    @JsonProperty("end_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @NotNull(message = "종료 시각은 필수입니다")
    private LocalDateTime endTime;

    /** 슬롯 분할 단위(분). 선택. 기본값 30. */
    @JsonProperty("slot_duration_minutes")
    @Min(value = 1, message = "슬롯 분할 단위는 1분 이상입니다")
    @Max(value = 240, message = "슬롯 분할 단위는 240분 이하입니다")
    private Integer slotDurationMinutes;
}

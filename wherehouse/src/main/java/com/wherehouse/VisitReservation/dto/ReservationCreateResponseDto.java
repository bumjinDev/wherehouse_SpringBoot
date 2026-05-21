package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * F004 슬롯 예약 확정 응답 DTO (설계 명세서 섹션 7.4).
 *
 * 등록자 연락 정보는 본 예약이 확정된 시점에 한 쌍의 당사자 (확정 탐색자) 에게만
 * 공개된다 (섹션 9 기획서 결과 처리).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationCreateResponseDto {

    @JsonProperty("reservation_id")
    private Long reservationId;

    @JsonProperty("slot_id")
    private Long slotId;

    @JsonProperty("property_id")
    private String propertyId;

    @JsonProperty("lease_type")
    private String leaseType;

    @JsonProperty("start_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;

    @JsonProperty("end_time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;

    @JsonProperty("status")
    private String status;

    @JsonProperty("confirmed_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime confirmedAt;

    /** 등록자 연락 정보. 확정 시점에 공개. */
    @JsonProperty("registrant")
    private ContactInfoDto registrant;
}

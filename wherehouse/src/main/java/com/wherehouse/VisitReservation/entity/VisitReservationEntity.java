package com.wherehouse.VisitReservation.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;

/**
 * 방문 예약 JPA Entity — VISIT_RESERVATION 테이블 매핑 (설계 명세서 섹션 4.1.3).
 *
 * 한 행은 한 탐색자가 한 슬롯에 대해 가진 한 예약을 표현한다. 한 슬롯은 예약 한 건보다
 * 오래 존속한다 — 예약이 취소되면 슬롯은 다시 AVAILABLE 이 되어 새 예약을 받을 수
 * 있고, 슬롯에는 시간에 걸쳐 여러 예약 행이 누적될 수 있다. 그러나 그중 STATUS=CONFIRMED
 * 인 행은 어느 시점에나 최대 1 건이다.
 *
 * ────────────────────────────────────────────────────────────
 * STATUS 와 VISIT_RESULT 의 분리 (섹션 4.1.3)
 * ────────────────────────────────────────────────────────────
 * STATUS 는 예약의 생애주기 상태 (CONFIRMED → CANCELLED/INVALIDATED/COMPLETED) 를,
 * VISIT_RESULT 는 종료된 예약에 대한 사후 분류 (VISITED/NO_SHOW/NULL) 를 표현하는
 * 두 개의 독립 차원이다. VISIT_RESULT 는 STATUS=COMPLETED 일 때만 의미를 가지며 그 외
 * 상태에서는 NULL.
 *
 * ────────────────────────────────────────────────────────────
 * 데이터베이스 차원 무결성 백스톱 (섹션 4.1.3) — 핵심
 * ────────────────────────────────────────────────────────────
 * UQ_VISIT_RESERVATION_CONFIRMED_SLOT 부분 유일 인덱스가 SLOT_ID 컬럼에 대해 STATUS=
 * 'CONFIRMED' 인 행에 한정한 유일성을 강제한다. 종료 상태 (CANCELLED/INVALIDATED/
 * COMPLETED) 행은 인덱스 키가 NULL 이 되어 제약에서 빠지므로, 한 슬롯에 종료 예약이
 * 누적되어도 정합성이 유지된다. 본 제약은 동시성 제어 기법과 직교한 마지막 무결성
 * 방어선이며, 어떤 제어 기법을 통과한 INSERT 도 본 제약을 마지막에 한 번 더 통과해야
 * 한다 (섹션 8.4).
 */
@Entity
@Table(name = "VISIT_RESERVATION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class VisitReservationEntity {

    /**
     * 예약 식별자. Oracle 시퀀스 SEQ_VISIT_RESERVATION 로 생성.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_visit_reservation_generator")
    @SequenceGenerator(name = "seq_visit_reservation_generator", sequenceName = "SEQ_VISIT_RESERVATION", allocationSize = 1)
    @Column(name = "RESERVATION_ID", nullable = false)
    private Long reservationId;

    /**
     * 대상 슬롯 식별자. FK → VISIT_SLOT(SLOT_ID).
     */
    @Column(name = "SLOT_ID", nullable = false)
    private Long slotId;

    /**
     * 예약한 탐색자의 식별자. 회원 시스템의 user_id.
     */
    @Column(name = "SEARCHER_USER_ID", length = 100, nullable = false)
    private String searcherUserId;

    /**
     * 예약 상태. DDL DEFAULT 'CONFIRMED'. 상태 머신은 섹션 5.3·5.4.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 15, nullable = false)
    private VisitReservationStatus status;

    /**
     * 확정 시각. KST. INSERT 시점에 설정.
     */
    @Column(name = "CONFIRMED_AT", nullable = false)
    private LocalDateTime confirmedAt;

    /**
     * 취소 시각. STATUS=CANCELLED 시점.
     */
    @Column(name = "CANCELLED_AT")
    private LocalDateTime cancelledAt;

    /**
     * 무효화 시각. STATUS=INVALIDATED 시점 (윈도우 철회 또는 매물 비활성화).
     */
    @Column(name = "INVALIDATED_AT")
    private LocalDateTime invalidatedAt;

    /**
     * 방문 결과. STATUS=COMPLETED 일 때만 의미. 미분류는 NULL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "VISIT_RESULT", length = 10)
    private VisitResult visitResult;

    /**
     * 결과 분류 시각. 등록자가 방문 결과를 분류한 시점.
     */
    @Column(name = "RESULT_CLASSIFIED_AT")
    private LocalDateTime resultClassifiedAt;
}

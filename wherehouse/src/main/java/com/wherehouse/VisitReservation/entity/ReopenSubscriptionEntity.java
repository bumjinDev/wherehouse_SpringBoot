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
 * 재개방 알림 구독 JPA Entity — REOPEN_SUBSCRIPTION 테이블 매핑 (설계 명세서 섹션 4.1.4).
 *
 * 이미 다른 탐색자에게 예약된 슬롯이 취소로 다시 열릴 때 통지를 받기 위한 신청을
 * 표현한다. 구독은 예약이 아니다 — 어떤 슬롯도 보장하지 않고, 우선권을 부여하는
 * 대기열도 아니다. 슬롯이 다시 열리면 모든 구독자가 통지를 받고 예약에서 동등하게
 * 경쟁한다.
 *
 * ────────────────────────────────────────────────────────────
 * 데이터베이스 차원 무결성 백스톱 (섹션 4.1.4)
 * ────────────────────────────────────────────────────────────
 * UQ_REOPEN_SUBSCRIPTION_ACTIVE 부분 유일 인덱스가 (SLOT_ID, SEARCHER_USER_ID) 에
 * 대해 STATUS=ACTIVE 인 행이 동시에 최대 1 건임을 강제한다. 종료된 구독 (CANCELLED/
 * FULFILLED/EXPIRED) 행은 인덱스 키 표현식이 NULL 이 되어 제약에서 빠지므로, 같은
 * 탐색자-슬롯 쌍의 구독 → 해제 → 재구독 → 해제 흐름이 누적되어도 정합성이 유지된다.
 */
@Entity
@Table(name = "REOPEN_SUBSCRIPTION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class ReopenSubscriptionEntity {

    /**
     * 구독 식별자. Oracle 시퀀스 SEQ_REOPEN_SUBSCRIPTION 로 생성.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_reopen_subscription_generator")
    @SequenceGenerator(name = "seq_reopen_subscription_generator", sequenceName = "SEQ_REOPEN_SUBSCRIPTION", allocationSize = 1)
    @Column(name = "SUBSCRIPTION_ID", nullable = false)
    private Long subscriptionId;

    /**
     * 대상 슬롯 식별자. FK → VISIT_SLOT(SLOT_ID).
     */
    @Column(name = "SLOT_ID", nullable = false)
    private Long slotId;

    /**
     * 구독자 식별자. 회원 시스템의 user_id.
     */
    @Column(name = "SEARCHER_USER_ID", length = 100, nullable = false)
    private String searcherUserId;

    /**
     * 구독 상태. DDL DEFAULT 'ACTIVE'.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 15, nullable = false)
    private SubscriptionStatus status;

    /**
     * 구독 시각. KST. INSERT 시점에 설정.
     */
    @Column(name = "SUBSCRIBED_AT", nullable = false)
    private LocalDateTime subscribedAt;

    /**
     * 종료 시각. 종료 상태로 전이된 시점. ACTIVE 동안 NULL.
     */
    @Column(name = "TERMINATED_AT")
    private LocalDateTime terminatedAt;

    /**
     * 종료 사유. SLOT_CLOSED / RESERVED / USER_CANCELLED. ACTIVE 동안 NULL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "TERMINATION_REASON", length = 20)
    private SubscriptionTerminationReason terminationReason;
}

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
 * 방문 예약 알림 JPA Entity — VISIT_NOTIFICATION 테이블 매핑 (설계 명세서 섹션 4.1.5).
 *
 * 방문 예약 과정에서 발생하는 비동기 통지를 저장한다. 한 당사자의 행위가 다른
 * 당사자에게 영향을 줄 때 영향을 받는 당사자에게 전달할 통지가 본 행으로 기록된다.
 * 어떤 상태 전이의 판정에도 사용되지 않는 단방향 출력 기록이다.
 *
 * ────────────────────────────────────────────────────────────
 * 행 생성 규칙 (섹션 4.1.5)
 * ────────────────────────────────────────────────────────────
 * 한 사건이 여러 수신자에게 영향을 주면 수신자마다 한 행이 생성된다. 매물 비활성화
 * 사건의 경우 무효화된 확정 예약 수만큼 행으로 분할되며, 한 탐색자는 같은 매물에
 * 활성 예약을 1 건만 가질 수 있으므로 한 매물 비활성화가 한 탐색자에게 발생시키는
 * PROPERTY_DEACTIVATED 알림은 정확히 1 건이다.
 *
 * ────────────────────────────────────────────────────────────
 * IS_READ 표현 (섹션 4.1.5)
 * ────────────────────────────────────────────────────────────
 * 기존 도메인의 boolean 컬럼 관례를 따라 CHAR(1) 'Y'/'N' 으로 저장한다. 본 Entity 는
 * String 으로 매핑하며, 알림 조회 응답 DTO 가 'Y'/'N' 을 Boolean 으로 변환한다.
 */
@Entity
@Table(name = "VISIT_NOTIFICATION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class VisitNotificationEntity {

    /**
     * 알림 식별자. Oracle 시퀀스 SEQ_VISIT_NOTIFICATION 로 생성.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_visit_notification_generator")
    @SequenceGenerator(name = "seq_visit_notification_generator", sequenceName = "SEQ_VISIT_NOTIFICATION", allocationSize = 1)
    @Column(name = "NOTIFICATION_ID", nullable = false)
    private Long notificationId;

    /**
     * 수신자 식별자. 회원 시스템의 user_id.
     */
    @Column(name = "USER_ID", length = 100, nullable = false)
    private String userId;

    /**
     * 알림 유형.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "NOTIFICATION_TYPE", length = 30, nullable = false)
    private NotificationType notificationType;

    /**
     * 관련 슬롯 식별자. 알림 유형에 따라 NULL 가능.
     */
    @Column(name = "RELATED_SLOT_ID")
    private Long relatedSlotId;

    /**
     * 관련 예약 식별자. 알림 유형에 따라 NULL 가능.
     */
    @Column(name = "RELATED_RESERVATION_ID")
    private Long relatedReservationId;

    /**
     * 관련 매물 식별자. CHAR(32) MD5 해시. 알림 유형에 따라 NULL 가능.
     */
    @Column(name = "RELATED_PROPERTY_ID", length = 32, columnDefinition = "CHAR(32)")
    private String relatedPropertyId;

    /**
     * 알림 메시지. 최대 500자.
     */
    @Column(name = "MESSAGE", length = 500, nullable = false)
    private String message;

    /**
     * 읽음 여부. DDL DEFAULT 'N'. CHECK IN ('Y','N').
     */
    @Column(name = "IS_READ", length = 1, nullable = false)
    private String isRead;

    /**
     * 생성 시각. KST.
     */
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}

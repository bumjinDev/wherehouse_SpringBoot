package com.wherehouse.security.filter.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * IP + userId 복합 밴 엔티티
 *
 * ============================================================================
 * [테이블] BANNED_SUBJECT
 * ============================================================================
 * Rate Limiting에서 반복적으로 429 응답을 받은 대상을 기록한다.
 * 기존 BANNED_IP 테이블이 IP만 추적했던 것과 달리,
 * BANNED_SUBJECT는 IP와 userId를 모두 기록하여 공격자가 IP를 변경해도
 * 동일 userId라면 밴 상태가 유지되도록 한다.
 *
 * 밴 기간은 1시간이며, BANNED_UNTIL 시점이 지나면 자동 해제 대상이다.
 * 관리자가 직접 DELETE 쿼리로 즉시 해제할 수도 있다.
 *
 * [컬럼 설계]
 * - IP_ADDRESS: 밴 시점의 클라이언트 IP. 항상 기록된다.
 * - USER_ID:    JWT에서 추출한 사용자 식별자.
 *               비인증 요청에서 밴이 발생한 경우 NULL이다.
 *               NULL이 아닌 경우, 이 userId로 들어오는 모든 요청이 차단된다.
 *               밴 등록 시 이 userId가 작성한 모든 리뷰가 삭제된다.
 *
 * [Oracle DDL]
 * ============================================================================
 * -- 기존 BANNED_IP 테이블 드롭 (마이그레이션 시)
 * -- DROP TABLE BANNED_IP;
 * -- DROP SEQUENCE BANNED_IP_SEQ;
 *
 * CREATE SEQUENCE BANNED_SUBJECT_SEQ START WITH 1 INCREMENT BY 1;
 *
 * CREATE TABLE BANNED_SUBJECT (
 *     BAN_ID        NUMBER          PRIMARY KEY,
 *     IP_ADDRESS    VARCHAR2(45)    NOT NULL,
 *     USER_ID       VARCHAR2(50),
 *     REASON        VARCHAR2(200),
 *     BANNED_AT     TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
 *     BANNED_UNTIL  TIMESTAMP       NOT NULL
 * );
 *
 * CREATE INDEX IDX_BANNED_SUBJECT_IP ON BANNED_SUBJECT(IP_ADDRESS);
 * CREATE INDEX IDX_BANNED_SUBJECT_USER ON BANNED_SUBJECT(USER_ID);
 * CREATE INDEX IDX_BANNED_SUBJECT_UNTIL ON BANNED_SUBJECT(BANNED_UNTIL);
 * ============================================================================
 */
@Entity
@Table(name = "BANNED_SUBJECT", indexes = {
        @Index(name = "IDX_BANNED_SUBJECT_IP", columnList = "IP_ADDRESS"),
        @Index(name = "IDX_BANNED_SUBJECT_USER", columnList = "USER_ID"),
        @Index(name = "IDX_BANNED_SUBJECT_UNTIL", columnList = "BANNED_UNTIL")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BannedSubject {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "banned_subject_seq_gen")
    @SequenceGenerator(name = "banned_subject_seq_gen", sequenceName = "BANNED_SUBJECT_SEQ", allocationSize = 1)
    @Column(name = "BAN_ID")
    private Long banId;

    @Column(name = "IP_ADDRESS", nullable = false, length = 45)
    private String ipAddress;

    /**
     * 밴 대상 사용자 ID.
     * 비인증 상태에서 밴이 발생한 경우 NULL이다.
     * NULL이 아닌 경우, IP가 달라져도 이 userId로 들어오는 요청은 모두 차단된다.
     */
    @Column(name = "USER_ID", length = 50)
    private String userId;

    @Column(name = "REASON", length = 200)
    private String reason;

    @Column(name = "BANNED_AT", nullable = false, updatable = false)
    private LocalDateTime bannedAt;

    @Column(name = "BANNED_UNTIL", nullable = false)
    private LocalDateTime bannedUntil;

    @Builder
    public BannedSubject(String ipAddress, String userId, String reason,
                         LocalDateTime bannedAt, LocalDateTime bannedUntil) {
        this.ipAddress = ipAddress;
        this.userId = userId;
        this.reason = reason;
        this.bannedAt = bannedAt;
        this.bannedUntil = bannedUntil;
    }
}
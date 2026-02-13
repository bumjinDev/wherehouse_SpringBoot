package com.wherehouse.security.filter.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * IP 밴 엔티티
 *
 * ============================================================================
 * [테이블] BANNED_IP
 * ============================================================================
 * Rate Limiting에서 반복적으로 429 응답을 받은 IP를 기록한다.
 * 밴 기간은 기본 7일이며, BANNED_UNTIL 시점이 지나면 자동 해제 대상이다.
 * 관리자가 직접 DELETE 쿼리로 즉시 해제할 수도 있다.
 *
 * [Oracle DDL]
 * CREATE SEQUENCE BANNED_IP_SEQ START WITH 1 INCREMENT BY 1;
 *
 * CREATE TABLE BANNED_IP (
 *     BAN_ID        NUMBER        PRIMARY KEY,
 *     IP_ADDRESS    VARCHAR2(45)  NOT NULL,
 *     REASON        VARCHAR2(200),
 *     BANNED_AT     TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
 *     BANNED_UNTIL  TIMESTAMP     NOT NULL
 * );
 *
 * CREATE INDEX IDX_BANNED_IP_ADDRESS ON BANNED_IP(IP_ADDRESS);
 * CREATE INDEX IDX_BANNED_IP_UNTIL ON BANNED_IP(BANNED_UNTIL);
 * ============================================================================
 */
@Entity
@Table(name = "BANNED_IP", indexes = {
        @Index(name = "IDX_BANNED_IP_ADDRESS", columnList = "IP_ADDRESS"),
        @Index(name = "IDX_BANNED_IP_UNTIL", columnList = "BANNED_UNTIL")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BannedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "banned_ip_seq_gen")
    @SequenceGenerator(name = "banned_ip_seq_gen", sequenceName = "BANNED_IP_SEQ", allocationSize = 1)
    @Column(name = "BAN_ID")
    private Long banId;

    @Column(name = "IP_ADDRESS", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "REASON", length = 200)
    private String reason;

    @Column(name = "BANNED_AT", nullable = false, updatable = false)
    private LocalDateTime bannedAt;

    @Column(name = "BANNED_UNTIL", nullable = false)
    private LocalDateTime bannedUntil;

    @Builder
    public BannedIp(String ipAddress, String reason, LocalDateTime bannedAt, LocalDateTime bannedUntil) {
        this.ipAddress = ipAddress;
        this.reason = reason;
        this.bannedAt = bannedAt;
        this.bannedUntil = bannedUntil;
    }
}

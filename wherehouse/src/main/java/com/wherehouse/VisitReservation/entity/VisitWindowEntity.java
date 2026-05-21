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
 * 방문 윈도우 JPA Entity — VISIT_WINDOW 테이블 매핑 (설계 명세서 섹션 4.1.1).
 *
 * 방문 예약의 공급 측을 형성한다. 등록자가 "이 시간대에 방문을 받겠다"고 공개하는
 * 행위를 저장하며, 윈도우 자체는 예약 대상이 아니다. 공개된 윈도우는 고정 길이 슬롯
 * (기본 30분) 으로 분할되어 VISIT_SLOT 에 적재된다. 예약 대상은 그 슬롯이다.
 *
 * ────────────────────────────────────────────────────────────
 * 매물 식별 방식 (섹션 4.1.1)
 * ────────────────────────────────────────────────────────────
 * PROPERTY_ID + LEASE_TYPE 쌍이 매물을 유일 식별한다. 같은 MD5 해시 식별자가 전세
 * 테이블과 월세 테이블에 각각 존재할 수 있기 때문이다. 등록자 식별자는 매물 테이블의
 * REGISTERED_USER_ID 를 조인으로 획득하며 본 Entity 에 저장하지 않는다.
 *
 * ────────────────────────────────────────────────────────────
 * 시간대 정책 (섹션 3.4)
 * ────────────────────────────────────────────────────────────
 * 모든 TIMESTAMP 컬럼은 KST 로컬 시각을 저장한다. Oracle TIMESTAMP 타입이 시간대 정보를
 * 갖지 않으므로 LocalDateTime 으로 매핑한다.
 *
 * ────────────────────────────────────────────────────────────
 * DynamicInsert / DynamicUpdate 사용 근거
 * ────────────────────────────────────────────────────────────
 * @DynamicInsert: WITHDRAWN_AT 가 NULL 인 INSERT 에서 본 컬럼을 제외하여 DDL DEFAULT
 *                 동작을 보장하고 쿼리를 간결화한다.
 * @DynamicUpdate: 철회 시 STATUS·WITHDRAWN_AT 두 컬럼만 갱신하는 UPDATE 효율화.
 */
@Entity
@Table(name = "VISIT_WINDOW")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class VisitWindowEntity {

    /**
     * 윈도우 식별자. Oracle 시퀀스 SEQ_VISIT_WINDOW 로 생성.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_visit_window_generator")
    @SequenceGenerator(name = "seq_visit_window_generator", sequenceName = "SEQ_VISIT_WINDOW", allocationSize = 1)
    @Column(name = "WINDOW_ID", nullable = false)
    private Long windowId;

    /**
     * 대상 매물 식별자. CHAR(32) MD5 해시. PROPERTIES_CHARTER/MONTHLY 의 PROPERTY_ID 참조.
     */
    @Column(name = "PROPERTY_ID", length = 32, columnDefinition = "CHAR(32)", nullable = false)
    private String propertyId;

    /**
     * 임대 유형. CHARTER 또는 MONTHLY.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "LEASE_TYPE", length = 10, nullable = false)
    private LeaseType leaseType;

    /**
     * 윈도우 시작 시각. KST.
     */
    @Column(name = "START_TIME", nullable = false)
    private LocalDateTime startTime;

    /**
     * 윈도우 종료 시각. KST. CHECK (END_TIME > START_TIME).
     */
    @Column(name = "END_TIME", nullable = false)
    private LocalDateTime endTime;

    /**
     * 슬롯 분할 단위(분). DDL DEFAULT 30. 기획서 기본 단위.
     */
    @Column(name = "SLOT_DURATION_MINUTES", nullable = false)
    private Integer slotDurationMinutes;

    /**
     * 윈도우 상태. DDL DEFAULT 'ACTIVE'.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 10, nullable = false)
    private VisitWindowStatus status;

    /**
     * 생성 시각. KST.
     */
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 철회 시각. 상태가 WITHDRAWN 일 때만 값이 존재.
     */
    @Column(name = "WITHDRAWN_AT")
    private LocalDateTime withdrawnAt;
}

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
 * 방문 슬롯 JPA Entity — VISIT_SLOT 테이블 매핑 (설계 명세서 섹션 4.1.2).
 *
 * 윈도우를 분할한 고정 길이 슬롯이다. 예약·구독·통지가 모두 슬롯을 기준으로 연결되며,
 * 슬롯의 STATUS 가 예약 가능 여부의 판정 기준이다. 슬롯은 윈도우 공개 (F001) 시점에
 * 일괄 생성된다.
 *
 * ────────────────────────────────────────────────────────────
 * 윈도우 식별 (FK)
 * ────────────────────────────────────────────────────────────
 * WINDOW_ID 는 단순 Long 컬럼으로 보관한다. @ManyToOne 연관을 두지 않는 이유는,
 * 슬롯 예약 처리 흐름이 슬롯 → 윈도우 → 매물 의 명시적 단건 조회로 구성되어 있고
 * (섹션 4.1.6 슬롯 예약 핵심 경로), 묵시적 lazy fetch 가 발생하는 것을 피하기 위함이다.
 *
 * ────────────────────────────────────────────────────────────
 * 데이터베이스 차원 무결성 백스톱 (섹션 4.1.2)
 * ────────────────────────────────────────────────────────────
 * UQ_VISIT_SLOT_WINDOW_START (WINDOW_ID, START_TIME) 유일 제약이 코드 버그나 동시
 * 호출로 같은 윈도우에 같은 시작 시각 슬롯이 중복 INSERT 되는 사고를 데이터베이스에서
 * 차단한다. 위반 시 DataIntegrityViolationException 으로 표출된다.
 */
@Entity
@Table(name = "VISIT_SLOT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicInsert
@DynamicUpdate
public class VisitSlotEntity {

    /**
     * 슬롯 식별자. Oracle 시퀀스 SEQ_VISIT_SLOT 로 생성.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_visit_slot_generator")
    @SequenceGenerator(name = "seq_visit_slot_generator", sequenceName = "SEQ_VISIT_SLOT", allocationSize = 1)
    @Column(name = "SLOT_ID", nullable = false)
    private Long slotId;

    /**
     * 소속 윈도우 식별자. FK → VISIT_WINDOW(WINDOW_ID).
     * 매물·임대 유형·등록자 정보는 윈도우 → 매물 조인으로 얻는다.
     */
    @Column(name = "WINDOW_ID", nullable = false)
    private Long windowId;

    /**
     * 슬롯 시작 시각. KST.
     */
    @Column(name = "START_TIME", nullable = false)
    private LocalDateTime startTime;

    /**
     * 슬롯 종료 시각. KST. CHECK (END_TIME > START_TIME).
     */
    @Column(name = "END_TIME", nullable = false)
    private LocalDateTime endTime;

    /**
     * 슬롯 상태. DDL DEFAULT 'AVAILABLE'. 상태 머신은 섹션 5.1·5.2.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 15, nullable = false)
    private VisitSlotStatus status;

    /**
     * 생성 시각. KST.
     */
    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}

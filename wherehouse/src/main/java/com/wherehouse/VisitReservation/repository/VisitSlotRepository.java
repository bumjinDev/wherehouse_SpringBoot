package com.wherehouse.VisitReservation.repository;

import com.wherehouse.VisitReservation.entity.VisitSlotEntity;
import com.wherehouse.VisitReservation.entity.VisitSlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 방문 슬롯 저장소 (설계 명세서 섹션 4.1.6).
 *
 * 핵심 조회 방향:
 *   - 단건 PK 조회 (예약·취소·종료 처리의 진입점)
 *   - 윈도우별 슬롯 목록 (F002 윈도우 철회, F008 등록자 슬롯 현황)
 *   - 종료 시각이 경과한 슬롯 식별 (F007 슬롯 종료 컴포넌트)
 */
@Repository
public interface VisitSlotRepository extends JpaRepository<VisitSlotEntity, Long> {

    /**
     * 특정 윈도우에 속한 모든 슬롯 (시작 시각 순).
     *
     * UQ_VISIT_SLOT_WINDOW_START 인덱스가 (WINDOW_ID, START_TIME) 순이므로
     * 윈도우 식별자로 좁히고 시작 시각 정렬을 그대로 활용한다.
     */
    List<VisitSlotEntity> findByWindowIdOrderByStartTimeAsc(Long windowId);

    /**
     * 여러 윈도우에 속한 모든 슬롯.
     *
     * F003 슬롯 조회 2단계 (매물 활성 윈도우 식별 후 슬롯 일괄 조회).
     */
    List<VisitSlotEntity> findByWindowIdIn(Collection<Long> windowIds);

    /**
     * 여러 윈도우에 속한 슬롯 중 특정 상태인 것만.
     *
     * F003 슬롯 조회에서 AVAILABLE/RESERVED 만 응답 (CLOSED/WITHDRAWN 제외).
     */
    List<VisitSlotEntity> findByWindowIdInAndStatusIn(
            Collection<Long> windowIds, Collection<VisitSlotStatus> statuses);

    /**
     * 종료 시각이 경과한 활성 슬롯 식별 (F007 슬롯 종료 처리).
     *
     * IX_VISIT_SLOT_STATUS_END (STATUS, END_TIME) 인덱스를 활용한다.
     * 조건: STATUS IN (AVAILABLE, RESERVED) AND END_TIME &lt;= 현재 시각.
     */
    @Query("SELECT s FROM VisitSlotEntity s " +
            "WHERE s.status IN :activeStatuses " +
            "AND s.endTime <= :now")
    List<VisitSlotEntity> findExpiredActiveSlots(
            @Param("activeStatuses") Collection<VisitSlotStatus> activeStatuses,
            @Param("now") LocalDateTime now);

    /**
     * 여러 윈도우의 슬롯 중 특정 상태의 것을 일괄 조회. 윈도우 철회 (F002) 처리에서
     * 윈도우에 속한 AVAILABLE/RESERVED 슬롯을 WITHDRAWN 으로 전이하기 위해 사용.
     */
    List<VisitSlotEntity> findByWindowIdAndStatusIn(
            Long windowId, Collection<VisitSlotStatus> statuses);

    /**
     * 슬롯을 AVAILABLE → RESERVED 로 전이하되, 현재 AVAILABLE 인 경우에만 성공시키는 조건부 UPDATE.
     *
     * F004 시나리오 1 — 동시 예약 경합의 1차 방어선. status=AVAILABLE 조건을 WHERE 에 두어,
     * 서비스의 사전 상태 검증을 통과한 뒤 다른 트랜잭션이 먼저 점유하면 갱신 대상이 0행이 되어
     * 후착을 차단한다. 영향받은 행 수가 0이면 race 에서 패배한 것이므로 호출 측에서 거부한다.
     * 슬롯 행 X-락에서 직렬화되며, 락은 이 UPDATE ~ commit 구간에서만 보유된다.
     *
     * ※ 현재 활성 — F004 시나리오1 ② 조건부 UPDATE(D) 회차 측정용. (baseline 회차에선 미사용)
     */
    @Modifying
    @Query("UPDATE VisitSlotEntity s " +
           "SET s.status = com.wherehouse.VisitReservation.entity.VisitSlotStatus.RESERVED " +
           "WHERE s.slotId = :slotId " +
           "AND s.status = com.wherehouse.VisitReservation.entity.VisitSlotStatus.AVAILABLE")
    int reserveSlotIfAvailable(@Param("slotId") Long slotId);

    /**
     * 슬롯 행을 X-락(PESSIMISTIC_WRITE = SELECT … FOR UPDATE)으로 읽는다.
     *
     * F004 시나리오 1 ③ 비관적 락(B) 측정용. 선착 트랜잭션만 즉시 락을 잡고, 후착은 이 SELECT 에서
     * 락 대기 후 선착의 commit 결과(RESERVED)를 읽는다. 호출 측 2단계 AVAILABLE 검증이 후착을
     * RESERVED 로 보고 거부하므로, 차단은 "읽기(SELECT FOR UPDATE)" 단계에서 일어난다. 락은 이
     * SELECT ~ commit 구간에서 보유된다.
     *
     * ※ 현재 활성 — B 회차 측정용. (D 회차의 reserveSlotIfAvailable 은 B 에선 미사용)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM VisitSlotEntity s WHERE s.slotId = :slotId")
    Optional<VisitSlotEntity> findByIdForUpdate(@Param("slotId") Long slotId);
}

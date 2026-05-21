package com.wherehouse.VisitReservation.repository;

import com.wherehouse.VisitReservation.entity.VisitSlotEntity;
import com.wherehouse.VisitReservation.entity.VisitSlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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
}

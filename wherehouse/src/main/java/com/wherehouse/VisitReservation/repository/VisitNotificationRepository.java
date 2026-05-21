package com.wherehouse.VisitReservation.repository;

import com.wherehouse.VisitReservation.entity.VisitNotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 방문 예약 알림 저장소 (설계 명세서 섹션 4.1.6 IX_VISIT_NOTIFICATION_USER, 7.12 페이징).
 *
 * 핵심 조회 방향:
 *   - 한 이용자의 알림을 미읽음 우선·최신순으로 조회 (페이징 지원)
 *   - 알림 식별자 기반 단건 조회 (읽음 처리, 본인 확인)
 */
@Repository
public interface VisitNotificationRepository extends JpaRepository<VisitNotificationEntity, Long> {

    /**
     * 한 이용자의 알림을 (미읽음 우선, 최신 순) 으로 페이징 조회.
     *
     * 본 페이지 이전 (notificationId &lt; before) 의 알림을 반환한다. 첫 페이지 요청 시에는
     * before 에 Long.MAX_VALUE 를 전달하면 모든 알림이 대상이 된다.
     *
     * IS_READ 'N' 이 'Y' 보다 정렬에서 앞서야 미읽음이 우선 표시되며, 동일 IS_READ 안에서는
     * CREATED_AT DESC, 같은 시각이면 NOTIFICATION_ID DESC 로 안정 정렬한다.
     */
    @Query("SELECT n FROM VisitNotificationEntity n " +
            "WHERE n.userId = :userId " +
            "AND n.notificationId < :before " +
            "ORDER BY n.isRead ASC, n.createdAt DESC, n.notificationId DESC")
    List<VisitNotificationEntity> findByUserIdPaged(
            @Param("userId") String userId,
            @Param("before") Long before,
            Pageable pageable);

    /**
     * 한 이용자의 미읽음 알림만 페이징 조회.
     *
     * unread_only=true 인 호출에서 사용. 정렬 기준은 미읽음 우선 정렬이 자명하므로
     * 최신 순으로 단순화.
     */
    @Query("SELECT n FROM VisitNotificationEntity n " +
            "WHERE n.userId = :userId " +
            "AND n.isRead = 'N' " +
            "AND n.notificationId < :before " +
            "ORDER BY n.createdAt DESC, n.notificationId DESC")
    List<VisitNotificationEntity> findUnreadByUserIdPaged(
            @Param("userId") String userId,
            @Param("before") Long before,
            Pageable pageable);
}

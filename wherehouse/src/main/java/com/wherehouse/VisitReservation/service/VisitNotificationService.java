package com.wherehouse.VisitReservation.service;

import com.wherehouse.VisitReservation.entity.NotificationType;
import com.wherehouse.VisitReservation.entity.VisitNotificationEntity;
import com.wherehouse.VisitReservation.repository.VisitNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 방문 예약 알림 서비스 (설계 명세서 섹션 2.5, 4.1.5).
 *
 * 알림 생성 책임만 본 서비스에 집중한다. 알림 조회·읽음 처리는 본인 자원 조회의
 * 일부로 {@code VisitReservationQueryService} 가 담당한다.
 *
 * 본 서비스는 다른 쓰기 서비스 (윈도우 철회, 슬롯 예약, 예약 취소, 매물 비활성화 연동,
 * 슬롯 종료) 가 자신의 트랜잭션 안에서 호출하여 알림 INSERT 를 함께 묶는다. 즉,
 * 본 메서드는 {@code REQUIRED} 전파로 호출 측 트랜잭션에 참여하여, 메인 상태 전이와
 * 알림 생성이 한 트랜잭션 안에서 커밋되거나 함께 롤백되도록 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitNotificationService {

    private final VisitNotificationRepository notificationRepository;

    /**
     * 알림 1 건 생성. 호출 측 트랜잭션에 참여한다.
     *
     * RELATED 컬럼 (slotId / reservationId / propertyId) 은 알림 유형에 따라 일부가
     * NULL 일 수 있다. 섹션 4.1.5 의 행 생성 규칙을 따른다.
     */
    @Transactional
    public VisitNotificationEntity notify(String userId,
                                          NotificationType type,
                                          Long relatedSlotId,
                                          Long relatedReservationId,
                                          String relatedPropertyId,
                                          String message) {
        VisitNotificationEntity notification = VisitNotificationEntity.builder()
                .userId(userId)
                .notificationType(type)
                .relatedSlotId(relatedSlotId)
                .relatedReservationId(relatedReservationId)
                .relatedPropertyId(relatedPropertyId)
                .message(message)
                .isRead("N")
                .createdAt(LocalDateTime.now())
                .build();

        VisitNotificationEntity saved = notificationRepository.save(notification);
        log.info("[VISIT_NOTIFY] type={}, userId={}, notificationId={}",
                type, userId, saved.getNotificationId());
        return saved;
    }
}

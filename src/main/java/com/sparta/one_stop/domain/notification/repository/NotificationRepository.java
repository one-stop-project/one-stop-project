package com.sparta.one_stop.domain.notification.repository;

import com.sparta.one_stop.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Consumer 멱등성 체크
    // eventId로 이미 알림이 생성되었는지 확인한다
    boolean existsByEventId(String eventId);

}

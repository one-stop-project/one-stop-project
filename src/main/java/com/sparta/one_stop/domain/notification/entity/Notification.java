package com.sparta.one_stop.domain.notification.entity;

import com.sparta.one_stop.global.entity.BaseEntity;
import com.sparta.one_stop.global.enums.notification.NotificationType;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "notification",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_notification_event_id",
            columnNames = "event_id"
        )
    },
    indexes = {
        @Index(
            name = "idx_notification_user",
            columnList = "user_id, created_at"
        )
    }
)
public class Notification extends BaseEntity {

    // 알림 ID
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    // 알림 대상 사용자
    // User 엔티티와 FK 대신 Long 필드로 관리한다
    // Kafka Consumer에서 payload의 userId로 직접 생성하므로 User 엔티티 조회가 불필요하다
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 멱등성 보장용 고유 이벤트 ID
    // Outbox의 eventId와 동일한 값을 저장한다
    // UNIQUE 제약으로 동일 이벤트의 중복 알림 생성을 방지한다
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    // 알림 유형
    // PAYMENT_APPROVED 등
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    // 알림 제목
    // 예: "결제 완료"
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    // 알림 내용
    // 예: "주문 #13 결제가 완료되었습니다. (결제 금액: 192,000원)"
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    // 읽음 여부
    // 생성 시 false, 향후 읽음 처리 API에서 true로 변경
    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    // == 생성자 ==
    private Notification(
        Long userId,
        String eventId,
        NotificationType type,
        String title,
        String message
    ) {
        validateRequired(userId, eventId, type, title, message);

        this.userId = userId;
        this.eventId = eventId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.isRead = false;
    }

    // == 정적 생성 메서드 ==

    // 알림 생성
    // Kafka Consumer가 이벤트를 수신한 뒤 호출한다
    public static Notification create(
        Long userId,
        String eventId,
        NotificationType type,
        String title,
        String message
    ) {
        return new Notification(userId, eventId, type, title, message);
    }

    // == 검증 메서드 ==

    private void validateRequired(
        Long userId,
        String eventId,
        NotificationType type,
        String title,
        String message
    ) {
        if (userId == null) {
            throw new CustomException(ErrorCode.NOTIFICATION_020);
        }

        if (eventId == null || eventId.isBlank()) {
            throw new CustomException(ErrorCode.NOTIFICATION_021);
        }

        if (type == null) {
            throw new CustomException(ErrorCode.NOTIFICATION_022);
        }

        if (title == null || title.isBlank()) {
            throw new CustomException(ErrorCode.NOTIFICATION_023);
        }

        if (message == null || message.isBlank()) {
            throw new CustomException(ErrorCode.NOTIFICATION_024);
        }
    }

    // == 비즈니스 메서드 ==

    // 읽음 처리
    // 향후 알림 읽음 처리 API 확장 시 사용
    public void markRead() {
        this.isRead = true;
    }

}

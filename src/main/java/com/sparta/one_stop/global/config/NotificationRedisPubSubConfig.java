package com.sparta.one_stop.global.config;

import com.sparta.one_stop.domain.notification.publisher.NotificationRedisPublisher;
import com.sparta.one_stop.domain.notification.subscriber.NotificationRedisSubscriber;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@RequiredArgsConstructor
public class NotificationRedisPubSubConfig {

    /**
     * SSE 알림 Redis Pub/Sub 채널 Topic
     *
     * - Publisher와 Subscriber가 동일한 채널명을 사용해야 한다.
     * - 채널명은 NotificationRedisPublisher의 상수를 재사용한다.
     */
    @Bean
    public ChannelTopic notificationSseTopic() {
        return new ChannelTopic(
            NotificationRedisPublisher.NOTIFICATION_SSE_CHANNEL
        );
    }

    /**
     * Redis Pub/Sub 메시지를 NotificationRedisSubscriber.onMessage(String payload)로 연결하는 어댑터
     *
     * - Redis Pub/Sub 메시지는 JSON 문자열로 발행된다.
     * - StringRedisSerializer를 지정해 Redis Message body를 String으로 변환한다.
     * - onMessage 메서드에서 JSON 역직렬화 후 SSE 전송을 처리한다.
     */
    @Bean
    public MessageListenerAdapter notificationMessageListenerAdapter(
        NotificationRedisSubscriber notificationRedisSubscriber
    ) {
        MessageListenerAdapter adapter = new MessageListenerAdapter(
            notificationRedisSubscriber,
            "onMessage"
        );

        adapter.setSerializer(new StringRedisSerializer());

        return adapter;
    }

    /**
     * Redis Pub/Sub Listener Container
     *
     * - Redis 채널을 구독하고 메시지가 들어오면 MessageListenerAdapter로 전달한다.
     * - 다중 서버 환경에서는 각 서버 인스턴스가 동일한 채널을 구독한다.
     * - 각 서버는 메시지를 수신한 뒤 자기 메모리에 해당 userId의 SseEmitter가 있는 경우에만 SSE 전송한다.
     */
    @Bean
    public RedisMessageListenerContainer notificationRedisMessageListenerContainer(
        RedisConnectionFactory redisConnectionFactory,
        MessageListenerAdapter notificationMessageListenerAdapter,
        ChannelTopic notificationSseTopic
    ) {
        RedisMessageListenerContainer container =
            new RedisMessageListenerContainer();

        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(
            notificationMessageListenerAdapter,
            notificationSseTopic
        );

        return container;
    }

}

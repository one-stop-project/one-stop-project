package com.sparta.one_stop.domain.ai.service;

import com.sparta.one_stop.domain.ai.dto.ReviewCategoryType;
import com.sparta.one_stop.domain.ai.dto.ReviewSummary;
import com.sparta.one_stop.global.ai.logging.AiTokenLogger;
import com.sparta.one_stop.global.ai.prompt.AiPromptProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewSummaryServiceTest {

    // ChatModel 을 mock 하고 실제 ChatClient 를 생성하면 BeanOutputConverter 흐름을 정확히 검증 가능합니다.
    // 하지만 ChatModel 을 직접 mock 하면 Spring AI 내부 Prompt 객체 생성 로직에 의존하게 되므로,
    // 여기서는 ChatClient 를 deep stub 으로 mock 해 단위 테스트 독립성을 유지합니다.
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private AiPromptProperties promptProperties;

    @Mock
    private AiTokenLogger tokenLogger;

    private ReviewSummaryService service;

    private static final String SAMPLE_REVIEWS = "착용감이 좋아요.\n사이즈가 정확해요.";

    // AI 가 반환하는 JSON – BeanOutputConverter 가 ReviewSummary 로 역직렬화합니다
    private static final String VALID_JSON = """
            {"pros":["착용감 좋음","사이즈 정확"],"cons":[],"keywords":["착용감","사이즈"],"sentiment":"POSITIVE"}
            """;

    @BeforeEach
    void setUp() {
        service = new ReviewSummaryService(chatClient, promptProperties, tokenLogger);
        ReflectionTestUtils.setField(service, "modelName", "gpt-4o-mini");
    }

    @Nested
    @DisplayName("summarize() 성공 케이스")
    class SummarizeSuccess {

        @Test
        @DisplayName("의류 카테고리 리뷰를 요약하면 ReviewSummary 를 반환한다")
        void clothing_summarize_returns_summary() {
            // given
            given(promptProperties.clothing()).willReturn("의류 리뷰 분석: {reviews}");

            ChatResponse mockResponse = buildChatResponse(VALID_JSON);
            given(chatClient.prompt()
                    .user(any(String.class))
                    .call()
                    .chatResponse())
                    .willReturn(mockResponse);

            // when
            ReviewSummary result = service.summarize(ReviewCategoryType.CLOTHING, SAMPLE_REVIEWS);

            // then
            assertThat(result).isNotNull();
            assertThat(result.pros()).contains("착용감 좋음", "사이즈 정확");
            assertThat(result.cons()).isEmpty();
            assertThat(result.keywords()).contains("착용감", "사이즈");
            assertThat(result.sentiment()).isEqualTo("POSITIVE");
        }

        @Test
        @DisplayName("전자제품 카테고리 리뷰를 요약하면 ReviewSummary 를 반환한다")
        void electronics_summarize_returns_summary() {
            // given
            String electronicsJson = """
                    {"pros":["배터리 오래감"],"cons":["발열 있음"],"keywords":["배터리","발열"],"sentiment":"NEUTRAL"}
                    """;
            given(promptProperties.electronics()).willReturn("전자제품 리뷰 분석: {reviews}");

            ChatResponse mockResponse = buildChatResponse(electronicsJson);
            given(chatClient.prompt()
                    .user(any(String.class))
                    .call()
                    .chatResponse())
                    .willReturn(mockResponse);

            // when
            ReviewSummary result = service.summarize(ReviewCategoryType.ELECTRONICS, "배터리 오래가요. 약간 뜨거워요.");

            // then
            assertThat(result.sentiment()).isEqualTo("NEUTRAL");
            assertThat(result.pros()).contains("배터리 오래감");
            assertThat(result.cons()).contains("발열 있음");
        }

        @Test
        @DisplayName("토큰 로거가 성공 응답으로 호출된다")
        void token_logger_called_on_success() {
            // given
            given(promptProperties.clothing()).willReturn("의류 리뷰 분석: {reviews}");
            given(chatClient.prompt()
                    .user(any(String.class))
                    .call()
                    .chatResponse())
                    .willReturn(buildChatResponse(VALID_JSON));

            // when
            service.summarize(ReviewCategoryType.CLOTHING, SAMPLE_REVIEWS);

            // then
            verify(tokenLogger).logSuccess(any(), any(ChatResponse.class), any(Long.class));
        }
    }

    @Nested
    @DisplayName("summarize() 실패 케이스")
    class SummarizeFailure {

        @Test
        @DisplayName("ChatClient 예외 발생 시 실패 로거가 호출되고 예외를 다시 던진다")
        void chat_client_exception_logs_and_rethrows() {
            // given
            given(promptProperties.clothing()).willReturn("의류 리뷰 분석: {reviews}");
            given(chatClient.prompt()
                    .user(any(String.class))
                    .call()
                    .chatResponse())
                    .willThrow(new RuntimeException("AI 서버 응답 없음"));

            // when & then
            assertThatThrownBy(() -> service.summarize(ReviewCategoryType.CLOTHING, SAMPLE_REVIEWS))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("AI 서버 응답 없음");

            verify(tokenLogger).logFailure(any(), any(Long.class), any(), any(Throwable.class));
        }
    }

    /** ChatResponse 생성 헬퍼 – AssistantMessage + Generation 으로 구성합니다. */
    private ChatResponse buildChatResponse(String content) {
        AssistantMessage message = new AssistantMessage(content);
        Generation generation = new Generation(message);
        return new ChatResponse(List.of(generation));
    }
}

package com.sparta.one_stop.dummy.naver;

import com.sparta.one_stop.dummy.naver.dto.NaverShopItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("NaverShopClient")
class NaverShopClientTest {

    private static final String BASE_URL = "https://openapi.naver.com/v1/search/shop.json";
    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";

    private MockRestServiceServer server;
    private NaverShopClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("X-Naver-Client-Id", CLIENT_ID)
                .defaultHeader("X-Naver-Client-Secret", CLIENT_SECRET);

        server = MockRestServiceServer.bindTo(builder).build();
        client = new NaverShopClient(builder.build());
    }

    @Test
    @DisplayName("search_success")
    void searchSuccess() throws IOException {
        String body = new ClassPathResource("naver/shop-sample.json")
                .getContentAsString(StandardCharsets.UTF_8);

        server.expect(once(), requestTo(BASE_URL + "?query=%EA%B0%A4%EB%9F%AD%EC%8B%9C&display=3&start=1&sort=sim"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Naver-Client-Id", CLIENT_ID))
                .andExpect(header("X-Naver-Client-Secret", CLIENT_SECRET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<NaverShopItem> items = client.search("갤럭시", 3, 1, "sim");

        assertThat(items).hasSize(3);
        assertThat(items.get(0).title()).doesNotContain("<b>", "</b>");
        assertThat(items.get(0).title()).contains("&");
        assertThat(items.get(1).title()).contains("\"화이트\"");
        assertThat(items.get(2).title()).contains("'실버'");
        assertThat(Long.parseLong(items.get(0).lprice())).isPositive();
        assertThat(items.get(0).hprice()).isEqualTo("300000");
        assertThat(items.get(0).productId()).isNotBlank();
        assertThat(items.get(0).productType()).isEqualTo("1");
        assertThat(items.get(0).category1()).isNotBlank();

        server.verify();
    }

    @Test
    @DisplayName("error_400")
    void error400() {
        server.expect(once(), requestTo(BASE_URL + "?query=%EA%B0%A4%EB%9F%AD%EC%8B%9C&display=3&start=1&sort=sim"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":\"SE01\",\"errorMessage\":\"Bad Request\"}"));

        assertThatThrownBy(() -> client.search("갤럭시", 3, 1, "sim"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("400");

        server.verify();
    }

    @Test
    @DisplayName("error_403")
    void error403() {
        server.expect(once(), requestTo(BASE_URL + "?query=%EA%B0%A4%EB%9F%AD%EC%8B%9C&display=3&start=1&sort=sim"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withForbiddenRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":\"SE03\",\"errorMessage\":\"Forbidden\"}"));

        assertThatThrownBy(() -> client.search("갤럭시", 3, 1, "sim"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("403");

        server.verify();
    }

    @Test
    @DisplayName("blank_query")
    void blankQuery() {
        assertThatThrownBy(() -> client.search(" ", 3, 1, "sim"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("display_over_100")
    void displayOver100() {
        assertThatThrownBy(() -> client.search("갤럭시", 101, 1, "sim"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start_over_1000")
    void startOver1000() {
        assertThatThrownBy(() -> client.search("갤럭시", 3, 1001, "sim"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("boundary_values_do_not_throw")
    void boundaryValuesDoNotThrow() {
        String body = """
                {
                  "total": 1,
                  "start": 1,
                  "display": 1,
                  "items": [
                    {
                      "title": "boundary",
                      "link": "https://shopping.naver.com/catalog/100000004",
                      "image": "https://shopping-phinf.pstatic.net/main_1000000/100000004.jpg",
                      "lprice": "1000",
                      "hprice": "300000",
                      "brand": "brand",
                      "maker": "maker",
                      "category1": "category",
                      "category2": "",
                      "category3": "",
                      "category4": "",
                      "productId": "100000004",
                      "productType": "1",
                      "mallName": "mall"
                    }
                  ]
                }
                """;

        expectSuccessfulSearch("boundary", 1, 1, body);
        expectSuccessfulSearch("boundary", 100, 1, body);
        expectSuccessfulSearch("boundary", 1, 1_000, body);
        expectSuccessfulSearch("boundary", 100, 1_000, body);

        assertThatCode(() -> client.search("boundary", 1, 1, "sim"))
                .doesNotThrowAnyException();
        assertThatCode(() -> client.search("boundary", 100, 1, "sim"))
                .doesNotThrowAnyException();
        assertThatCode(() -> client.search("boundary", 1, 1_000, "sim"))
                .doesNotThrowAnyException();
        assertThatCode(() -> client.search("boundary", 100, 1_000, "sim"))
                .doesNotThrowAnyException();

        server.verify();
    }

    private void expectSuccessfulSearch(String query, int display, int start, String body) {
        server.expect(once(), requestTo(BASE_URL
                        + "?query=" + query
                        + "&display=" + display
                        + "&start=" + start
                        + "&sort=sim"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}

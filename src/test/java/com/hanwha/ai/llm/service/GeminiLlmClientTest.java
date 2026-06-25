package com.hanwha.ai.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.config.GeminiProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiLlmClientTest {
    @Test
    void generateCallsGeminiGenerateContentApi() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GeminiLlmClient client = new GeminiLlmClient(
                new GeminiProperties("test-gemini-key", "gemini-2.5-flash", "https://generativelanguage.googleapis.com"),
                restClientBuilder
        );

        server.expect(once(), requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(POST))
                .andExpect(header("x-goog-api-key", "test-gemini-key"))
                .andExpect(header(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("Create user controller")))
                .andExpect(jsonPath("$.contents[0].parts[0].text", containsString("standard controller pattern")))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  {
                                    "text": "@RestController\\npublic class UserController {}"
                                  }
                                ]
                              },
                              "finishReason": "STOP"
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = client.generate(new LlmGenerateRequest(
                "Create user controller",
                "standard controller pattern"
        ));

        assertThat(client.provider()).isEqualTo(LlmProvider.GEMINI);
        assertThat(response.content()).contains("@RestController");
        server.verify();
    }
    @Test
    void generateIncludesGeminiErrorCodeAndMessage() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        GeminiLlmClient client = new GeminiLlmClient(
                new GeminiProperties("test-gemini-key", "gemini-2.5-flash", "https://generativelanguage.googleapis.com"),
                restClientBuilder
        );

        server.expect(once(), requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": {
                                    "code": 503,
                                    "message": "This model is currently experiencing high demand. Spikes in demand are usually temporary. Please try again later.",
                                    "status": "UNAVAILABLE"
                                  }
                                }
                                """));

        assertThatThrownBy(() -> client.generate(new LlmGenerateRequest("hello", "context")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LLM response code=503")
                .hasMessageContaining("This model is currently experiencing high demand");

        server.verify();
    }
}

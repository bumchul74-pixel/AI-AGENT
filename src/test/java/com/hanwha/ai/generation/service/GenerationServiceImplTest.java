package com.hanwha.ai.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hanwha.ai.generation.dto.GenerationRequest;
import com.hanwha.ai.generation.repository.GenerationRepository;
import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.llm.config.LlmProperties;
import com.hanwha.ai.llm.config.OpenAiProperties;
import com.hanwha.ai.llm.domain.LlmProvider;
import com.hanwha.ai.llm.dto.LlmGenerateRequest;
import com.hanwha.ai.llm.dto.LlmGenerateResponse;
import com.hanwha.ai.llm.service.LlmClient;
import com.hanwha.ai.llm.service.LlmClientFactory;
import com.hanwha.ai.llm.service.OpenAiLlmClient;
import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import com.hanwha.ai.rag.service.PythonRagClient;
import com.hanwha.ai.rag.service.RagClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GenerationServiceImplTest {
    @Test
    void generateCallsPythonRagServerThenOpenAiLlm() {
        RestClient.Builder ragRestClientBuilder = RestClient.builder();
        MockRestServiceServer ragServer = MockRestServiceServer.bindTo(ragRestClientBuilder).build();
        RestClient.Builder llmRestClientBuilder = RestClient.builder();
        MockRestServiceServer llmServer = MockRestServiceServer.bindTo(llmRestClientBuilder).build();

        RagProperties ragProperties = new RagProperties("http://localhost:8000", "/api/search", 2);
        RagClient ragClient = new PythonRagClient(ragRestClientBuilder, ragProperties);
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(new OpenAiLlmClient(
                        new OpenAiProperties("test-api-key", "gpt-test", "https://api.openai.test/v1", null),
                        llmRestClientBuilder
                ))
        );
        GenerationService service = new GenerationServiceImpl(
                ragClient,
                llmClientFactory,
                new GenerationRepository(),
                ragProperties
        );

        ragServer.expect(once(), requestTo("http://localhost:8000/api/search"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.query", containsString("Controller")))
                .andExpect(jsonPath("$.query", containsString("Create user controller")))
                .andExpect(jsonPath("$.top_k", is(2)))
                .andRespond(withSuccess("""
                        {
                          "documents": [
                            "[source: standard-source/UserController.java]\\n@RestController public class UserController {}",
                            "[source: standard-source/UserService.java]\\npublic interface UserService {}"
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        llmServer.expect(once(), requestTo("https://api.openai.test/v1/responses"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-api-key"))
                .andExpect(jsonPath("$.model", is("gpt-test")))
                .andExpect(jsonPath("$.input", containsString("Create user controller")))
                .andExpect(jsonPath("$.input", containsString("standard-source/UserController.java")))
                .andRespond(withSuccess("""
                        {
                          "output_text": "@RestController\\npublic class UserController {}"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = service.generate(new GenerationRequest("Controller", "Create user controller"));

        assertThat(response.targetType()).isEqualTo("Controller");
        assertThat(response.generatedCode()).contains("@RestController");
        assertThat(response.ragDocuments()).containsExactly(
                "[source: standard-source/UserController.java]\n@RestController public class UserController {}",
                "[source: standard-source/UserService.java]\npublic interface UserService {}"
        );
        ragServer.verify();
        llmServer.verify();
    }

    @Test
    void generateUsesRagContextAndConfiguredLlmProvider() {
        AtomicReference<String> ragQuery = new AtomicReference<>();
        RagClient ragClient = request -> {
            ragQuery.set(request.query());
            return new RagSearchResponse(List.of("sample controller pattern"));
        };
        AtomicReference<LlmGenerateRequest> llmRequest = new AtomicReference<>();
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(fakeLlmClient(llmRequest))
        );
        GenerationService service = new GenerationServiceImpl(
                ragClient,
                llmClientFactory,
                new GenerationRepository(),
                new RagProperties("http://localhost:8000", "/api/search", 5)
        );

        var response = service.generate(new GenerationRequest("Controller", "Create user controller"));

        assertThat(response.targetType()).isEqualTo("Controller");
        assertThat(response.generatedCode()).contains("sample controller pattern");
        assertThat(response.ragDocuments()).containsExactly("sample controller pattern");
        assertThat(ragQuery.get()).contains("Controller", "Create user controller");
        assertThat(llmRequest.get().prompt()).contains(
                "First derive the source structure from the retrieved RAG source",
                "sample controller pattern"
        );
    }

    @Test
    void generateStopsWhenRagResultIsEmpty() {
        RagClient ragClient = request -> RagSearchResponse.empty();
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(fakeLlmClient(new AtomicReference<>()))
        );
        GenerationService service = new GenerationServiceImpl(
                ragClient,
                llmClientFactory,
                new GenerationRepository(),
                new RagProperties("http://localhost:8000", "/api/search", 5)
        );

        assertThatThrownBy(() -> service.generate(new GenerationRequest("Controller", "Create user controller")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("RAG search result is required before source generation.");
    }

    private LlmClient fakeLlmClient(AtomicReference<LlmGenerateRequest> llmRequest) {
        return new LlmClient() {
            @Override
            public LlmProvider provider() {
                return LlmProvider.OPENAI;
            }

            @Override
            public LlmGenerateResponse generate(LlmGenerateRequest request) {
                llmRequest.set(request);
                return new LlmGenerateResponse(request.context());
            }
        };
    }
}

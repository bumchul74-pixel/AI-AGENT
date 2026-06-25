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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GenerationServiceImplTest {
    private static final String PROJECT_PATH = "D:\\workspace\\management";

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
                ragProperties,
                fakeProjectStructureAnalyzer()
        );

        ragServer.expect(once(), requestTo("http://localhost:8000/api/search"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.query", containsString("Controller")))
                .andExpect(jsonPath("$.query", containsString("selected target types")))
                .andExpect(jsonPath("$.query", containsString("Create user controller")))
                .andExpect(jsonPath("$.query", containsString(PROJECT_PATH)))
                .andExpect(jsonPath("$.query", containsString("MCP project structure analysis")))
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
                .andExpect(jsonPath("$.input", containsString("Generate source only for the selected target types: Controller")))
                .andExpect(jsonPath("$.input", containsString("Return only one Java source code output")))
                .andExpect(jsonPath("$.input", containsString("Selected project full path")))
                .andExpect(jsonPath("$.input", containsString(PROJECT_PATH)))
                .andExpect(jsonPath("$.input", containsString("MCP analyzed project structure")))
                .andExpect(jsonPath("$.input", containsString("Base package: com.hanwha.ai")))
                .andExpect(jsonPath("$.input", containsString("standard-source/UserController.java")))
                .andRespond(withSuccess("""
                        {
                          "output_text": "@RestController\\npublic class UserController {}"
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = service.generate(new GenerationRequest(List.of("Controller"), "Create user controller", PROJECT_PATH));

        assertThat(response.targetType()).isEqualTo("Controller");
        assertThat(response.targetTypes()).containsExactly("Controller");
        assertThat(response.generatedCode()).contains("@RestController");
        assertThat(response.projectStructure()).contains("MCP project structure analysis", PROJECT_PATH, "Spring Boot: detected");
        assertThat(response.ragDocuments()).containsExactly(
                "[source: standard-source/UserController.java]\n@RestController public class UserController {}",
                "[source: standard-source/UserService.java]\npublic interface UserService {}"
        );
        ragServer.verify();
        llmServer.verify();
    }

    @Test
    void generateUsesOnlySelectedTargetTypesInPromptAndSearchQuery() {
        AtomicReference<String> ragQuery = new AtomicReference<>();
        RagClient ragClient = request -> {
            ragQuery.set(request.query());
            return new RagSearchResponse(List.of("sample controller and dto pattern"));
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
                new RagProperties("http://localhost:8000", "/api/search", 5),
                fakeProjectStructureAnalyzer()
        );

        var response = service.generate(new GenerationRequest(
                List.of("Controller", "DTO"),
                "Create user API",
                PROJECT_PATH
        ));

        assertThat(response.targetType()).isEqualTo("Controller, DTO");
        assertThat(response.targetTypes()).containsExactly("Controller", "DTO");
        assertThat(response.generatedCode()).contains("sample controller and dto pattern", "Base package: com.hanwha.ai");
        assertThat(response.ragDocuments()).containsExactly("sample controller and dto pattern");
        assertThat(response.projectStructure()).contains("MCP project structure analysis", PROJECT_PATH, "Spring Boot: detected");
        assertThat(ragQuery.get()).contains(
                "Controller, DTO",
                "selected target types only",
                PROJECT_PATH,
                "MCP project structure analysis"
        );
        assertThat(llmRequest.get().prompt()).contains(
                "Selected generation target types:",
                "Controller, DTO",
                "Generate source only for the selected target types: Controller, DTO",
                "do not include sections for unselected target types",
                "Use the MCP analyzed project structure and selected project full path"
        );
        assertThat(llmRequest.get().context()).contains(
                "Selected project full path",
                PROJECT_PATH,
                "MCP analyzed project structure",
                "Retrieved RAG source",
                "sample controller and dto pattern"
        );
    }

    @Test
    void generateStopsWhenTargetTypesAreMissing() {
        AtomicBoolean ragCalled = new AtomicBoolean(false);
        RagClient ragClient = request -> {
            ragCalled.set(true);
            return new RagSearchResponse(List.of("sample controller pattern"));
        };
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(fakeLlmClient(new AtomicReference<>()))
        );
        GenerationService service = new GenerationServiceImpl(
                ragClient,
                llmClientFactory,
                new GenerationRepository(),
                new RagProperties("http://localhost:8000", "/api/search", 5),
                fakeProjectStructureAnalyzer()
        );

        assertThatThrownBy(() -> service.generate(new GenerationRequest(List.of(), "Create user controller", PROJECT_PATH)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("targetTypes, prompt, and projectStructure are required.");
        assertThat(ragCalled.get()).isFalse();
    }

    @Test
    void generateStopsWhenProjectStructureIsMissing() {
        AtomicBoolean ragCalled = new AtomicBoolean(false);
        RagClient ragClient = request -> {
            ragCalled.set(true);
            return new RagSearchResponse(List.of("sample controller pattern"));
        };
        LlmClientFactory llmClientFactory = new LlmClientFactory(
                new LlmProperties("openai"),
                List.of(fakeLlmClient(new AtomicReference<>()))
        );
        GenerationService service = new GenerationServiceImpl(
                ragClient,
                llmClientFactory,
                new GenerationRepository(),
                new RagProperties("http://localhost:8000", "/api/search", 5),
                fakeProjectStructureAnalyzer()
        );

        assertThatThrownBy(() -> service.generate(new GenerationRequest(List.of("Controller"), "Create user controller", " ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("targetTypes, prompt, and projectStructure are required.");
        assertThat(ragCalled.get()).isFalse();
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
                new RagProperties("http://localhost:8000", "/api/search", 5),
                fakeProjectStructureAnalyzer()
        );

        assertThatThrownBy(() -> service.generate(new GenerationRequest(List.of("Controller"), "Create user controller", PROJECT_PATH)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("RAG search result is required before source generation.");
    }

    private ProjectStructureAnalyzer fakeProjectStructureAnalyzer() {
        return (projectPath, targetTypes) -> """
                MCP project structure analysis:
                Tool: FileSystemProjectStructureAnalyzer
                Project full path: %s
                Selected target types: %s

                Analysis result:
                Spring Boot: detected
                Base package: com.hanwha.ai
                Modules:
                - user/controller
                - user/dto
                """.formatted(projectPath, String.join(", ", targetTypes));
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

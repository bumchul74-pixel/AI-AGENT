package com.hanwha.ai.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hanwha.ai.rag.config.RagProperties;
import com.hanwha.ai.rag.dto.RagSearchRequest;
import com.hanwha.ai.rag.dto.RagSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PythonRagClientTest {
    @Test
    void searchCallsPythonRagServerAndMapsDocuments() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        PythonRagClient client = new PythonRagClient(
                restClientBuilder,
                new RagProperties("http://localhost:8000", "/api/search", 3)
        );

        server.expect(once(), requestTo("http://localhost:8000/api/search"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query", is("Create User Controller")))
                .andExpect(jsonPath("$.top_k", is(3)))
                .andRespond(withSuccess("""
                        {
                          "documents": [
                            "[source: standard-source/UserController.java]\\n@RestController public class UserController {}",
                            "[source: standard-source/UserService.java]\\npublic interface UserService {}"
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        RagSearchResponse response = client.search(new RagSearchRequest("Create User Controller", 3));

        assertThat(response.documents()).containsExactly(
                "[source: standard-source/UserController.java]\n@RestController public class UserController {}",
                "[source: standard-source/UserService.java]\npublic interface UserService {}"
        );
        server.verify();
    }

    @Test
    void searchReturnsEmptyResponseWhenPythonRagServerFails() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        PythonRagClient client = new PythonRagClient(
                restClientBuilder,
                new RagProperties("http://localhost:8000", "/api/search", 5)
        );

        server.expect(once(), requestTo("http://localhost:8000/api/search"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        RagSearchResponse response = client.search(new RagSearchRequest("Create User Controller", 5));

        assertThat(response.documents()).isEmpty();
        server.verify();
    }
}

package com.hanwha.ai.document.workflow.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.document.dto.VectorChunkIngestRequest;
import com.hanwha.ai.sourcegraph.dto.JavaSourceGraphIngestRequest;
import com.hanwha.ai.sourcegraph.service.JavaSourceGraphAnalyzer;
import java.util.List;
import org.junit.jupiter.api.Test;

class JavaMethodVectorChunkFactoryTest {
    private final JavaMethodVectorChunkFactory factory =
            new JavaMethodVectorChunkFactory(new JavaSourceGraphAnalyzer());

    @Test
    void createsStableJavaMethodChunkLinkedToMethodAndQualityMetadata() {
        JavaSourceGraphIngestRequest request = new JavaSourceGraphIngestRequest(
                "document:90",
                "OrderService.java",
                """
                        package com.example;
                        public class OrderService {
                            public String findOrder(Long id) {
                                if (id == null) {
                                    throw new IllegalArgumentException();
                                }
                                return load(id);
                            }
                            private String load(Long id) { return ""; }
                        }
                        """,
                "commerce",
                "backend",
                "src/main/java/com/example/OrderService.java",
                "hash90",
                List.of()
        );

        List<VectorChunkIngestRequest.VectorChunk> first =
                factory.create(request, "document:90", 90L);
        List<VectorChunkIngestRequest.VectorChunk> second =
                factory.create(request, "document:90", 90L);

        assertThat(first).hasSize(2);
        assertThat(first).extracting(VectorChunkIngestRequest.VectorChunk::chunkId)
                .containsExactlyElementsOf(second.stream()
                        .map(VectorChunkIngestRequest.VectorChunk::chunkId)
                        .toList());

        VectorChunkIngestRequest.VectorChunk chunk = first.stream()
                .filter(candidate -> candidate.symbol().endsWith("findOrder(Long)"))
                .findFirst()
                .orElseThrow();
        String methodUid = "method:commerce:com.example.OrderService:findOrder(Long)";

        assertThat(chunk.chunkId()).startsWith("document:90:java-method:");
        assertThat(chunk.entityIds()).contains(
                methodUid,
                "type:commerce:com.example.OrderService",
                "file:commerce:src/main/java/com/example/OrderService.java"
        );
        assertThat(chunk.content()).contains(
                "Java Method: com.example.OrderService.findOrder(Long)",
                "return load(id);"
        );
        assertThat(chunk.metadata())
                .containsEntry("contentType", "java-method")
                .containsEntry("methodUid", methodUid)
                .containsEntry("cyclomaticComplexity", 2)
                .containsEntry("parameterCount", 1)
                .containsKeys(
                        "startLine", "endLine", "lineCount", "methodBody", "normalizedBody",
                        "methodHash", "structuralHash", "cognitiveComplexity", "maxNestingDepth",
                        "returnCount", "throwCount", "branchCount", "callCount"
                );
    }
}

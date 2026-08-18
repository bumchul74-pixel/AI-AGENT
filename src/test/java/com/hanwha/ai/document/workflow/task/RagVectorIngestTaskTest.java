package com.hanwha.ai.document.workflow.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hanwha.ai.document.config.DocumentProperties;
import com.hanwha.ai.document.domain.DocumentType;
import com.hanwha.ai.document.domain.RagDocument;
import com.hanwha.ai.document.dto.PythonDocumentIngestResponse;
import com.hanwha.ai.document.dto.VectorChunkIngestRequest;
import com.hanwha.ai.document.service.PythonDocumentIngestClient;
import com.hanwha.ai.document.service.RagDocumentRepository;
import com.hanwha.ai.document.workflow.DocumentIndexContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RagVectorIngestTaskTest {
    @TempDir
    Path tempDirectory;

    @Test
    void javaSourceStoresOnlyMethodChunks() throws Exception {
        Path source = tempDirectory.resolve("Sample.java");
        Files.writeString(source, "class Sample { void run() {} }");
        RagDocument document = RagDocument.create("commerce", "Sample.java", "stored.java",
                source.toString(), Files.size(source), "text/x-java-source", DocumentType.STANDARD_SOURCE);
        document.setId(10L);
        document.setFileHash("hash");
        document.setVectorSourceKey("document:10");
        document.setGraphSourceKey("document:10");
        DocumentIndexContext context = new DocumentIndexContext(document);

        PythonDocumentIngestClient client = mock(PythonDocumentIngestClient.class);
        RagDocumentRepository repository = mock(RagDocumentRepository.class);
        JavaMethodVectorChunkFactory factory = mock(JavaMethodVectorChunkFactory.class);
        VectorChunkIngestRequest.VectorChunk chunk = new VectorChunkIngestRequest.VectorChunk(
                "document:10:java-method:hash", "document:10", "Java Method: Sample.run()",
                10L, "commerce", "Sample.java", "hash", List.of("method:run"),
                "Sample.run()", Map.of("contentType", "java-method"));
        when(factory.create(any(), any(), any())).thenReturn(List.of(chunk));
        when(client.ingestChunks(any())).thenReturn(new PythonDocumentIngestResponse(1));

        RagVectorIngestTask task = new RagVectorIngestTask(client,
                new DocumentProperties("uploads", 1200, 150, "default", "main"), repository, factory);
        task.execute(context);

        assertThat(context.storedChunkCount()).isEqualTo(1);
        assertThat(context.storedChunkIds()).containsExactly("document:10:java-method:hash");
        verify(client).ingestChunks(any());
        verifyNoMoreInteractions(client);
    }
}

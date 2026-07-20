package com.hanwha.ai.document.controller;

import com.hanwha.ai.document.dto.DocumentDownload;
import com.hanwha.ai.document.dto.DocumentPageResponse;
import com.hanwha.ai.document.dto.DocumentResponse;
import com.hanwha.ai.document.service.DocumentService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<DocumentResponse> findAll() {
        return documentService.findAll();
    }

    @GetMapping("/page")
    public DocumentPageResponse findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        return documentService.findPage(page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "documentType", required = false) String documentType
    ) {
        return documentService.upload(file, documentType);
    }

    @PostMapping("/{id}/reindex")
    public DocumentResponse reindex(@PathVariable Long id) {
        return documentService.reindex(id);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        DocumentDownload download = documentService.download(id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (download.contentType() != null && !download.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(download.contentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(download.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

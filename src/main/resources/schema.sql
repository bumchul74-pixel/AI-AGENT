CREATE TABLE IF NOT EXISTS rag_document (
    id BIGSERIAL PRIMARY KEY,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(255),
    document_type VARCHAR(50) NOT NULL,
    index_status VARCHAR(50) NOT NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_document_status
    ON rag_document (index_status);

CREATE INDEX IF NOT EXISTS idx_rag_document_created_at
    ON rag_document (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_document_deleted_at
    ON rag_document (deleted_at);

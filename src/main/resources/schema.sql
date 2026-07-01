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

CREATE TABLE IF NOT EXISTS generation_history (
    id BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(255) NOT NULL,
    target_types JSONB,
    prompt TEXT NOT NULL,
    project_structure TEXT,
    rag_documents JSONB,
    generated_code TEXT NOT NULL,
    llm_provider VARCHAR(50),
    llm_model VARCHAR(100),
    neo4j_index_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    neo4j_indexed_at TIMESTAMP,
    neo4j_index_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE generation_history
    ADD COLUMN IF NOT EXISTS neo4j_index_status VARCHAR(30) NOT NULL DEFAULT 'PENDING';

ALTER TABLE generation_history
    ADD COLUMN IF NOT EXISTS neo4j_indexed_at TIMESTAMP;

ALTER TABLE generation_history
    ADD COLUMN IF NOT EXISTS neo4j_index_error TEXT;

CREATE INDEX IF NOT EXISTS idx_generation_history_neo4j_index_status
    ON generation_history (neo4j_index_status);
CREATE INDEX IF NOT EXISTS idx_generation_history_created_at
    ON generation_history (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_generation_history_target_type
    ON generation_history (target_type);

CREATE INDEX IF NOT EXISTS idx_generation_history_target_types_gin
    ON generation_history USING GIN (target_types);

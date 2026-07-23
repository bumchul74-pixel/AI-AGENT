CREATE TABLE IF NOT EXISTS knowledge_project (
    id BIGSERIAL PRIMARY KEY,
    project_key VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO knowledge_project (project_key, name, description)
VALUES ('default', 'Default Project', 'Existing documents migrated from the legacy index configuration')
ON CONFLICT (project_key) DO NOTHING;

CREATE TABLE IF NOT EXISTS rag_document (
    id BIGSERIAL PRIMARY KEY,
    project_key VARCHAR(64) NOT NULL DEFAULT 'default',
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(255),
    document_type VARCHAR(50) NOT NULL,
    file_hash VARCHAR(64),
    vector_source_key VARCHAR(255),
    graph_source_key VARCHAR(255),
    index_status VARCHAR(50) NOT NULL,
    vector_index_status VARCHAR(50),
    graph_index_status VARCHAR(50),
    chunk_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

ALTER TABLE rag_document
    ADD COLUMN IF NOT EXISTS project_key VARCHAR(64);

UPDATE rag_document SET project_key = 'default'
WHERE project_key IS NULL OR BTRIM(project_key) = '';

ALTER TABLE rag_document
    ALTER COLUMN project_key SET DEFAULT 'default';

ALTER TABLE rag_document
    ALTER COLUMN project_key SET NOT NULL;

ALTER TABLE rag_document
    DROP CONSTRAINT IF EXISTS fk_rag_document_knowledge_project;

ALTER TABLE rag_document
    ADD CONSTRAINT fk_rag_document_knowledge_project
    FOREIGN KEY (project_key) REFERENCES knowledge_project(project_key) ON UPDATE CASCADE;

ALTER TABLE rag_document
    ADD COLUMN IF NOT EXISTS file_hash VARCHAR(64);

ALTER TABLE rag_document
    ADD COLUMN IF NOT EXISTS vector_source_key VARCHAR(255);

ALTER TABLE rag_document
    ADD COLUMN IF NOT EXISTS graph_source_key VARCHAR(255);

ALTER TABLE rag_document
    ADD COLUMN IF NOT EXISTS vector_index_status VARCHAR(50);

ALTER TABLE rag_document
    ADD COLUMN IF NOT EXISTS graph_index_status VARCHAR(50);

-- Source keys are the exact identifiers used by the VectorDB and Neo4j
-- adapters. Normalize rows created by older workflow versions before serving
-- them again.
UPDATE rag_document
SET vector_source_key = 'document:' || id,
    graph_source_key = CASE
        WHEN LOWER(original_file_name) LIKE '%.java' THEN 'document:' || id
        ELSE NULL
    END
WHERE deleted_at IS NULL;
-- Keep one active row for each uploaded content/type pair. Older duplicate
-- rows are hidden before the unique index is created so existing installations
-- are repaired during the same schema initialization.
WITH duplicate_documents AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY project_key, file_hash, document_type
               ORDER BY created_at ASC, id ASC
           ) AS row_number
    FROM rag_document
    WHERE deleted_at IS NULL
      AND file_hash IS NOT NULL
)
UPDATE rag_document document
SET index_status = 'DELETED',
    vector_index_status = 'DELETED',
    graph_index_status = CASE
        WHEN document.graph_source_key IS NULL THEN document.graph_index_status
        ELSE 'DELETED'
    END,
    deleted_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
FROM duplicate_documents duplicate_document
WHERE document.id = duplicate_document.id
  AND duplicate_document.row_number > 1;

DROP INDEX IF EXISTS uq_rag_document_active_hash_type;

CREATE UNIQUE INDEX IF NOT EXISTS uq_rag_document_project_hash_type
    ON rag_document (project_key, file_hash, document_type)
    WHERE deleted_at IS NULL AND file_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_rag_document_project_key
    ON rag_document (project_key, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_document_status
    ON rag_document (index_status);

CREATE INDEX IF NOT EXISTS idx_rag_document_vector_status
    ON rag_document (vector_index_status);

CREATE INDEX IF NOT EXISTS idx_rag_document_graph_status
    ON rag_document (graph_index_status);

CREATE INDEX IF NOT EXISTS idx_rag_document_vector_source_key
    ON rag_document (vector_source_key);

CREATE INDEX IF NOT EXISTS idx_rag_document_graph_source_key
    ON rag_document (graph_source_key);

CREATE INDEX IF NOT EXISTS idx_rag_document_created_at
    ON rag_document (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_rag_document_deleted_at
    ON rag_document (deleted_at);

CREATE TABLE IF NOT EXISTS generation_history (
    id BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(255) NOT NULL,
    target_types JSONB,
    prompt TEXT NOT NULL,
    project_key VARCHAR(64),
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
    ADD COLUMN IF NOT EXISTS project_key VARCHAR(64);

ALTER TABLE generation_history
    DROP CONSTRAINT IF EXISTS fk_generation_history_knowledge_project;

ALTER TABLE generation_history
    ADD CONSTRAINT fk_generation_history_knowledge_project
    FOREIGN KEY (project_key) REFERENCES knowledge_project(project_key)
    ON UPDATE CASCADE ON DELETE SET NULL;

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
CREATE INDEX IF NOT EXISTS idx_generation_history_project_key
    ON generation_history (project_key, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_generation_history_target_type
    ON generation_history (target_type);

CREATE INDEX IF NOT EXISTS idx_generation_history_target_types_gin
    ON generation_history USING GIN (target_types);

CREATE TABLE IF NOT EXISTS chat_project (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_conversation (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    project_id BIGINT REFERENCES chat_project(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES chat_project(id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS chat_message (
    id BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES chat_conversation(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant')),
    message TEXT NOT NULL,
    attachment_name VARCHAR(255),
    attachment_content BYTEA,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS attachment_content BYTEA;

CREATE INDEX IF NOT EXISTS idx_chat_conversation_updated_at
    ON chat_conversation (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_conversation_project_id
    ON chat_conversation (project_id);

CREATE INDEX IF NOT EXISTS idx_chat_project_updated_at
    ON chat_project (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_created_at
    ON chat_message (conversation_id, created_at, id);

CREATE TABLE IF NOT EXISTS secure_coding_scan_job (
    id BIGSERIAL PRIMARY KEY,
    project_key VARCHAR(64) NOT NULL REFERENCES knowledge_project(project_key) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL,
    total_files INTEGER NOT NULL DEFAULT 0,
    processed_files INTEGER NOT NULL DEFAULT 0,
    passed_files INTEGER NOT NULL DEFAULT 0,
    finding_count INTEGER NOT NULL DEFAULT 0,
    error_files INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS secure_coding_scan_file (
    id BIGSERIAL PRIMARY KEY,
    scan_job_id BIGINT NOT NULL REFERENCES secure_coding_scan_job(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL,
    file_name VARCHAR(1000) NOT NULL,
    file_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    UNIQUE (scan_job_id, document_id)
);

CREATE TABLE IF NOT EXISTS secure_coding_scan_result (
    id BIGSERIAL PRIMARY KEY,
    scan_job_id BIGINT NOT NULL REFERENCES secure_coding_scan_job(id) ON DELETE CASCADE,
    scan_file_id BIGINT NOT NULL REFERENCES secure_coding_scan_file(id) ON DELETE CASCADE,
    document_id BIGINT NOT NULL,
    file_name VARCHAR(1000) NOT NULL,
    file_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    severity VARCHAR(30),
    rule_id VARCHAR(500),
    message TEXT NOT NULL,
    start_line INTEGER,
    start_column INTEGER,
    end_line INTEGER,
    end_column INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_secure_coding_active_project
    ON secure_coding_scan_job (project_key) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX IF NOT EXISTS idx_secure_coding_job_project_created
    ON secure_coding_scan_job (project_key, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_secure_coding_file_job_status
    ON secure_coding_scan_file (scan_job_id, status);
CREATE INDEX IF NOT EXISTS idx_secure_coding_result_job
    ON secure_coding_scan_result (scan_job_id, id);

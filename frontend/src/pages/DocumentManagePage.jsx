import { useRef, useState } from 'react';
import { Download, FileStack, RefreshCw, RotateCw, Trash2, UploadCloud } from 'lucide-react';
import { documentDownloadUrl } from '../api/documentApi.js';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { useDocument } from '../hooks/useDocument.js';
import { formatDateTime } from '../utils/dateUtils.js';

const DOCUMENT_TYPES = [
  { value: 'STANDARD_DOCUMENT', label: '표준 문서' },
  { value: 'STANDARD_SOURCE', label: '표준 소스코드' },
];

const STATUS_LABELS = {
  PENDING: '대기',
  INDEXING: '색인 중',
  INDEXED: '완료',
  FAILED: '실패',
  DELETED: '삭제됨',
};

function formatFileSize(value) {
  if (!value) {
    return '0 B';
  }

  const units = ['B', 'KB', 'MB', 'GB'];
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
  const size = value / 1024 ** index;
  return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

function statusClass(status) {
  return `status-${String(status ?? 'PENDING').toLowerCase()}`;
}

function statusLabel(status) {
  return STATUS_LABELS[status] ?? status ?? '대기';
}

export function DocumentManagePage() {
  const documentStore = useDocument();
  const fileInputRef = useRef(null);
  const [selectedFile, setSelectedFile] = useState(null);
  const [documentType, setDocumentType] = useState(DOCUMENT_TYPES[0].value);

  async function handleUpload() {
    const uploadedDocument = await documentStore.upload({ file: selectedFile, documentType });
    if (uploadedDocument) {
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  }

  async function handleDelete(documentId) {
    const confirmed = window.confirm('문서를 삭제하시겠습니까?');
    if (confirmed) {
      await documentStore.remove(documentId);
    }
  }

  return (
    <section className="document-page">
      <div className="card document-upload-panel">
        <div className="panel-title">
          <FileStack size={18} />
          <div>
            <h1>Documents</h1>
            <p>표준 문서와 소스코드를 업로드하고 RAG 검색 대상으로 색인합니다.</p>
          </div>
        </div>

        <div className="document-upload-grid">
          <label className="field">
            <span>문서 유형</span>
            <select value={documentType} onChange={(event) => setDocumentType(event.target.value)}>
              {DOCUMENT_TYPES.map((type) => (
                <option key={type.value} value={type.value}>{type.label}</option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>파일</span>
            <input
              ref={fileInputRef}
              type="file"
              onChange={(event) => setSelectedFile(event.target.files?.[0] ?? null)}
            />
          </label>
        </div>

        {selectedFile && (
          <div className="selected-file">
            <strong>{selectedFile.name}</strong>
            <span>{formatFileSize(selectedFile.size)}</span>
          </div>
        )}

        {documentStore.error && <p className="error-text">{documentStore.error}</p>}

        <div className="action-row">
          <Button icon={UploadCloud} onClick={handleUpload} disabled={!selectedFile || documentStore.isUploading}>
            {documentStore.isUploading ? '업로드 중' : '업로드 및 색인'}
          </Button>
          <Button icon={RefreshCw} variant="secondary" onClick={documentStore.loadDocuments} disabled={documentStore.isLoading}>
            새로고침
          </Button>
        </div>
      </div>

      <div className="card document-list-panel">
        <div className="page-heading">
          <div>
            <h1>업로드 문서</h1>
            <p>총 {documentStore.documents.length}개</p>
          </div>
        </div>

        {documentStore.isLoading ? (
          <Loading />
        ) : documentStore.documents.length === 0 ? (
          <div className="empty-result">
            <strong>등록된 문서가 없습니다.</strong>
            <span>표준 문서나 Java 소스코드를 업로드해 주세요.</span>
          </div>
        ) : (
          <div className="document-table-wrap">
            <table className="document-table">
              <thead>
                <tr>
                  <th>파일명</th>
                  <th>유형</th>
                  <th>상태</th>
                  <th>Chunk</th>
                  <th>크기</th>
                  <th>업로드</th>
                  <th>작업</th>
                </tr>
              </thead>
              <tbody>
                {documentStore.documents.map((document) => {
                  const isWorking = documentStore.workingDocumentId === document.id;

                  return (
                    <tr key={document.id}>
                      <td>
                        <div className="document-name-cell">
                          <strong>{document.originalFileName}</strong>
                          {document.errorMessage && <span>{document.errorMessage}</span>}
                        </div>
                      </td>
                      <td>{document.documentType === 'STANDARD_SOURCE' ? '소스코드' : '문서'}</td>
                      <td>
                        <span className={`status-badge document-status ${statusClass(document.indexStatus)}`}>
                          {statusLabel(document.indexStatus)}
                        </span>
                      </td>
                      <td>{document.chunkCount ?? 0}</td>
                      <td>{formatFileSize(document.fileSize)}</td>
                      <td>{document.createdAt ? formatDateTime(document.createdAt) : '-'}</td>
                      <td>
                        <div className="document-actions">
                          <button
                            className="icon-button"
                            type="button"
                            title="재색인"
                            disabled={isWorking}
                            onClick={() => documentStore.reindex(document.id)}
                          >
                            <RotateCw size={16} />
                          </button>
                          <a className="icon-button" title="다운로드" href={documentDownloadUrl(document.id)}>
                            <Download size={16} />
                          </a>
                          <button
                            className="icon-button danger"
                            type="button"
                            title="삭제"
                            disabled={isWorking}
                            onClick={() => handleDelete(document.id)}
                          >
                            <Trash2 size={16} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}

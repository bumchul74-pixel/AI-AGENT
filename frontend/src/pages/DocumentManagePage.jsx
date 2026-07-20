import { useRef, useState } from 'react';
import { Download, FileStack, RefreshCw, RotateCw, Trash2, UploadCloud } from 'lucide-react';
import { documentDownloadUrl } from '../api/documentApi.js';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { useDocument } from '../hooks/useDocument.js';
import { formatDateTime } from '../utils/dateUtils.js';

const TEXT = {
  standardDocument: '\uD45C\uC900 \uBB38\uC11C',
  standardSource: '\uD45C\uC900 \uC18C\uC2A4\uCF54\uB4DC',
  pending: '\uB300\uAE30',
  indexing: '\uC0C9\uC778 \uC911',
  indexed: '\uC644\uB8CC',
  failed: '\uC2E4\uD328',
  deleted: '\uC0AD\uC81C\uB428',
  deleteConfirm: '\uBB38\uC11C\uB97C \uC0AD\uC81C\uD558\uC2DC\uACA0\uC2B5\uB2C8\uAE4C?',
  description: '\uD45C\uC900 \uBB38\uC11C\uC640 \uC18C\uC2A4\uCF54\uB4DC\uB97C \uC5C5\uB85C\uB4DC\uD558\uACE0 RAG \uAC80\uC0C9 \uB300\uC0C1\uC73C\uB85C \uC0C9\uC778\uD569\uB2C8\uB2E4.',
  documentType: '\uBB38\uC11C \uC720\uD615',
  file: '\uD30C\uC77C',
  uploading: '\uC5C5\uB85C\uB4DC \uC911',
  uploadAndIndex: '\uC5C5\uB85C\uB4DC \uBC0F \uC0C9\uC778',
  refresh: '\uC0C8\uB85C\uACE0\uCE68',
  uploadedDocuments: '\uC5C5\uB85C\uB4DC \uBB38\uC11C',
  emptyTitle: '\uB4F1\uB85D\uB41C \uBB38\uC11C\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.',
  emptyDescription: '\uD45C\uC900 \uBB38\uC11C\uB098 Java \uC18C\uC2A4\uCF54\uB4DC\uB97C \uC5C5\uB85C\uB4DC\uD574 \uC8FC\uC138\uC694.',
  fileName: '\uD30C\uC77C\uBA85',
  type: '\uC720\uD615',
  status: '\uC0C1\uD0DC',
  size: '\uD06C\uAE30',
  uploaded: '\uC5C5\uB85C\uB4DC',
  actions: '\uC791\uC5C5',
  source: '\uC18C\uC2A4\uCF54\uB4DC',
  document: '\uBB38\uC11C',
  reindex: '\uC7AC\uC0C9\uC778',
  download: '\uB2E4\uC6B4\uB85C\uB4DC',
  delete: '\uC0AD\uC81C',
};

const DOCUMENT_TYPES = [
  { value: 'STANDARD_DOCUMENT', label: TEXT.standardDocument },
  { value: 'STANDARD_SOURCE', label: TEXT.standardSource },
];

const STATUS_LABELS = {
  PENDING: TEXT.pending,
  INDEXING: TEXT.indexing,
  INDEXED: TEXT.indexed,
  FAILED: TEXT.failed,
  DELETED: TEXT.deleted,
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
  return STATUS_LABELS[status] ?? status ?? TEXT.pending;
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
    const confirmed = window.confirm(TEXT.deleteConfirm);
    if (confirmed) {
      await documentStore.remove(documentId);
    }
  }

  function handleDocumentTableScroll(event) {
    const target = event.currentTarget;
    const distanceToBottom = target.scrollHeight - target.scrollTop - target.clientHeight;
    if (distanceToBottom <= 80) {
      documentStore.loadMoreDocuments();
    }
  }

  return (
    <section className="document-page">
      <div className="card document-upload-panel">
        <div className="panel-title">
          <FileStack size={18} />
          <div>
            <h1>Documents</h1>
            <p>{TEXT.description}</p>
          </div>
        </div>

        <div className="document-upload-grid">
          <label className="field">
            <span>{TEXT.documentType}</span>
            <select value={documentType} onChange={(event) => setDocumentType(event.target.value)}>
              {DOCUMENT_TYPES.map((type) => (
                <option key={type.value} value={type.value}>{type.label}</option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>{TEXT.file}</span>
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
            {documentStore.isUploading ? TEXT.uploading : TEXT.uploadAndIndex}
          </Button>
          <Button icon={RefreshCw} variant="secondary" onClick={documentStore.loadDocuments} disabled={documentStore.isLoading}>
            {TEXT.refresh}
          </Button>
        </div>
      </div>

      <div className="card document-list-panel">
        <div className="page-heading">
          <div>
            <h1>{TEXT.uploadedDocuments}</h1>
            <p>{`\uCD1D ${documentStore.totalCount}\uAC1C \uC911 ${documentStore.documents.length}\uAC1C \uD45C\uC2DC`}</p>
          </div>
        </div>

        {documentStore.isLoading ? (
          <Loading />
        ) : documentStore.documents.length === 0 ? (
          <div className="empty-result">
            <strong>{TEXT.emptyTitle}</strong>
            <span>{TEXT.emptyDescription}</span>
          </div>
        ) : (
          <div className="document-table-wrap" onScroll={handleDocumentTableScroll}>
            <table className="document-table">
              <thead>
                <tr>
                  <th>{TEXT.fileName}</th>
                  <th>{TEXT.type}</th>
                  <th>{TEXT.status}</th>
                  <th>Chunk</th>
                  <th>{TEXT.size}</th>
                  <th>{TEXT.uploaded}</th>
                  <th>{TEXT.actions}</th>
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
                      <td>{document.documentType === 'STANDARD_SOURCE' ? TEXT.source : TEXT.document}</td>
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
                            title={TEXT.reindex}
                            disabled={isWorking}
                            onClick={() => documentStore.reindex(document.id)}
                          >
                            <RotateCw size={16} />
                          </button>
                          <a className="icon-button" title={TEXT.download} href={documentDownloadUrl(document.id)}>
                            <Download size={16} />
                          </a>
                          <button
                            className="icon-button danger"
                            type="button"
                            title={TEXT.delete}
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
                {documentStore.isLoadingMore && (
                  <tr className="document-loading-row">
                    <td colSpan={7}>
                      <Loading />
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </section>
  );
}

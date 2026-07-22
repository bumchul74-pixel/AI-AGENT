import { useEffect, useState } from 'react';
import { FolderKanban, Pencil, Plus, Save, Trash2, X } from 'lucide-react';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import {
  createKnowledgeProject,
  deleteKnowledgeProject,
  fetchKnowledgeProjects,
  updateKnowledgeProject,
} from '../api/projectApi.js';
import { isApiRequestError } from '../api/apiClient.js';
import { formatDateTime } from '../utils/dateUtils.js';

const EMPTY_FORM = { projectKey: '', name: '', description: '' };

export function ProjectManagePage() {
  const [projects, setProjects] = useState([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingKey, setEditingKey] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');

  async function load() {
    setIsLoading(true);
    setError('');
    try { setProjects(await fetchKnowledgeProjects()); }
    catch (exception) { setError(isApiRequestError(exception) ? '' : exception.message); }
    finally { setIsLoading(false); }
  }

  useEffect(() => { load(); }, []);

  function change(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function startEdit(project) {
    setEditingKey(project.projectKey);
    setForm({ projectKey: project.projectKey, name: project.name, description: project.description ?? '' });
  }

  function resetForm() {
    setEditingKey(null);
    setForm(EMPTY_FORM);
    setError('');
  }

  async function save() {
    setIsSaving(true);
    setError('');
    try {
      if (editingKey) await updateKnowledgeProject(editingKey, form);
      else await createKnowledgeProject(form);
      resetForm();
      await load();
    } catch (exception) { setError(isApiRequestError(exception) ? '' : exception.message); }
    finally { setIsSaving(false); }
  }

  async function remove(project) {
    if (!window.confirm(`'${project.name}' 프로젝트를 삭제하시겠습니까?`)) return;
    setError('');
    try { await deleteKnowledgeProject(project.projectKey); await load(); }
    catch (exception) { setError(isApiRequestError(exception) ? '' : exception.message); }
  }

  return (
    <section className="project-manage-page">
      <div className="card project-form-panel">
        <div className="panel-title">
          <FolderKanban size={18} />
          <div>
            <h1>프로젝트 관리</h1>
            <p>문서를 업로드하기 전에 Knowledge 데이터를 분리할 프로젝트를 생성합니다.</p>
          </div>
        </div>
        <div className="project-form-grid">
          <label className="field">
            <span>프로젝트 키</span>
            <input value={form.projectKey} disabled={Boolean(editingKey)} maxLength={64}
              placeholder="예: user-platform" onChange={(event) => change('projectKey', event.target.value.toLowerCase())} />
            <small>영문 소문자, 숫자, -, _ 조합으로 입력합니다. 색인 후에는 변경할 수 없습니다.</small>
          </label>
          <label className="field">
            <span>프로젝트 이름</span>
            <input value={form.name} maxLength={120} placeholder="예: 사용자 플랫폼"
              onChange={(event) => change('name', event.target.value)} />
          </label>
          <label className="field project-description-field">
            <span>설명</span>
            <input value={form.description} maxLength={500} placeholder="프로젝트 용도 또는 범위"
              onChange={(event) => change('description', event.target.value)} />
          </label>
        </div>
        {error && <p className="error-text">{error}</p>}
        <div className="action-row">
          <Button icon={editingKey ? Save : Plus} onClick={save}
            disabled={isSaving || !form.projectKey.trim() || !form.name.trim()}>
            {isSaving ? '저장 중' : editingKey ? '수정 저장' : '프로젝트 추가'}
          </Button>
          {editingKey && <Button icon={X} variant="secondary" onClick={resetForm}>취소</Button>}
        </div>
      </div>

      <div className="card project-list-panel">
        <div className="page-heading">
          <div><h2>등록 프로젝트</h2><p>총 {projects.length}개 프로젝트</p></div>
        </div>
        {isLoading ? <Loading /> : projects.length === 0 ? (
          <div className="empty-result"><strong>등록된 프로젝트가 없습니다.</strong><span>첫 프로젝트를 추가해 주세요.</span></div>
        ) : (
          <div className="project-card-grid">
            {projects.map((project) => (
              <article className="knowledge-project-card" key={project.projectKey}>
                <div className="project-card-heading">
                  <div><strong>{project.name}</strong><code>{project.projectKey}</code></div>
                  <span>{project.documentCount} documents</span>
                </div>
                <p>{project.description || '설명이 없습니다.'}</p>
                <footer>
                  <small>{project.updatedAt ? formatDateTime(project.updatedAt) : '-'}</small>
                  <div className="document-actions">
                    <button className="icon-button" type="button" title="수정" onClick={() => startEdit(project)}><Pencil size={16} /></button>
                    <button className="icon-button danger" type="button" title="삭제"
                      disabled={project.projectKey === 'default' || project.documentCount > 0}
                      onClick={() => remove(project)}><Trash2 size={16} /></button>
                  </div>
                </footer>
              </article>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

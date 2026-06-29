import { apiUrl } from '../constants/apiConstants.js';

async function readError(response, fallbackMessage) {
  const errorBody = await response.json().catch(() => null);
  return new Error(errorBody?.message ?? fallbackMessage);
}

function queryString(filters = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      params.append(key, String(value).trim());
    }
  });

  const query = params.toString();
  return query ? `?${query}` : '';
}

export async function fetchProjectStructures() {
  const response = await fetch(apiUrl('/api/generations/project-structures'));

  if (!response.ok) {
    throw await readError(response, 'Project structure list request failed.');
  }

  const projectStructures = await response.json();
  if (!Array.isArray(projectStructures)) {
    throw new Error('Project structure list response is invalid.');
  }

  return projectStructures;
}

export async function generateCode({ targetTypes, prompt, projectStructure }) {
  const response = await fetch(apiUrl('/api/generations'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ targetTypes, prompt, projectStructure }),
  });

  if (!response.ok) {
    throw await readError(response, 'Code generation request failed.');
  }

  return response.json();
}

export async function fetchGenerationHistory(filters = {}) {
  const response = await fetch(apiUrl(`/api/generations/history${queryString(filters)}`));

  if (!response.ok) {
    throw await readError(response, 'Generation history request failed.');
  }

  const history = await response.json();
  if (!Array.isArray(history)) {
    throw new Error('Generation history response is invalid.');
  }

  return history;
}

export async function fetchGenerationHistoryDetail(id) {
  const response = await fetch(apiUrl(`/api/generations/history/${id}`));

  if (!response.ok) {
    throw await readError(response, 'Generation history detail request failed.');
  }

  return response.json();
}
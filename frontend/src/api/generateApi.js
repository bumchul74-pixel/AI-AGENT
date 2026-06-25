import { apiUrl } from '../constants/apiConstants.js';

export async function fetchProjectStructures() {
  const response = await fetch(apiUrl('/api/generations/project-structures'));

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message ?? 'Project structure list request failed.');
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
    const errorBody = await response.json().catch(() => null);
    throw new Error(errorBody?.message ?? 'Code generation request failed.');
  }

  return response.json();
}
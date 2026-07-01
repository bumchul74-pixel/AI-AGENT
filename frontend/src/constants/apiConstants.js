export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export function apiUrl(path) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const normalizedBaseUrl = API_BASE_URL.endsWith('/') ? API_BASE_URL.slice(0, -1) : API_BASE_URL;

  return `${normalizedBaseUrl}${normalizedPath}`;
}

export const GENERATION_TARGETS = [
  'Controller',
  'Service',
  'ServiceImpl',
  // 'Repository',
  'Mapper',
  'DTO',
  'DOMAIN',
  // 'Exception',
  'Test Code',
];

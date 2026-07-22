import { apiUrl } from '../constants/apiConstants.js';

export const API_ERROR_EVENT = 'app:api-error';

export class ApiRequestError extends Error {
  constructor(message, status = 0, cause = null) {
    super(message, cause ? { cause } : undefined);
    this.name = 'ApiRequestError';
    this.status = status;
  }
}

export function isApiRequestError(error) {
  return error instanceof ApiRequestError;
}

function emitApiError(message) {
  if (typeof window !== 'undefined' && message) {
    window.dispatchEvent(new CustomEvent(API_ERROR_EVENT, { detail: { message } }));
  }
}

export function apiResponseError(message, status = 200) {
  emitApiError(message);
  return new ApiRequestError(message, status);
}

async function errorMessage(response, fallbackMessage) {
  const body = await response.clone().json().catch(() => null);
  const message = body?.message ?? body?.detail;
  return typeof message === 'string' && message.trim() ? message : fallbackMessage;
}

export async function apiRequest(path, {
  errorMessage: fallbackMessage = '서버 요청을 처리하지 못했습니다.',
  responseType = 'json',
  includeResponse = false,
  ...options
} = {}) {
  const url = /^https?:\/\//i.test(path) ? path : apiUrl(path);
  let response;

  try {
    response = await fetch(url, options);
  } catch (cause) {
    emitApiError(fallbackMessage);
    throw new ApiRequestError(fallbackMessage, 0, cause);
  }

  if (!response.ok) {
    const message = await errorMessage(response, fallbackMessage);
    emitApiError(message);
    throw new ApiRequestError(message, response.status);
  }

  try {
    let data = null;
    if (responseType === 'blob') data = await response.blob();
    else if (responseType === 'text') data = await response.text();
    else if (responseType === 'json' && response.status !== 204) {
      const text = await response.text();
      data = text ? JSON.parse(text) : null;
    }
    return includeResponse ? { data, response } : data;
  } catch (cause) {
    const message = '서버 응답을 처리하지 못했습니다.';
    emitApiError(message);
    throw new ApiRequestError(message, response.status, cause);
  }
}

import { apiUrl } from '../constants/apiConstants.js';
import {
  BACKEND_REQUEST_TIMEOUT_MESSAGE,
  BACKEND_REQUEST_TIMEOUT_MS,
} from '../config/apiProperties.js';

export const API_ERROR_EVENT = 'app:api-error';
export const APP_NOTIFICATION_EVENT = 'app:notification';

export class ApiRequestError extends Error {
  constructor(message, status = 0, cause = null) {
    super(message, cause ? { cause } : undefined);
    this.name = 'ApiRequestError';
    this.status = status;
  }
}

export class ApiRequestCancelledError extends Error {
  constructor(message = 'Backend request cancelled.', cause = null) {
    super(message, cause ? { cause } : undefined);
    this.name = 'ApiRequestCancelledError';
  }
}

export function isApiRequestError(error) {
  return error instanceof ApiRequestError;
}

export function isApiRequestCancelledError(error) {
  return error instanceof ApiRequestCancelledError;
}

function emitApiError(message) {
  if (typeof window !== 'undefined' && message) {
    window.dispatchEvent(new CustomEvent(API_ERROR_EVENT, { detail: { message } }));
    window.dispatchEvent(new CustomEvent(APP_NOTIFICATION_EVENT, {
      detail: { message, variant: 'error' },
    }));
  }
}

export function notifyApp(message, variant = 'success') {
  if (typeof window !== 'undefined' && message) {
    window.dispatchEvent(new CustomEvent(APP_NOTIFICATION_EVENT, {
      detail: { message, variant },
    }));
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

function requestControl(externalSignal, timeoutMs) {
  const controller = new AbortController();
  let timedOut = false;

  function abortFromCaller() {
    controller.abort(externalSignal?.reason);
  }

  if (externalSignal?.aborted) {
    abortFromCaller();
  } else {
    externalSignal?.addEventListener('abort', abortFromCaller, { once: true });
  }

  const timeoutId = globalThis.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  return {
    signal: controller.signal,
    timedOut: () => timedOut,
    callerAborted: () => Boolean(externalSignal?.aborted),
    cleanup() {
      globalThis.clearTimeout(timeoutId);
      externalSignal?.removeEventListener('abort', abortFromCaller);
    },
  };
}

async function responseData(response, responseType) {
  if (responseType === 'blob') return response.blob();
  if (responseType === 'text') return response.text();
  if (responseType === 'json' && response.status !== 204) {
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  }
  return null;
}

export async function apiRequest(path, {
  errorMessage: fallbackMessage = '\uC11C\uBC84 \uC694\uCCAD\uC744 \uCC98\uB9AC\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.',
  responseType = 'json',
  includeResponse = false,
  signal: externalSignal,
  timeoutMs = BACKEND_REQUEST_TIMEOUT_MS,
  ...options
} = {}) {
  const url = /^https?:\/\//i.test(path) ? path : apiUrl(path);
  const normalizedTimeout = Number.isFinite(timeoutMs) && timeoutMs > 0
    ? timeoutMs
    : BACKEND_REQUEST_TIMEOUT_MS;
  const control = requestControl(externalSignal, normalizedTimeout);

  try {
    const response = await fetch(url, { ...options, signal: control.signal });

    if (!response.ok) {
      throw new ApiRequestError(
        await errorMessage(response, fallbackMessage),
        response.status,
      );
    }

    try {
      const data = await responseData(response, responseType);
      return includeResponse ? { data, response } : data;
    } catch (cause) {
      throw new ApiRequestError(
        '\uC11C\uBC84 \uC751\uB2F5\uC744 \uCC98\uB9AC\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.',
        response.status,
        cause,
      );
    }
  } catch (cause) {
    if (control.timedOut()) {
      emitApiError(BACKEND_REQUEST_TIMEOUT_MESSAGE);
      throw new ApiRequestError(BACKEND_REQUEST_TIMEOUT_MESSAGE, 408, cause);
    }
    if (control.callerAborted()) {
      throw new ApiRequestCancelledError(undefined, cause);
    }
    if (cause instanceof ApiRequestError) {
      emitApiError(cause.message);
      throw cause;
    }
    emitApiError(fallbackMessage);
    throw new ApiRequestError(fallbackMessage, 0, cause);
  } finally {
    control.cleanup();
  }
}

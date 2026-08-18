import propertiesText from './application.properties?raw';

const DEFAULT_BACKEND_REQUEST_TIMEOUT_MS = 60_000;

function readProperties(source) {
  return Object.fromEntries(
    source
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line && !line.startsWith('#'))
      .map((line) => {
        const separator = line.indexOf('=');
        return separator < 0
          ? [line, '']
          : [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
      }),
  );
}

const properties = readProperties(propertiesText);
const configuredTimeout = Number(properties['backend.request.timeout-ms']);

export const BACKEND_REQUEST_TIMEOUT_MS = Number.isFinite(configuredTimeout) && configuredTimeout > 0
  ? configuredTimeout
  : DEFAULT_BACKEND_REQUEST_TIMEOUT_MS;

export const BACKEND_REQUEST_TIMEOUT_MESSAGE =
  `Backend \uC11C\uBC84 \uC694\uCCAD\uC774 ${Math.ceil(BACKEND_REQUEST_TIMEOUT_MS / 1000)}\uCD08\uB97C \uCD08\uACFC\uD588\uC2B5\uB2C8\uB2E4.`;

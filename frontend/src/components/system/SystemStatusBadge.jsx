import { AlertTriangle, CheckCircle2, CircleHelp, XCircle } from 'lucide-react';

export const SYSTEM_STATUS_LABELS = {
  UP: '정상',
  DEGRADED: '일부 장애',
  DOWN: '장애',
  UNKNOWN: '미확인',
};

const STATUS_ICONS = {
  UP: CheckCircle2,
  DEGRADED: AlertTriangle,
  DOWN: XCircle,
  UNKNOWN: CircleHelp,
};

export function SystemStatusBadge({ status = 'UNKNOWN' }) {
  const normalized = SYSTEM_STATUS_LABELS[status] ? status : 'UNKNOWN';
  const Icon = STATUS_ICONS[normalized];
  return <span className={`system-status-badge status-${normalized.toLowerCase()}`}><Icon size={13} />{SYSTEM_STATUS_LABELS[normalized]}</span>;
}

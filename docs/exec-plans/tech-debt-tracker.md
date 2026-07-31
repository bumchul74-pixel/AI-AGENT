# 기술부채 추적기

| ID | 우선순위 | Gap | 완료 조건 | 검토 시점 |
| --- | --- | --- | --- | --- |
| TD-001 | 높음 | 운영 SLO, alert, RTO/RPO 미정 | 승인된 지표와 runbook | 운영 배포 전 |
| TD-002 | 중간 | package 규칙이 문서에만 존재 | ArchUnit test 추가 | 다음 아키텍처 변경 |
| TD-003 | 중간 | coverage threshold 없음 | 합의 기준을 `check`에 연결 | baseline 측정 후 |
| TD-004 | 중간 | frontend lint/component/a11y test 없음 | 검증 task 연결 | 다음 주요 UI 변경 |
| TD-005 | 중간 | Python dependency가 완전히 lock되지 않음 | lock과 취약점 점검 | RAG 배포 자동화 전 |

해결 작업은 active plan이나 issue를 연결하고 결과를 완료 계획에 기록한다.

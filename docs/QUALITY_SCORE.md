# 품질 점검표

이 점수는 검토 보조 자료이며 `verifyAll`을 대체하지 않는다. 0은 부재, 5는 강한 자동화를 뜻한다.

| 영역 | 점수 | 현재 증거 | 다음 개선 |
| --- | ---: | --- | --- |
| 검증 harness | 3 | Wrapper, doctor, verifyAll, JaCoCo report | coverage threshold |
| Java test | 3 | JUnit/Mockito/Spring test | tag 정착 |
| Python RAG | 3 | unittest와 ragTest | dependency lock/coverage |
| 프런트엔드 | 2 | lockfile과 production build | lint/component/a11y |
| 아키텍처 | 2 | canonical architecture와 ADR | ArchUnit |
| 문서 | 3 | 계층 문서와 계획 | API/schema 자동 생성 |
| 보안·신뢰성 | 2 | 정책과 구성 경계 | threat model/SLO |

**기준점: 18/35.** 실제 `verifyAll`과 운영 증거에 따라 갱신한다. 수용한 gap은 [기술부채 추적기](exec-plans/tech-debt-tracker.md)에 기록한다.

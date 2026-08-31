# Local Firestore Emulator 가이드

## 목적

Production Firestore 데이터와 비용에 영향을 주지 않고 Backend의 Repository,
Transaction, API와 로컬 테스트 데이터를 Firestore Emulator로 검증한다. 이 문서는
Emulator 시작, Backend 연결, Seed 생성·복원과 자동 통합 테스트의 기준 절차를 다룬다.

## 구성

```text
Firebase CLI  : 15.26.0
Project ID    : demo-town-ai
Database ID   : town-ai
Firestore     : Standard, Native mode Emulator
Firestore API : 127.0.0.1:8081
Emulator UI   : http://127.0.0.1:4000
Backend API   : http://localhost:8080
```

`demo-` Project ID는 실제 GCP Project가 아님을 Firebase CLI에 명확히 알려 실수로
Production에 연결되는 것을 막는다.

## 준비 사항

- Java 25
- Node.js 24 LTS
- PowerShell

Firebase CLI는 Script가 `npx`로 고정 Version을 실행하므로 Global 설치가 필수는 아니다.

## 1. Emulator 시작

Repository Root에서 다음 명령을 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\local-firestore-start.ps1
```

처음 실행하면 Firebase CLI Package를 내려받을 수 있다. Emulator Data는 기본적으로
메모리에만 존재하며 Process를 종료하면 사라진다.

## 2. Backend 시작

새 PowerShell에서 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\local-backend-firestore.ps1
```

Script가 다음 환경변수를 현재 Process에만 설정한다.

```text
FIRESTORE_PROJECT_ID=demo-town-ai
FIRESTORE_DATABASE_ID=town-ai
FIRESTORE_EMULATOR_HOST=127.0.0.1:8081
REPORT_STORAGE_TYPE=local
WEB_AUTH_ENABLED=false
LINE_EVENT_DISPATCHER=local
```

`FIRESTORE_DATABASE_ID`는 Production의 named database와 동일한 `town-ai`를 사용한다.
`FIRESTORE_EMULATOR_HOST`에는 `http://`를 붙이지 않는다. 이 값이 존재하면 Google
Application Default Credentials 없이 Emulator의 Plaintext Channel을 사용한다.

## 3. 테스트 데이터 생성

Backend가 8080에서 실행된 뒤 새 PowerShell에서 다음을 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\local-seed.ps1
```

Area 3개와 Visit 5개를 API를 통해 생성한다. 동일 데이터가 있으면 건너뛰므로 반복
실행할 수 있다.

## 4. 전체 초기화 후 복원

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\local-restore-seed.ps1
```

이 Script는 다음 순서로 동작한다.

1. Project ID가 `demo-`로 시작하고 대상이 Loopback Emulator인지 확인
2. Emulator 전용 삭제 Endpoint로 모든 문서 삭제
3. 기존 `local-seed.ps1` 실행
4. Counter도 삭제되므로 Area와 Visit ID를 1부터 다시 생성

Production Project나 원격 Host를 대상으로 실행하지 않는다.

## 5. 확인

```powershell
Invoke-RestMethod http://localhost:8080/api/areas
Invoke-RestMethod http://localhost:8080/api/visits
Invoke-RestMethod http://localhost:8080/api/statistics
```

예상 결과:

- 활성 Area: 3개
- Visit: 5개
- Area ID: 1, 2, 3
- Visit ID: 1~5

문서 원본은 Emulator UI `http://127.0.0.1:4000`에서도 확인할 수 있다.

## 자동 테스트

일반 Unit Test는 Emulator 없이 실행한다.

```powershell
.\backend\gradlew.bat -p backend :app:test --no-daemon
```

실제 Firestore Repository 통합 테스트는 다음 명령으로 실행한다.

```powershell
npx.cmd --yes firebase-tools@15.26.0 emulators:exec `
  --only firestore `
  --project demo-town-ai `
  --config backend/firebase.json `
  ".\backend\gradlew.bat -p backend :app:test --no-daemon"
```

GitHub Actions도 같은 방식으로 Emulator를 실행하고 Backend Build와 Javadoc을
검증한다.

## Firestore 설정 파일

| 파일 | 역할 |
| --- | --- |
| `backend/firebase.json` | `town-ai` named database, Standard Edition, Emulator Port와 Rules·Index 파일 연결 |
| `backend/firestore.rules` | Client의 모든 직접 접근 거부 |
| `backend/firestore.indexes.json` | Composite Index 정의, V1은 비어 있음 |

Admin Server SDK는 Security Rules가 아닌 IAM으로 접근한다. 따라서 Rules Emulator
Test는 Client SDK 보안 검증용이고, Backend Repository 통합 테스트는 Server SDK의
데이터 동작 검증용이다.

## 문제 해결

### Port가 이미 사용 중인 경우

```powershell
Get-NetTCPConnection -State Listen -LocalPort 8081,4000 |
  Select-Object LocalPort,OwningProcess
```

실행 중인 Emulator를 종료하거나 `backend/firebase.json`의 Port와 실행 Script 환경값을
함께 변경한다.

### Production 데이터가 보이는 경우

즉시 Backend를 종료하고 `FIRESTORE_EMULATOR_HOST=127.0.0.1:8081`,
`FIRESTORE_PROJECT_ID=demo-town-ai`, `FIRESTORE_DATABASE_ID=town-ai`를 확인한다. 로컬 개발에서는 실제 `town-ai`
Project를 사용하지 않는다.

### PowerShell 실행 정책 오류

문서의 `powershell.exe -ExecutionPolicy Bypass -File ...` 형식을 사용한다. 이 설정은
해당 Process에만 적용되며 시스템 실행 정책을 영구 변경하지 않는다.

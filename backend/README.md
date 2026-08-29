# Town AI Backend

## Local 실행

Town AI Backend의 Local Database는 Firestore Emulator이다. Repository Root에서 먼저
Emulator를 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\local-firestore-start.ps1
```

새 PowerShell에서 Backend를 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\local-backend-firestore.ps1
```

기본 주소:

- Backend: `http://localhost:8080`
- Firestore Emulator: `127.0.0.1:8081`
- Firestore Database ID: `town-ai`
- Emulator UI: `http://127.0.0.1:4000`

Local 기본값은 Web 관리 API 인증을 사용하지 않는다. 실제 Firebase ID Token 검증을
활성화할 때는 다음 일반 환경변수가 추가로 필요하다.

```powershell
$env:WEB_AUTH_ENABLED = "true"
$env:FIREBASE_PROJECT_ID = "town-ai"
$env:FIREBASE_ALLOWED_UID = "본인 Firebase UID"
```

Cloud Run에서는 Runtime Service Account의 Application Default Credentials를 사용한다.
Service Account JSON Key를 Repository나 Docker Image에 저장하지 않는다.

LINE Report 결과 링크를 Local에서 생성하려면 32자 이상의 별도 HMAC Secret도 설정한다.

```powershell
$env:REPORT_LINK_SIGNING_SECRET = "로컬 전용 32자 이상 무작위 값"
$env:REPORT_LINK_VALIDITY = "30d"
```

Production의 `REPORT_LINK_SIGNING_SECRET`은 Secret Manager에서 주입하고 Local 값과
공유하지 않는다. 기존 Web Report 조회·다운로드는 Firebase 인증을 요구하고, LINE에는
30일 만료 서명 URL을 전달한다.

## Local 테스트 데이터

Backend 실행 후 다른 PowerShell에서 Local 전용 Seed Script를 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\local-seed.ps1
```

Area 3개와 Visit 5개를 API로 등록한다. Emulator 전체 데이터를 지우고 Seed 상태로
되돌리려면 다음을 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\local-restore-seed.ps1
```

복원 Script는 Loopback Firestore Emulator만 허용하며 Production에는 실행되지 않는다.

## Production 데이터 복원

Legacy Cloud SQL 종료 전 GCS Report를 기준으로 Production Firestore에 Area 3개와 Visit
3개를 복원할 때 다음 Script를 사용한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\production-restore-gcs-report-data.ps1 `
  -ConfirmProductionRestore
```

Script는 `town-ai/town-ai` Firestore만 허용하고 Database 설정, 기존 Collection과 GCS
Markdown 9개의 메타데이터를 검사한다. 최초 실행은 모든 문서를 원자적으로 생성하며,
동일한 복원 데이터가 이미 있으면 덮어쓰지 않고 필드값과 GCS 불변 여부만 재검증한다.
실제 복원·Cloud SQL 종료 결과는 `../docs/015-firestore-production-cutover.md`에 기록한다.

## 테스트

Unit Test:

```powershell
.\backend\gradlew.bat -p backend :app:test --no-daemon
```

Firestore Emulator 통합 Test:

```powershell
npx.cmd --yes firebase-tools@15.26.0 emulators:exec `
  --only firestore `
  --project demo-town-ai `
  --config backend/firebase.json `
  ".\backend\gradlew.bat -p backend :app:test --no-daemon"
```

자세한 내용:

- Local Emulator: `../docs/007-local-database.md`
- Firestore 데이터 모델: `../docs/003-erd.md`
- Firestore 전환: `../docs/014-firestore-migration.md`
- Production 전환 검증: `../docs/015-firestore-production-cutover.md`

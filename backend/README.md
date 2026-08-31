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
실제 복원·Cloud SQL 종료 결과는 `../docs/015-firestore-production-cutover-validation.md`에 기록한다.

Area·Visit 복원 후 GCS에 보존된 기존 Report 9개의 Firestore Metadata를 복원할 때는
다음 Script를 사용한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\production-restore-gcs-report-metadata.ps1 `
  -ConfirmProductionRestore
```

Report Metadata 복원 Script는 GCS 파일명에 포함된 기존 ID `2~10`, 생성 당시 모델과
Prompt Version, 대상 Area를 복원한다. 이미 같은 Metadata가 있으면 값을 검증만 하고,
`counters/report`가 더 큰 경우 Counter를 낮추지 않는다. 실행 전후 GCS 객체의 generation,
Hash, 크기와 수정 시각을 비교하므로 Markdown 본문은 변경하지 않는다.

## Production Report 고아 객체 점검

Firestore `reports.storagePath`에서 참조하지 않는 GCS Markdown을 확인할 때 다음 Script를
실행한다. 기본 실행은 Dry Run이며 GCS 객체를 삭제하지 않는다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\production-cleanup-orphan-reports.ps1
```

Script는 `town-ai/town-ai`와 `gs://town_ai/reports/v1/{area|compare|summary|all}/*.md`만
허용한다. 기본 24시간보다 최근인
미참조 객체는 생성 중일 가능성이 있으므로 제외하고, 오래된 고아 객체의 경로·수정 시각·크기와
Generation만 출력한다. 실제 삭제에는 다음 두 Option을 함께 지정해야 한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\production-cleanup-orphan-reports.ps1 `
  -DeleteOrphans `
  -ConfirmProductionCleanup
```

삭제에는 Scan 당시 Generation 일치 조건을 사용해 조회 후 교체된 객체를 보호한다. 이미 없는
객체는 성공으로 처리한다. 실행 계정에는 Firestore Report 조회, GCS 객체 목록 조회 권한이
필요하며 실제 삭제 시 GCS 객체 삭제 권한도 필요하다. 삭제 후 Dry Run을 다시 실행해 고아
객체가 0개인지 확인한다.

## 코드 주석 기준

- 공개 Class·Interface에는 책임과 계층 경계를 설명하는 한국어 Javadoc을 작성한다.
- Repository 계약, Transaction 제약, 보안·멱등성, 외부 API와 Storage 경계에는 단순 동작보다
  설계 이유와 보장 조건을 기록한다.
- Override 구현은 Interface 계약을 그대로 반복하지 않고 Firestore·GCS 등 구현체만의 차이가
  있을 때 주석을 추가한다.
- Getter, 단순 DTO 변환, 메서드 이름으로 충분히 설명되는 분기에는 중복 주석을 작성하지 않는다.
- 코드 식별자와 제품명은 원문을 유지하되 설명 문장은 한국어로 통일한다.

## 테스트

Unit Test:

```powershell
.\backend\gradlew.bat -p backend :app:test --no-daemon
```

Production Report 고아 객체 정리 Script Test:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\backend\scripts\test-report-orphan-cleanup.ps1
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

- Local Emulator: `../docs/007-local-firestore-emulator.md`
- Firestore 데이터 모델: `../docs/003-firestore-data-model.md`
- Firestore 전환: `../docs/014-firestore-migration-design.md`
- Production 전환 검증: `../docs/015-firestore-production-cutover-validation.md`

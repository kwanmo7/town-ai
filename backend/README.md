# Town AI Backend

## Local 실행

PowerShell에서 Local MySQL 접속 정보를 환경변수로 설정하고 실행한다.

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "로컬 MySQL 비밀번호"
$env:REPORT_STORAGE_TYPE = "local"

.\gradlew.bat :app:bootRun
```

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

Backend 실행 후 다른 PowerShell에서 Local 전용 Seed 스크립트를 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\local-seed.ps1
```

스크립트는 Loopback 주소만 허용하고 Area 3개와 Visit 5개를 API로 등록한다. 같은
Area와 같은 날짜·점수의 Visit이 있으면 건너뛰므로 반복 실행할 수 있다.
`ExecutionPolicy Bypass`는 이 명령으로 시작한 Process에만 적용하며 Windows의
영구 실행 정책은 변경하지 않는다.

Area 관리 화면에서 Seed Area를 Soft Delete한 경우에는 기존 Visit을 유지한 채
세 Area만 복구한 후 Seed를 다시 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\local-restore-seed.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\local-seed.ps1
```

복구 스크립트는 Local MySQL만 허용하며 비밀번호를 파일이나 명령 인자에 저장하지
않고 MySQL Client Prompt에서 입력받는다.

자세한 Database와 Flyway 사용 방법은 `../docs/007-local-database.md`를 참고한다.

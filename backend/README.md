# Town AI Backend

## Local 실행

PowerShell에서 Local MySQL 접속 정보를 환경변수로 설정하고 실행한다.

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "로컬 MySQL 비밀번호"
$env:REPORT_STORAGE_TYPE = "local"

.\gradlew.bat :app:bootRun
```

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

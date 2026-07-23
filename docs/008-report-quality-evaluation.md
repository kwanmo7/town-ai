# Report Prompt 품질 평가

## 목적

단위 Test는 Prompt 파일 존재 여부, JSON 파싱, 제목 순서, 글자 수와 같은 형식 계약을 검증한다. 실제 Report의 분석 품질은 모델 응답을 확인해야 하므로 별도의 `promptEval` Gradle Task로 평가한다.

이 Task는 실제 서비스와 같은 흐름을 사용한다.

```text
현실형 Fixture
→ OpenAiReportClient
→ OpenAI Responses API
→ ReportContentGenerator 자동 검증
→ 최종 Markdown 저장
→ 수동 품질 평가
```

## 일반 Test와의 분리

- `test`: 실제 OpenAI API를 호출하지 않는다.
- `promptEval`: `prompt-eval` Tag가 붙은 실제 API 평가만 실행한다.
- `check`와 CI에는 `promptEval`을 포함하지 않는다.
- `promptEval`은 기본 4회 호출하며, 출력 교정 재시도가 모두 발생하면 최대 8회 호출할 수 있다.

## 실행 준비

OpenAI API Key는 Git, Test Resource, Gradle 파일 또는 결과 파일에 저장하지 않는다. PowerShell에서 현재 Terminal Process의 환경변수로만 전달할 수 있다.

```powershell
cd backend
$env:OPENAI_API_KEY = "실제 API Key"
.\gradlew.bat :app:promptEval
Remove-Item Env:OPENAI_API_KEY
```

기본 모델은 애플리케이션과 같은 `gpt-5.4-mini`다. 다른 모델을 비교할 때만 실행 전에 `OPENAI_REPORT_MODEL`을 설정한다.

```powershell
$env:OPENAI_REPORT_MODEL = "비교할 모델 ID"
.\gradlew.bat :app:promptEval
Remove-Item Env:OPENAI_REPORT_MODEL
```

### Windows PowerShell 5.1 한글 출력

PowerShell 5.1의 기본 Code Page와 Gradle JVM의 UTF-8 출력이 다르면 Test 이름의 한글이 깨질 수 있다. 현재 Terminal에서 다음 설정을 적용한 후 Gradle을 실행한다.

```powershell
chcp 65001 | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding $false
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
```

프로젝트의 `backend/gradle.properties`와 `backend/app/build.gradle`은 Gradle Daemon, Java Compile 및 Test JVM의 문자 인코딩을 UTF-8로 고정한다.

## Fixture 구성

| 파일 | 검증 목적 |
|---|---|
| `summary.json` | SQL 통계와 Top 5를 바꾸지 않고 짧게 해석하는지 확인 |
| `area.json` | 세 번의 방문 변화, 점수와 memo의 차이, 시간대별 재확인 필요성을 다루는지 확인 |
| `compare.json` | 세 지역의 서로 다른 강점과 Trade-off를 절대 순위 없이 비교하는지 확인 |
| `all.json` | 네 지역을 누락하지 않고, 방문 1회인 지역의 데이터 한계를 구분하는지 확인 |

Fixture 위치:

```text
backend/app/src/test/resources/prompt-eval/fixtures/
```

## 결과 확인

실행 결과는 다음 위치에 생성된다.

```text
backend/app/build/prompt-eval/
├── run-info.md
├── evaluation-sheet.md
├── summary.md
├── area.md
├── compare.md
└── all.md
```

`ReportContentGenerator`가 다음 항목을 자동 검증한다.

- 필수 제목과 제목 순서
- 입력 Area의 누락, 중복 및 순서
- Structured Output JSON 파싱
- SUMMARY·COMPARE 글자 수
- 확인 체크리스트 개수
- Markdown 빈 결과와 코드 블록 출력

자동 검증을 통과한 뒤 `evaluation-sheet.md`에서 입력 충실성, 분석 유용성, 균형성, 확인 항목의 실행 가능성 및 가독성을 1점부터 5점까지 평가한다.

## 합격 기준

- 입력에 없는 객관적 사실을 단정한 내용이 없어야 한다.
- 적용 가능한 모든 평가 항목이 3점 이상이어야 한다.
- Report별 평균은 4.0 이상이어야 한다.
- Prompt Version을 최종 확정하기 전 동일 Fixture를 최소 3회 실행해 결과 편차도 확인한다.

평가 결과가 기준을 충족하지 않으면 Prompt를 수정하고 `{type}-v2`처럼 Prompt Version을 올릴지 검토한다. 단순 문구 보완 단계에서는 V1 개발 중인 동안 같은 Version을 유지할 수 있지만, 이미 저장된 Report의 재현 기준이 된 이후에는 기존 Prompt를 덮어쓰지 않는다.

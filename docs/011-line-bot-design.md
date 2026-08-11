# LINE Bot 화면 및 상호작용 설계

## 1. 목적

Town AI V1의 LINE Bot은 개인 사용자가 모바일 채팅에서 방문 평가를 자연어로
등록하고, 저장된 Area와 Visit을 기반으로 AI Report를 생성·조회하는 보조
Client이다. 모든 저장과 검증은 Backend가 담당하며 LINE 메시지는 입력과 결과
확인을 위한 UI로만 사용한다.

화면 기준본은 `linebotdesign/`에서 관리한다.

## 2. LINE Platform 제약

- LINE은 사용자가 채팅방을 열 때 별도의 Webhook Event를 보내지 않는다.
- Welcome Message는 친구 추가 또는 차단 해제 시에만 전송한다.
- 이후 기능 진입점은 모바일 채팅방 하단의 기본 Rich Menu로 제공한다.
- Rich Menu는 LINE PC에서 표시되지 않으므로 각 결과 화면에 메뉴 이동 버튼을
  함께 제공한다.
- Bot은 일반 파일을 발신하는 Message Type을 지원하지 않으므로 Markdown
  Report는 안전한 HTTPS 보기·다운로드 URL로 전달한다.

## 3. 전체 사용자 흐름

```text
최초 친구 추가
→ Welcome Message
→ 기본 Rich Menu 표시

방문 기록 등록
→ 자연어 입력 안내
→ 사용자가 평가 전송
→ OpenAI Visit Parser
→ 기존 Area 또는 신규 Area 위치 후보가 포함된 Draft
→ 저장 가능한 Draft 또는 재입력 안내
→ 저장·수정·취소
→ 수정 선택 시 바꿀 내용만 입력하고 수정된 Draft 재확인
→ 처리 결과와 다음 메뉴

리포트 조회
→ AREA·COMPARE·SUMMARY·ALL 선택
→ 필요한 경우 Area 선택
→ 분석 가능한 Visit 존재 여부 확인
→ 현재 Prompt 입력과 같은 기존 Report 확인
→ 있으면 기존 결과 카드
→ 없으면 Report 생성 중 안내
→ OpenAI Report 생성 및 GCS 저장
→ Report 결과 카드
→ HTTPS 보기 또는 Markdown 다운로드
```

## 4. 화면 정의

| 화면 | 파일 | 주요 동작 |
|---|---|---|
| Welcome | `welcome-message.json` | 최초 사용 안내 |
| 메인 메뉴 | `main-menu-message.json` | 등록·조회 선택 |
| 등록 안내 | `visit-registration-guide-message.json` | 자연어 입력 방법 안내 |
| Draft 확인 | `visit-draft-message.json` | 저장·부분 수정·취소 |
| Draft 보완 | `visit-draft-needs-input-message.json` | 누락값 보완·메뉴 이동 |
| Visit 저장 결과 | `visit-save-result-message.json` | 계속 등록·메뉴 이동 |
| Report Type | `report-type-menu-message.json` | 네 가지 Report Type 선택 |
| AREA 선택 | `report-area-list-message.json` | Area별 Report 생성 |
| COMPARE 선택 | `report-compare-selection-message.json` | Area 2~5개 선택·생성 |
| Report 생성 중 | `report-generating-message.json` | 비동기 처리 안내 |
| Report 결과 | `report-result-message.json` | 보기·다운로드·리포트 조회·메인 메뉴 |
| Area 없음 | `report-no-area-message.json` | Area 등록 필요 안내 |
| Visit 없음 | `report-no-visits-message.json` | 방문 기록 등록 안내 |

Flex Message 파일은 Simulator에서 바로 확인할 수 있도록 `bubble` 또는
`carousel` Container를 최상위로 사용한다. Backend는 전송 시 `type=flex`,
`altText`, `contents`를 포함하는 LINE Message Object로 변환한다.

## 5. Rich Menu

기본 Rich Menu는 좌우 2분할로 구성한다.

```text
2500 x 843
┌────────────────────┬────────────────────┐
│   방문 기록 등록    │     리포트 조회     │
│  x=0, width=1250   │ x=1250, width=1250 │
└────────────────────┴────────────────────┘
```

- 정의: `linebotdesign/rich-menu.json`
- 이미지: `linebotdesign/rich-menu.jpg`
- 형식: JPEG
- 크기: `2500 x 843`
- 파일 제한: 1MB 이하
- 기본 표시: `selected=true`
- Chat Bar: `기능 메뉴`

Rich Menu Object 생성, 이미지 업로드, 기본 Rich Menu 설정 순서로 적용한다.
이미지가 설정된 Rich Menu의 이미지는 교체할 수 없으므로 디자인 변경 시 새 Rich
Menu Object를 생성한다.

## 6. Postback 규약

Postback Data는 URL Query와 같은 `key=value&key=value` 형식을 사용한다.
Backend Parser는 파라미터 순서에 의존하는 정규식 대신 Key 기반으로 해석한다.

| action | 추가 값 | 의미 |
|---|---|---|
| `menu` | `target=main` | 메인 메뉴 표시 |
| `menu` | `target=visit-register` | Visit 등록 안내 표시 |
| `menu` | `target=report` | Report Type 메뉴 표시 |
| `confirm` | `draftId` | Draft 확인 및 Visit 저장 |
| `edit` | `draftId` | Draft 수정 대기 상태를 먼저 저장하고 입력 안내 전송 |
| `cancel` | `draftId` | Draft 취소 |
| `report-type` | `reportType` | Report Type에 따른 다음 화면 결정 |
| `compare-toggle` | `areaId`, `selectedAreaIds` | 비교 대상 선택·해제 |
| `report-generate` | `reportType`, `areaId` 또는 `areaIds` | Report 생성 |

Postback Data는 화면 상태 표현일 뿐 신뢰 가능한 권한 정보가 아니다. Backend는
다음을 다시 검증한다.

- `LINE_ALLOWED_USER_ID`와 Event User ID 일치
- Draft 소유자·상태·만료 및 필수 값
- 기존 Area 존재·Soft Delete 여부 또는 신규 Area의 이름·도도부현·시구정촌
- 대상 Area에 Visit 존재 여부
- 중복 Area 제거
- AREA 1개, COMPARE 2~5개, SUMMARY·ALL 대상 ID 없음

오래된 비교 선택 메시지를 다시 누를 수 있으므로 `selectedAreaIds`는 반드시 현재
요청에서 재검증하고, 필요하면 서버 측 선택 Session으로 교체한다.

## 7. Report 흐름

### AREA

활성 Area 중 Visit이 한 건 이상 있는 항목을 Carousel로 보여준다. Area를 선택하면
`AREA` Report를 생성한다.

### COMPARE

활성 Area 중 Visit이 있는 항목에서 2개 이상 5개 이하를 선택한다. 각 선택 변경 후
새 선택 상태 메시지를 보내고, 선택 완료 시 `COMPARE` Report를 생성한다.

### SUMMARY 및 ALL

별도 Area 선택 없이 바로 생성한다. `SUMMARY`는 SQL 통계 중심의 짧은 AI Comment,
`ALL`은 모든 대상 Area의 상세 AI 분석이라는 기존 Report 정책을 유지한다.
단, 활성 Visit이 한 건도 없으면 생성 중 화면과 AI 호출 없이 방문 기록 등록 안내를
표시한다.

### 결과 전달

사전 검증을 통과하면 Report Type, OpenAI 모델, Prompt Version, 대상 Area 순서와 실제 Prompt 입력의
SHA-256 지문으로 기존 결과를 먼저 조회한다. 같은 지문이 있으면 생성 중 안내와 OpenAI
호출 없이 `기존 리포트` 제목과 원래 생성일이 포함된 결과를 Push한다. 지문이 없을 때만 진행 안내를 먼저 Push하고 생성과
GCS 저장이 완료되면 결과 Flex Message를 Push한다.

AREA는 해당 Area의 실제 입력, COMPARE는 선택한 2~5개 Area의 순서와 입력을 기준으로
재사용한다. SUMMARY와 ALL은 전체 입력을 기준으로 하므로 신규 Area·Visit 또는 기존
데이터 변경이 있으면 새로 생성한다. Prompt Version 변경도 새 생성 조건이다.

결과 카드에는 다음 정보만 표시한다.

- 사용자용 Report Type 이름
- 대상 Area 이름
- 생성일
- Report 보기 버튼
- Markdown 다운로드 버튼
- 다른 리포트 조회 버튼
- 메인 메뉴 버튼

DB ID와 GCS 내부 Object 경로는 사용자 메시지에 표시하지 않는다. URL은 추측
가능한 Report ID만으로 접근할 수 없도록 인증 또는 만료 정책을 Backend 구현 시
확정한다.

## 8. Backend 반영 범위

Backend는 Follow, 자연어 Text Message와 메뉴·Draft·Report Postback을 처리한다.
메인 메뉴, 등록 안내, Draft, Report 유형·대상 선택과 생성 결과 화면을 동적 Flex
Message로 만들며 COMPARE는 2~5개 선택을 검증한다. LINE Report는 Webhook Event
ID를 UNIQUE 멱등 Key로 저장해 Cloud Tasks 재처리 시 기존 결과를 재사용한다. 서로 다른
사용자 요청이라도 현재 Prompt 입력 지문이 같으면 기존 Report와 GCS 파일을 재사용한다.
기존 Area가 없으면 Parser의 신규 위치 후보를 Draft에 보존하고, 사용자가 저장을
확인한 시점에 Area와 Visit을 하나의 Transaction으로 생성한다.
사용자는 기본적으로 지역명만 입력할 수 있으며, Parser가 위치를 명확히 특정할 수
있으면 도도부현·시구정촌·인접 역을 보완한다. 보완된 위치는 저장 전에 Draft에서
사용자가 확인하고, 동명 지역처럼 모호한 경우에만 위치 추가 입력을 요구한다.
최초 Parser 결과에 신규 Area의 도도부현 또는 시구정촌이 없으면 Backend가 위치
보완 요청을 한 번 더 수행한다. `광역권`, 문자열 `null` 같은 자리표시자는 위치로
표시하거나 저장하지 않는다. 보완 출력에서는 같은 지역명의 위치만 사용하고 최초
검증이 끝난 방문일·점수·메모는 Backend가 그대로 보존한다.
Draft의 수정 버튼은 수정 대기 상태가 저장된 뒤 입력 안내를 보낸다. 사용자는 안내를
확인하고 `접근성 8로 수정`, `방문일은 7월 26일`, `메모에 공원이 가까웠다고 추가`
같은 부분 입력을 보낸다. 수정 Text Message는 AI 호출 전에 원본 Draft를 점유하며,
Backend는 Parser의 `changedFields`만 기존 값에 병합한다. 사용자는 새 Draft를 다시
확인한 뒤 저장한다.

Visit 저장 완료 화면은 `계속 등록`과 `메인 메뉴` 버튼을 제공한다. 저장 직후 전체
메인 메뉴를 자동으로 다시 보내 대화가 길어지는 대신 사용자가 다음 동작을 직접
선택한다.

Production 적용 상태와 남은 작업은 다음과 같다.

1. Rich Menu 생성·이미지 업로드·기본 메뉴 설정 완료
2. 실제 모바일에서 등록·부분 수정·저장과 Report 조회 버튼 검증 완료
3. 기존 Report 재사용과 분석 입력 변경 시 재생성 검증 완료
4. 추측 가능한 Report ID를 보호할 인증 또는 만료 URL 정책 구현 필요
5. 오래된 메시지의 장기 재클릭 검증 필요

V1 Welcome Message는 LINE Official Account Manager의 Greeting Message로
설정한다. Backend는 이어서 수신한 Follow Event를 저장·비동기 처리하고 메인 메뉴
Flex Message를 Push한다. 차단 해제에서도 같은 흐름을 멱등하게 처리한다.
Webhook 처리와 별도로 같은 메시지가 중복되지 않도록 Official Account Manager의
`자동 응답 메시지`는 끄고, `Greeting Message`와 `Webhook`만 켠다. 채팅 입력에 대한
응답은 Backend Messaging API가 전담한다.

## 9. 검증 기준

2026-08-11 Production 수정본을 실제 LINE 모바일 대화에서 사용해 Rich Menu,
Visit 등록·부분 수정·저장, 공개 HTTPS Report 보기·다운로드, 기존 Report 재사용,
데이터 변경 시 재생성과 메인 메뉴 복귀를 확인했다. 세부 기록은
`010-production-gcp-integration.md`에서 관리한다.

- 모든 JSON이 UTF-8로 파싱되는지 확인
- Flex Message Simulator에서 Bubble·Carousel 표시 확인
- 긴 Area 이름과 Memo의 줄바꿈 확인
- Postback Data가 LINE 300자 및 Backend 저장 길이 이내인지 확인
- Rich Menu 이미지가 JSON 영역, 형식, 해상도 및 1MB 제한과 일치하는지 확인
- LINE 모바일 단말에서 Rich Menu와 모든 버튼 확인
- 저장 완료 화면의 `계속 등록`과 `메인 메뉴` 이동 확인
- Production `LINE_REPORT_BASE_URL`이 Cloud Run HTTPS Origin인지 확인하고 Report
  보기·다운로드 버튼이 `localhost`가 아닌 공개 URL을 여는지 확인
- 오래된 메시지 재클릭과 Cloud Tasks 재시도에서 중복 Visit·Report가 생성되지
  않는지 확인
- 활성 Visit 0건에서 SUMMARY·ALL이 생성 중 화면이나 AI 호출로 넘어가지 않는지 확인
- 기존 Area 0건에서 신규 Area 후보를 확인·저장해 Area와 Visit이 함께 생기는지 확인
- 허용되지 않은 LINE User와 조작된 ID가 거부되는지 확인

## 10. 공식 참고 문서

- [LINE Rich Menu](https://developers.line.biz/en/docs/messaging-api/rich-menus-overview/)
- [Rich Menu API](https://developers.line.biz/en/reference/messaging-api/nojs/#rich-menu)
- [Flex Message Simulator](https://developers.line.biz/flex-simulator/)
- [LINE Webhook Event](https://developers.line.biz/en/docs/messaging-api/receiving-messages/)
- [LINE Bot 구축과 자동 응답 설정](https://developers.line.biz/en/docs/messaging-api/building-bot/)

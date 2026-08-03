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
→ 저장 가능한 Draft 또는 재입력 안내
→ 저장·취소
→ 처리 결과와 다음 메뉴

리포트 조회
→ AREA·COMPARE·SUMMARY·ALL 선택
→ 필요한 경우 Area 선택
→ Report 생성 중 안내
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
| Draft 확인 | `visit-draft-message.json` | 저장·취소 |
| Draft 보완 | `visit-draft-needs-input-message.json` | 재입력·메뉴 이동 |
| Visit 저장 결과 | `visit-save-result-message.json` | 계속 등록·메뉴 이동 |
| Report Type | `report-type-menu-message.json` | 네 가지 Report Type 선택 |
| AREA 선택 | `report-area-list-message.json` | Area별 Report 생성 |
| COMPARE 선택 | `report-compare-selection-message.json` | Area 2~5개 선택·생성 |
| Report 생성 중 | `report-generating-message.json` | 비동기 처리 안내 |
| Report 결과 | `report-result-message.json` | 보기·다운로드 |
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
| `visit-input` | `draftId` | 재입력용 키보드 열기 |
| `confirm` | `draftId` | Draft 확인 및 Visit 저장 |
| `cancel` | `draftId` | Draft 취소 |
| `report-type` | `reportType` | Report Type에 따른 다음 화면 결정 |
| `compare-toggle` | `areaId`, `selectedAreaIds` | 비교 대상 선택·해제 |
| `report-generate` | `reportType`, `areaId` 또는 `areaIds` | Report 생성 |

Postback Data는 화면 상태 표현일 뿐 신뢰 가능한 권한 정보가 아니다. Backend는
다음을 다시 검증한다.

- `LINE_ALLOWED_USER_ID`와 Event User ID 일치
- Draft 소유자·상태·만료 및 필수 값
- Area 존재 및 Soft Delete 여부
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

### 결과 전달

Report 생성 작업이 시작되면 진행 안내를 먼저 Push하고, 생성과 GCS 저장이
완료되면 결과 Flex Message를 Push한다.

결과 카드에는 다음 정보만 표시한다.

- 사용자용 Report Type 이름
- 대상 Area 이름
- 생성일
- Report 보기 버튼
- Markdown 다운로드 버튼

DB ID와 GCS 내부 Object 경로는 사용자 메시지에 표시하지 않는다. URL은 추측
가능한 Report ID만으로 접근할 수 없도록 인증 또는 만료 정책을 Backend 구현 시
확정한다.

## 8. Backend 반영 범위

현재 Backend는 자연어 Text Message 수신, Draft 생성, Flex Message 초안 표시와
`confirm`·`cancel` Postback을 지원한다. 고정 메뉴와 안내 화면을 생성하는 Flex
Factory도 구현했지만 아직 Webhook Handler에는 연결하지 않았다. 디자인을 모두 실제
적용하려면 다음 변경이 필요하다.

1. Webhook Selector가 메뉴와 Report Postback을 허용하도록 확장
2. Postback 정규식 Parser를 Key 기반 Command Parser로 교체
3. 메뉴·Report Type·Area 선택 Command와 Handler 구현
4. Area 목록 및 Visit 존재 여부 조회 Use Case 추가
5. 비교 선택 상태 처리 및 2~5개 검증
6. LINE용 Report 생성 흐름과 완료 Push 구현
7. Rich Menu 생성·이미지 업로드·기본 메뉴 설정
8. 안전한 Report 보기·다운로드 URL 구현

Flex Message 공통 모델, Draft 화면 Factory와 고정 메뉴 Factory는 구현을 완료했다.
메뉴 Factory의 버튼은 1~3번이 완료되기 전까지 실제 사용자 흐름에 연결하지 않는다.

V1 Welcome Message는 LINE Official Account Manager의 Greeting Message로
설정하고 기본 Rich Menu를 함께 사용한다. 따라서 단순한 Welcome 처리를 위해
Backend에 Follow Event 저장 흐름을 추가하지 않는다. 향후 사용자별 Onboarding이
필요해질 때 Follow Event 처리를 별도로 검토한다.

## 9. 검증 기준

- 모든 JSON이 UTF-8로 파싱되는지 확인
- Flex Message Simulator에서 Bubble·Carousel 표시 확인
- 긴 Area 이름과 Memo의 줄바꿈 확인
- Postback Data가 LINE 300자 및 Backend 저장 길이 이내인지 확인
- Rich Menu 이미지가 JSON 영역, 형식, 해상도 및 1MB 제한과 일치하는지 확인
- LINE 모바일 단말에서 Rich Menu와 모든 버튼 확인
- 오래된 메시지 재클릭과 Cloud Tasks 재시도에서 중복 Visit·Report가 생성되지
  않는지 확인
- 허용되지 않은 LINE User와 조작된 ID가 거부되는지 확인

## 10. 공식 참고 문서

- [LINE Rich Menu](https://developers.line.biz/en/docs/messaging-api/rich-menus-overview/)
- [Rich Menu API](https://developers.line.biz/en/reference/messaging-api/nojs/#rich-menu)
- [Flex Message Simulator](https://developers.line.biz/flex-simulator/)
- [LINE Webhook Event](https://developers.line.biz/en/docs/messaging-api/receiving-messages/)

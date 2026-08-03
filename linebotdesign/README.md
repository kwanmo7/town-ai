# Town AI LINE Bot 디자인

이 디렉터리는 LINE Bot 메시지와 Rich Menu의 화면 기준본을 관리한다.
JSON은 실제 데이터 대신 예시 값을 사용한 디자인 샘플이며, Backend에서는
동일한 구조에 DB 및 처리 결과를 동적으로 넣어 LINE Messaging API로 전송한다.

## 파일 구성

| 파일 | 형식 | 용도 |
|---|---|---|
| `welcome-message.json` | Text Message | 최초 친구 추가 시 안내 |
| `main-menu-message.json` | Flex Bubble | 방문 기록 등록·리포트 조회 선택 |
| `visit-registration-guide-message.json` | Flex Bubble | 자연어 방문 평가 입력 안내 |
| `visit-draft-message.json` | Flex Bubble | 저장 가능한 Visit Draft 확인 |
| `visit-draft-needs-input-message.json` | Flex Bubble | 필수 값 누락 및 재입력 안내 |
| `visit-save-result-message.json` | Flex Bubble | Visit 저장 완료 안내 |
| `report-type-menu-message.json` | Flex Bubble | AREA·COMPARE·SUMMARY·ALL 선택 |
| `report-area-list-message.json` | Flex Carousel | AREA Report 대상 선택 |
| `report-compare-selection-message.json` | Flex Bubble | COMPARE 대상 2~5개 선택 |
| `report-generating-message.json` | Text Message | 비동기 Report 생성 진행 안내 |
| `report-result-message.json` | Flex Bubble | Report 보기·다운로드 안내 |
| `report-no-area-message.json` | Flex Bubble | 활성 Area가 없는 상태 안내 |
| `report-no-visits-message.json` | Flex Bubble | Report 대상 Visit이 없는 상태 안내 |
| `rich-menu.json` | Rich Menu Object | 좌우 터치 영역과 Postback 정의 |
| `rich-menu.jpg` | JPEG | 모바일 채팅방 하단 Rich Menu 이미지 |

## 사용자 흐름

```text
최초 친구 추가
→ Welcome Message와 기본 Rich Menu

방문 기록 등록
→ 입력 안내
→ 자연어 입력
→ Draft 확인 또는 재입력
→ 저장 또는 취소

리포트 조회
→ Report Type 선택
→ Area 또는 비교 대상 선택
→ 생성 중 안내
→ 보기 또는 다운로드
```

LINE은 사용자가 채팅방을 단순히 열었을 때 Webhook을 보내지 않는다. Welcome
Message는 최초 친구 추가 또는 차단 해제 시에만 사용하고, 이후 기능 진입점은
기본 Rich Menu와 메시지의 메뉴 버튼으로 제공한다.

## Flex Message 확인

Flex 디자인 JSON은 LINE Flex Message Simulator에 바로 붙여 넣을 수 있도록
최상위가 `bubble` 또는 `carousel`인 Container 형태로 저장한다.

1. [Flex Message Simulator](https://developers.line.biz/flex-simulator/)를 연다.
2. `View as JSON`을 선택한다.
3. 확인할 JSON 전체를 붙여 넣고 적용한다.
4. 모바일 환경에서도 줄바꿈, 버튼 및 긴 메모를 확인한다.

실제 Messaging API 전송 시에는 Backend가 Container를 다음 Message Object로
감싼다.

```json
{
  "type": "flex",
  "altText": "방문 기록 초안을 확인해주세요.",
  "contents": {
    "type": "bubble"
  }
}
```

`welcome-message.json`과 `report-generating-message.json`은 일반 Text Message이며
Flex Message Simulator 대상이 아니다.

## 동적 값

다음 값은 화면 확인용 예시이므로 Backend에서 실제 값으로 교체한다.

- Area ID, 이름, 위치 및 Visit 개수
- 방문일, 다섯 가지 점수, 메모 및 경고
- Draft ID와 선택된 비교 Area ID 목록
- Report ID, Type, 대상 Area 및 생성일
- Report 조회·다운로드 URL

Report URL은 현재 화면 확인용 주소다. Production 연동에서는 추측 가능한 Report
ID만으로 접근할 수 없는 인증 또는 만료 URL 정책을 적용한다.

## Postback 규칙

| 기능 | 예시 |
|---|---|
| 메인 메뉴 | `action=menu&target=main` |
| Visit 등록 | `action=menu&target=visit-register` |
| Report 메뉴 | `action=menu&target=report` |
| Draft 저장·취소 | `action=confirm&draftId=10`, `action=cancel&draftId=10` |
| Report Type | `action=report-type&reportType=AREA` |
| AREA 생성 | `action=report-generate&reportType=AREA&areaId=1` |
| 비교 선택 | `action=compare-toggle&areaId=1&selectedAreaIds=1,2` |
| COMPARE 생성 | `action=report-generate&reportType=COMPARE&areaIds=1,2` |

Backend는 전달된 ID와 선택 상태를 신뢰하지 않고 활성 Area, Visit 존재 여부,
중복, 소유자 및 Report Type별 대상 개수를 다시 검증한다.

## Rich Menu

`rich-menu.jpg`는 `2500 x 843`, JPEG, 1MB 이하이며 `rich-menu.json`의 좌우
`1250 x 843` 터치 영역과 일치한다.

Messaging API 등록 순서는 다음과 같다.

1. `rich-menu.json`으로 Rich Menu Object 생성
2. 반환된 Rich Menu ID에 `rich-menu.jpg`를 `image/jpeg`로 업로드
3. 생성한 Rich Menu를 기본 Rich Menu로 설정

Rich Menu는 LINE 모바일 앱에서 표시되며 LINE PC에서는 표시되지 않는다.

## 구현 상태

이 디렉터리는 화면 설계 기준본이다. 현재 Backend는 자연어 Text Message, Draft
Flex 화면, Follow와 메뉴·Report Postback을 처리한다. 화면의 Area·Visit·Report
값은 Backend Factory가 DB 결과로 동적 생성한다. Rich Menu 등록과 안전한 Report
URL은 후속 Production 작업으로 남아 있다.

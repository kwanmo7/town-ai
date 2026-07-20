# 시스템 아키텍쳐 (Architecture)

## 목차
- [시스템 구성](#시스템-구성)
- [Report Generation Sequence](#report-generation-sequence)
- [LINE Visit 입력 흐름](#line-visit-입력-흐름)
- [설계 원칙](#설계-원칙)

## 시스템 구성
![SystemArchitecture](./architecture/Component%20Diagram.png)


## Report Generation Sequence
![ReportGenerationSequence](./architecture/Report%20Generation%20Sequence.png)

## LINE Visit 입력 흐름

Production에서는 LINE Webhook의 외부 요청 처리와 OpenAI 호출을 분리하기 위해 Cloud Tasks를 사용한다.

```text
LINE Platform
→ POST /api/line/webhook
→ 원문 Body 기반 서명 검증
→ 허용된 LINE User 및 이벤트 검증
→ line_webhook_event 저장
→ Cloud Tasks에 webhookEventId 전달
→ 200 OK

Cloud Tasks
→ POST /internal/tasks/line-events/{webhookEventId}
→ OpenAI Visit Parser 호출
→ line_visit_draft 저장
→ LINE Push Message로 Draft와 확인/취소 버튼 전송
→ LINE이 Push 요청을 수락한 후 Webhook Event를 COMPLETED로 전환

사용자 확인 Postback
→ LINE Webhook
→ Cloud Tasks
→ Draft 소유자·상태·만료·필수 값 재검증
→ Visit 저장과 Draft 확정을 하나의 DB Transaction으로 처리
→ LINE Push Message로 저장 결과 전송
→ LINE이 Push 요청을 수락한 후 Webhook Event를 COMPLETED로 전환
```

- Cloud Tasks는 적어도 한 번 전달될 수 있으므로 `webhookEventId`와 Draft 상태를 기준으로 멱등 처리한다.
- Webhook 요청 하나에 여러 이벤트가 포함되면 이벤트별로 DB Row와 Task를 만든다.
- Production Task는 OIDC Token을 사용하며 Backend는 예상 Service Account와 Audience를 검증한다.
- Local에서는 같은 Port를 사용하는 `LocalLineEventDispatcher`로 흐름을 검증하고, Production에서만 `CloudTasksLineEventDispatcher`를 사용한다.
- 비동기 처리 결과는 Reply Token 만료 영향을 받지 않도록 LINE Push Message로 전송한다.
- LINE Push Message는 `webhookEventId`와 메시지 용도로부터 만든 결정적 Retry Key를 사용해 재시도 중 중복 전송을 방지한다.
- LINE Push Message가 `2xx` 또는 이미 수락된 Request ID가 포함된 `409 Conflict`로 확인된 후에만 이벤트를 `COMPLETED`로 전환한다.
- Draft가 이미 저장된 이벤트를 재처리할 때는 OpenAI를 다시 호출하거나 Draft를 다시 INSERT하지 않고 기존 Draft를 재사용한다.
- 애플리케이션 처리 시도가 최대 횟수에 도달하면 이벤트를 `FAILED`로 종료해 `RECEIVED` 상태로 남지 않게 한다.

## 설계 원칙
- React와 LINE Bot은 입력 채널이다.
- Spring Boot가 모든 비즈니스 로직을 담당한다.
- MySQL은 원본 데이터(Source of Truth)이다.
- OpenAI는 자연어 처리와 리포트 생성만 담당한다.
- Google Cloud Storage는 생성된 리포트를 저장한다.
- Cloud Tasks는 LINE Webhook 이벤트의 비동기 전달과 재시도를 담당하며, 처리 멱등성은 MySQL 상태로 보장한다.

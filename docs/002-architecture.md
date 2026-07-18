# 시스템 아키텍쳐 (Architecture)

## 목차
- [시스템 구성](#시스템-구성)
- [Report Generation Sequence](#report-generation-sequence)
- [설계 원칙](#설계-원칙)

## 시스템 구성
![SystemArchitecture](./architecture/Component%20Diagram.png)


## Report Generation Sequence
![ReportGenerationSequence](./architecture/Report%20Generation%20Sequence.png)

## 설계 원칙
- React와 LINE Bot은 입력 채널이다.
- Spring Boot가 모든 비즈니스 로직을 담당한다.
- MySQL은 원본 데이터(Source of Truth)이다.
- OpenAI는 자연어 처리와 리포트 생성만 담당한다.
- Google Cloud Storage는 생성된 리포트를 저장한다.

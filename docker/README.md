# Docker

이 디렉터리는 여러 Service가 공통으로 사용할 Docker Compose나 개발용 Image가 필요할
경우를 위한 예약 경로다.

V1에는 별도의 최상위 Docker 구성이 없다. 현재 사용하는 Container는 다음 위치에서
관리한다.

- Cloud Run Backend Image: `backend/Dockerfile`
- Local Firestore Emulator: `backend/scripts/local-firestore-start.ps1`가 고정된 Firebase CLI Version으로 실행
- Backend Production Build: Developer Connect Cloud Build Trigger의 인라인 구성

따라서 Backend Image를 수정할 때 이 디렉터리에 Dockerfile을 추가하지 않고
`backend/Dockerfile`과 관련 배포 문서를 함께 갱신한다.

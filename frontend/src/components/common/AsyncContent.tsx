interface LoadingPanelProps {
  label?: string
}

/** 데이터 조회 중임을 화면과 보조 기술에 함께 알리는 공통 Panel이다. */
export function LoadingPanel({ label = '데이터를 불러오는 중입니다.' }: LoadingPanelProps) {
  return (
    <div className="state-panel" role="status">
      <span className="loader" aria-hidden="true" />
      <p>{label}</p>
    </div>
  )
}

interface ErrorPanelProps {
  error: Error
  onRetry: () => void
}

/** 조회 실패 메시지와 선택적 재시도 동작을 제공하는 공통 Panel이다. */
export function ErrorPanel({ error, onRetry }: ErrorPanelProps) {
  return (
    <div className="state-panel state-panel--error" role="alert">
      <span className="state-panel__symbol" aria-hidden="true">!</span>
      <div>
        <strong>정보를 불러오지 못했습니다.</strong>
        <p>{error.message}</p>
      </div>
      <button className="button button--secondary" type="button" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  )
}

interface EmptyStateProps {
  title: string
  description: string
}

/** 조회는 성공했지만 표시할 데이터가 없을 때 사용하는 공통 안내 영역이다. */
export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <span className="empty-state__mark" aria-hidden="true">T</span>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  )
}

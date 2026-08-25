interface LoadingPanelProps {
  label?: string
}

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

export function EmptyState({ title, description }: EmptyStateProps) {
  return (
    <div className="empty-state">
      <span className="empty-state__mark" aria-hidden="true">T</span>
      <h2>{title}</h2>
      <p>{description}</p>
    </div>
  )
}

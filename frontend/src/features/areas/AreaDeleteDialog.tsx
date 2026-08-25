import { useEffect } from 'react'
import type { AreaSummary } from '../../types/api'

interface AreaDeleteDialogProps {
  area: AreaSummary
  isDeleting: boolean
  error: string | null
  onCancel: () => void
  onConfirm: () => void
}

export function AreaDeleteDialog({
  area,
  isDeleting,
  error,
  onCancel,
  onConfirm,
}: AreaDeleteDialogProps) {
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isDeleting) onCancel()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [isDeleting, onCancel])

  return (
    <div className="dialog-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !isDeleting) onCancel()
    }}>
      <section
        className="dialog dialog--compact"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="delete-area-title"
        aria-describedby="delete-area-description"
      >
        <span className="danger-mark" aria-hidden="true">!</span>
        <h2 id="delete-area-title">{area.name} 지역을 삭제할까요?</h2>
        <p id="delete-area-description">
          목록과 새로운 방문 기록·리포트 대상에서는 제외됩니다. 기존 방문 기록은
          원본 데이터 보존을 위해 유지됩니다.
        </p>
        {error && <p className="form-alert" role="alert">{error}</p>}
        <footer className="dialog__footer">
          <button
            className="button button--secondary"
            type="button"
            onClick={onCancel}
            disabled={isDeleting}
          >
            취소
          </button>
          <button
            className="button button--danger"
            type="button"
            onClick={onConfirm}
            disabled={isDeleting}
          >
            {isDeleting ? '삭제 중...' : '삭제'}
          </button>
        </footer>
      </section>
    </div>
  )
}

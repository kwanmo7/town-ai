import { useEffect } from 'react'
import type { VisitSummary } from '../../types/api'

interface VisitDeleteDialogProps {
  visit: VisitSummary
  isDeleting: boolean
  error: string | null
  onCancel: () => void
  onConfirm: () => void
}

export function VisitDeleteDialog({
  visit,
  isDeleting,
  error,
  onCancel,
  onConfirm,
}: VisitDeleteDialogProps) {
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
        aria-labelledby="delete-visit-title"
        aria-describedby="delete-visit-description"
      >
        <span className="danger-mark" aria-hidden="true">!</span>
        <h2 id="delete-visit-title">{visit.area.name} 방문 기록을 삭제할까요?</h2>
        <p id="delete-visit-description">
          {visit.visitDate} 기록은 복구할 수 없으며 이후 통계와 새 리포트에서 제외됩니다.
          이미 생성된 리포트는 변경되지 않습니다.
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

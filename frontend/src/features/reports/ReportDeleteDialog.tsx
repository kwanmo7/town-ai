import { useEffect } from 'react'
import { reportTypeLabels } from '../../lib/format'
import type { ReportSummary } from '../../types/api'

interface ReportDeleteDialogProps {
  report: ReportSummary
  isDeleting: boolean
  error: string | null
  onCancel: () => void
  onConfirm: () => void
}

/** Report Metadata와 연결된 Markdown 삭제를 최종 확인하는 Dialog이다. */
export function ReportDeleteDialog({
  report,
  isDeleting,
  error,
  onCancel,
  onConfirm,
}: ReportDeleteDialogProps) {
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
        aria-labelledby="delete-report-title"
        aria-describedby="delete-report-description"
      >
        <span className="danger-mark" aria-hidden="true">!</span>
        <h2 id="delete-report-title">
          {reportTypeLabels[report.reportType]} #{report.id}을 삭제할까요?
        </h2>
        <p id="delete-report-description">
          저장된 Markdown 파일과 리포트 정보가 함께 삭제되며 복구할 수 없습니다.
          방문 기록과 지역 정보는 삭제되지 않습니다.
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

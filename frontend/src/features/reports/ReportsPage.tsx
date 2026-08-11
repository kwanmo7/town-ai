import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { EmptyState, ErrorPanel, LoadingPanel } from '../../components/common/AsyncContent'
import { PageHeader } from '../../components/common/PageHeader'
import { useAsyncData } from '../../hooks/useAsyncData'
import { ApiError, api } from '../../lib/api'
import { formatDateTime, reportTypeLabels } from '../../lib/format'
import type { ReportSummary, ReportType } from '../../types/api'
import { ReportCreateDialog } from './ReportCreateDialog'
import { ReportDeleteDialog } from './ReportDeleteDialog'

interface ReportFilterOption {
  type: ReportType | null
  label: string
}

const reportFilterOptions: ReportFilterOption[] = [
  { type: null, label: '전체' },
  { type: 'AREA', label: reportTypeLabels.AREA },
  { type: 'COMPARE', label: reportTypeLabels.COMPARE },
  { type: 'SUMMARY', label: reportTypeLabels.SUMMARY },
  { type: 'ALL', label: reportTypeLabels.ALL },
]

export function ReportsPage() {
  const navigate = useNavigate()
  const [selectedType, setSelectedType] = useState<ReportType | null>(null)
  const { data: reports, error, isLoading, reload } = useAsyncData(api.getReports)
  const filteredReports = useMemo(
    () => reports?.filter((report) => (
      selectedType === null || report.reportType === selectedType
    )) ?? [],
    [reports, selectedType],
  )
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
  const [deletingReport, setDeletingReport] = useState<ReportSummary | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    if (!notice) return
    const timeout = window.setTimeout(() => setNotice(null), 4000)
    return () => window.clearTimeout(timeout)
  }, [notice])

  const handleCreated = (report: ReportSummary) => {
    setIsCreateDialogOpen(false)
    navigate(`/reports/${report.id}`)
  }

  const openDeleteDialog = (report: ReportSummary) => {
    setDeletingReport(report)
    setDeleteError(null)
  }

  const closeDeleteDialog = () => {
    if (isDeleting) return
    setDeletingReport(null)
    setDeleteError(null)
  }

  const handleDelete = async () => {
    if (!deletingReport) return
    setIsDeleting(true)
    setDeleteError(null)

    try {
      await api.deleteReport(deletingReport.id)
      setNotice(`${reportTypeLabels[deletingReport.reportType]} #${deletingReport.id}을 삭제했습니다.`)
      setDeletingReport(null)
      await reload()
    } catch (error) {
      setDeleteError(
        error instanceof ApiError ? error.message : '리포트를 삭제하지 못했습니다.',
      )
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="AI REPORTS"
        title="리포트 보관함"
        description="생성 시점의 방문 데이터를 기준으로 작성된 분석 결과를 확인합니다."
        action={(
          <button className="button" type="button" onClick={() => setIsCreateDialogOpen(true)}>
            + 새 리포트
          </button>
        )}
      />

      {notice && (
        <div className="notice" role="status">
          <span aria-hidden="true">✓</span>
          {notice}
          <button type="button" onClick={() => setNotice(null)} aria-label="알림 닫기">×</button>
        </div>
      )}

      <section className="report-toolbar" aria-label="리포트 유형 필터">
        <div className="report-filter" role="group" aria-label="유형 선택">
          {reportFilterOptions.map((option) => (
            <button
              className={selectedType === option.type ? 'is-active' : undefined}
              type="button"
              aria-pressed={selectedType === option.type}
              onClick={() => setSelectedType(option.type)}
              key={option.type ?? 'ALL_TYPES'}
            >
              {option.label}
            </button>
          ))}
        </div>
        <span className="report-result-count" aria-live="polite">
          {error ? '조회 실패' : reports ? `${filteredReports.length}개` : '조회 중'}
        </span>
      </section>

      {isLoading && !reports && <LoadingPanel label="리포트 목록을 불러오고 있습니다." />}
      {error && <ErrorPanel error={error} onRetry={reload} />}
      {reports && filteredReports.length === 0 && !isLoading && (
        <EmptyState
          title={selectedType
            ? `${reportTypeLabels[selectedType]} 리포트가 없습니다.`
            : '생성된 리포트가 없습니다.'}
          description={selectedType
            ? '다른 유형을 선택하거나 새 리포트를 생성해보세요.'
            : '방문 기록을 등록한 후 새 리포트 버튼에서 AI 분석을 시작할 수 있습니다.'}
        />
      )}

      {filteredReports.length > 0 && (
        <section className="report-grid" aria-label="리포트 목록" aria-busy={isLoading}>
          {filteredReports.map((report) => (
            <article className="report-card" key={report.id}>
              <Link className="report-card__link" to={`/reports/${report.id}`}>
                <div className={`report-card__cover report-card__cover--${report.reportType.toLowerCase()}`}>
                  <span>{report.promptVersion}</span>
                  <strong>{reportTypeLabels[report.reportType]}</strong>
                  <small>REPORT #{report.id}</small>
                </div>
                <div className="report-card__body">
                  <span className={`report-type report-type--${report.reportType.toLowerCase()}`}>
                    {reportTypeLabels[report.reportType]}
                  </span>
                  <h2>{reportTypeLabels[report.reportType]} 리포트</h2>
                  <p>{formatDateTime(report.createdAt)}</p>
                  <div>
                    <small>{report.model}</small>
                    <span aria-hidden="true">→</span>
                  </div>
                </div>
              </Link>
              <button
                className="report-card__delete"
                type="button"
                aria-label={`${reportTypeLabels[report.reportType]} 리포트 #${report.id} 삭제`}
                onClick={() => openDeleteDialog(report)}
              >
                삭제
              </button>
            </article>
          ))}
        </section>
      )}

      {isCreateDialogOpen && (
        <ReportCreateDialog
          onClose={() => setIsCreateDialogOpen(false)}
          onCreated={handleCreated}
        />
      )}

      {deletingReport && (
        <ReportDeleteDialog
          report={deletingReport}
          isDeleting={isDeleting}
          error={deleteError}
          onCancel={closeDeleteDialog}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )
}

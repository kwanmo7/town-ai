import { useEffect, useState } from 'react'
import { EmptyState, ErrorPanel, LoadingPanel } from '../../components/common/AsyncContent'
import { PageHeader } from '../../components/common/PageHeader'
import { useAsyncData } from '../../hooks/useAsyncData'
import { ApiError, api } from '../../lib/api'
import { scoreLabels } from '../../lib/format'
import type { VisitSummary } from '../../types/api'
import { VisitDeleteDialog } from './VisitDeleteDialog'
import { VisitDraftDialog } from './VisitDraftDialog'
import { VisitEditDialog } from './VisitEditDialog'

async function loadVisitPage() {
  const [visits, areas] = await Promise.all([
    api.getVisits(),
    api.getAreas(),
  ])
  return { visits, areas }
}

export function VisitsPage() {
  const { data, error, isLoading, reload } = useAsyncData(loadVisitPage)
  const [isDraftOpen, setIsDraftOpen] = useState(false)
  const [editingVisitId, setEditingVisitId] = useState<number | null>(null)
  const [deletingVisit, setDeletingVisit] = useState<VisitSummary | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const visits = data?.visits

  useEffect(() => {
    if (!notice) return
    const timeout = window.setTimeout(() => setNotice(null), 4000)
    return () => window.clearTimeout(timeout)
  }, [notice])

  const handleSaved = async (message: string) => {
    setIsDraftOpen(false)
    setEditingVisitId(null)
    setNotice(message)
    await reload()
  }

  const openDeleteDialog = (visit: VisitSummary) => {
    setDeletingVisit(visit)
    setDeleteError(null)
  }

  const closeDeleteDialog = () => {
    if (isDeleting) return
    setDeletingVisit(null)
    setDeleteError(null)
  }

  const handleDelete = async () => {
    if (!deletingVisit) return
    setIsDeleting(true)
    setDeleteError(null)

    try {
      await api.deleteVisit(deletingVisit.id)
      setNotice(`${deletingVisit.area.name} 방문 기록을 삭제했습니다.`)
      setDeletingVisit(null)
      await reload()
    } catch (error) {
      setDeleteError(
        error instanceof ApiError ? error.message : '방문 기록을 삭제하지 못했습니다.',
      )
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="VISIT LOG"
        title="방문 기록"
        description="느낀 점은 자연어로 남기고 다섯 가지 점수는 직접 선택해 기록합니다."
        action={(
          <button className="button" type="button" onClick={() => setIsDraftOpen(true)}>
            + 방문 기록 등록
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

      {isLoading && !data && <LoadingPanel label="방문 기록을 불러오고 있습니다." />}
      {error && <ErrorPanel error={error} onRetry={reload} />}
      {visits?.length === 0 && !isLoading && (
        <EmptyState
          title="아직 방문 기록이 없습니다."
          description="방문 기록 등록을 눌러 자연어와 직접 선택한 점수로 첫 기록을 남겨보세요."
        />
      )}

      {visits && visits.length > 0 && (
        <section className="visit-timeline" aria-label="방문 기록 목록">
          {visits.map((visit) => {
            const scores = {
              atmosphere: visit.atmosphereScore,
              infra: visit.infraScore,
              clean: visit.cleanScore,
              size: visit.sizeScore,
              access: visit.accessScore,
            }
            const average = Object.values(scores).reduce((sum, value) => sum + value, 0) / 5

            return (
              <article className="visit-card" key={visit.id}>
                <div className="visit-card__date">
                  <strong>{visit.visitDate.slice(8, 10)}</strong>
                  <span>{visit.visitDate.slice(0, 7).replace('-', '.')}</span>
                </div>
                <div className="visit-card__content">
                  <div className="visit-card__heading">
                    <div>
                      <span className="eyebrow">VISIT #{visit.id}</span>
                      <h2>{visit.area.name}</h2>
                    </div>
                    <div className="visit-card__side">
                      <span className="average-badge">
                        <small>평균</small>
                        {average.toFixed(1)}
                      </span>
                      <div className="visit-card__actions">
                        <button type="button" onClick={() => setEditingVisitId(visit.id)}>
                          수정
                        </button>
                        <button
                          className="visit-card__delete"
                          type="button"
                          onClick={() => openDeleteDialog(visit)}
                        >
                          삭제
                        </button>
                      </div>
                    </div>
                  </div>
                  <div className="score-chip-list">
                    {Object.entries(scores).map(([key, value]) => (
                      <span key={key}>
                        {scoreLabels[key as keyof typeof scoreLabels]}
                        <strong>{value}</strong>
                      </span>
                    ))}
                  </div>
                </div>
              </article>
            )
          })}
        </section>
      )}

      {isDraftOpen && (
        <VisitDraftDialog
          areas={data?.areas ?? []}
          onClose={() => setIsDraftOpen(false)}
          onSaved={handleSaved}
        />
      )}

      {editingVisitId !== null && (
        <VisitEditDialog
          key={editingVisitId}
          visitId={editingVisitId}
          areas={data?.areas ?? []}
          onClose={() => setEditingVisitId(null)}
          onSaved={handleSaved}
        />
      )}

      {deletingVisit && (
        <VisitDeleteDialog
          visit={deletingVisit}
          isDeleting={isDeleting}
          error={deleteError}
          onCancel={closeDeleteDialog}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )
}

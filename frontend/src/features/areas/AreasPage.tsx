import { useEffect, useState } from 'react'
import { EmptyState, ErrorPanel, LoadingPanel } from '../../components/common/AsyncContent'
import { PageHeader } from '../../components/common/PageHeader'
import { useAsyncData } from '../../hooks/useAsyncData'
import { ApiError, api } from '../../lib/api'
import type { AreaSummary } from '../../types/api'
import { AreaDeleteDialog } from './AreaDeleteDialog'
import { AreaFormDialog } from './AreaFormDialog'

/** Area 목록 조회와 등록·수정·논리 삭제 Dialog를 조율하는 관리 화면이다. */
export function AreasPage() {
  const { data: areas, error, isLoading, reload } = useAsyncData(api.getAreas)
  const [isFormOpen, setIsFormOpen] = useState(false)
  const [editingArea, setEditingArea] = useState<AreaSummary | null>(null)
  const [deletingArea, setDeletingArea] = useState<AreaSummary | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    if (!notice) return
    const timeout = window.setTimeout(() => setNotice(null), 4000)
    return () => window.clearTimeout(timeout)
  }, [notice])

  const openCreateDialog = () => {
    setEditingArea(null)
    setIsFormOpen(true)
  }

  const openEditDialog = (area: AreaSummary) => {
    setEditingArea(area)
    setIsFormOpen(true)
  }

  const closeFormDialog = () => {
    setIsFormOpen(false)
    setEditingArea(null)
  }

  const handleSaved = async (message: string) => {
    closeFormDialog()
    setNotice(message)
    await reload()
  }

  const openDeleteDialog = (area: AreaSummary) => {
    setDeletingArea(area)
    setDeleteError(null)
  }

  const closeDeleteDialog = () => {
    if (isDeleting) return
    setDeletingArea(null)
    setDeleteError(null)
  }

  const handleDelete = async () => {
    if (!deletingArea) return
    setIsDeleting(true)
    setDeleteError(null)

    try {
      await api.deleteArea(deletingArea.id)
      setNotice(`${deletingArea.name} 지역을 삭제했습니다.`)
      setDeletingArea(null)
      await reload()
    } catch (error) {
      setDeleteError(
        error instanceof ApiError ? error.message : '지역을 삭제하지 못했습니다.',
      )
    } finally {
      setIsDeleting(false)
    }
  }

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="AREAS"
        title="관심 지역"
        description="방문 기록과 리포트의 기준이 되는 지역 정보를 관리합니다."
        action={(
          <button className="button" type="button" onClick={openCreateDialog}>
            + 지역 등록
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

      {isLoading && !areas && <LoadingPanel label="지역 목록을 불러오고 있습니다." />}
      {error && <ErrorPanel error={error} onRetry={reload} />}
      {areas?.length === 0 && !isLoading && (
        <EmptyState
          title="등록된 지역이 없습니다."
          description="지역을 먼저 등록하면 방문 기록과 AI 리포트의 대상으로 사용할 수 있습니다."
        />
      )}

      {areas && areas.length > 0 && (
        <section className="area-grid" aria-label="지역 목록" aria-busy={isLoading}>
          {areas.map((area, index) => (
            <article className="area-card" key={area.id}>
              <div className="area-card__visual">
                <span>{String(index + 1).padStart(2, '0')}</span>
                <strong>{area.name.slice(0, 2)}</strong>
              </div>
              <div className="area-card__body">
                <span className="area-card__location">{area.prefecture}</span>
                <h2>{area.name}</h2>
                <p>{area.city}</p>
                <div className="area-card__station">
                  <span aria-hidden="true">⌖</span>
                  {area.station ?? '인접 역 미등록'}
                </div>
                <div className="area-card__actions">
                  <button type="button" onClick={() => openEditDialog(area)}>수정</button>
                  <button
                    className="area-card__delete"
                    type="button"
                    onClick={() => openDeleteDialog(area)}
                  >
                    삭제
                  </button>
                </div>
              </div>
            </article>
          ))}
        </section>
      )}

      {isFormOpen && (
        <AreaFormDialog
          area={editingArea}
          onClose={closeFormDialog}
          onSaved={handleSaved}
        />
      )}

      {deletingArea && (
        <AreaDeleteDialog
          area={deletingArea}
          isDeleting={isDeleting}
          error={deleteError}
          onCancel={closeDeleteDialog}
          onConfirm={handleDelete}
        />
      )}
    </div>
  )
}

import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { ErrorPanel, LoadingPanel } from '../../components/common/AsyncContent'
import { useAsyncData } from '../../hooks/useAsyncData'
import { ApiError, api } from '../../lib/api'
import { reportTypeLabels } from '../../lib/format'
import type {
  AreaSummary,
  ReportCreateInput,
  ReportSummary,
  ReportType,
  VisitSummary,
} from '../../types/api'

interface ReportCreateDialogProps {
  onClose: () => void
  onCreated: (report: ReportSummary) => void
}

interface ReportTargetData {
  areas: AreaSummary[]
  visits: VisitSummary[]
}

interface ReportTypeOption {
  type: ReportType
  title: string
  description: string
}

const reportTypeOptions: ReportTypeOption[] = [
  {
    type: 'AREA',
    title: '한 지역 분석',
    description: '선택한 한 지역의 방문 기록과 변화 흐름을 상세하게 분석합니다.',
  },
  {
    type: 'COMPARE',
    title: '지역 비교',
    description: '2~5개 지역을 항목별로 비교하고 선택에 도움이 되는 결론을 작성합니다.',
  },
  {
    type: 'SUMMARY',
    title: '전체 요약',
    description: '전체 통계를 중심으로 순위와 짧은 AI 평가를 작성합니다.',
  },
  {
    type: 'ALL',
    title: '전체 상세 분석',
    description: '모든 지역과 방문 기록을 종합해 상세 리포트를 작성합니다.',
  },
]

const progressMessages: Record<ReportType, string> = {
  AREA: '선택한 지역의 방문 기록을 분석하고 있습니다.',
  COMPARE: '선택한 지역들을 비교하고 있습니다.',
  SUMMARY: '전체 통계를 정리하고 AI 평가를 작성하고 있습니다.',
  ALL: '모든 방문 기록을 종합해 상세 분석을 작성하고 있습니다.',
}

async function loadReportTargets(): Promise<ReportTargetData> {
  const [areas, visits] = await Promise.all([api.getAreas(), api.getVisits()])
  return { areas, visits }
}

/** Report 유형별 대상 Area를 검증하고 동기 AI 생성을 실행하는 Dialog이다. */
export function ReportCreateDialog({ onClose, onCreated }: ReportCreateDialogProps) {
  const { data, error, isLoading, reload } = useAsyncData(loadReportTargets)
  const [reportType, setReportType] = useState<ReportType>('AREA')
  const [selectedAreaIds, setSelectedAreaIds] = useState<number[]>([])
  const [validationError, setValidationError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isSubmitting) {
        onClose()
      }
    }
    window.addEventListener('keydown', handleKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [isSubmitting, onClose])

  const visitCountByArea = useMemo(() => {
    const counts = new Map<number, number>()
    for (const visit of data?.visits ?? []) {
      counts.set(visit.area.id, (counts.get(visit.area.id) ?? 0) + 1)
    }
    return counts
  }, [data?.visits])

  const hasVisits = (data?.visits.length ?? 0) > 0
  const needsTargets = reportType === 'AREA' || reportType === 'COMPARE'

  const changeReportType = (nextType: ReportType) => {
    if (isSubmitting) return
    setReportType(nextType)
    setSelectedAreaIds([])
    setValidationError(null)
    setSubmitError(null)
  }

  const toggleArea = (areaId: number) => {
    if (isSubmitting || !visitCountByArea.has(areaId)) return
    setValidationError(null)
    setSubmitError(null)

    if (reportType === 'AREA') {
      // 한 지역 분석은 마지막 선택 하나만 유지하고 비교 분석은 선택 순서를 보존한다.
      setSelectedAreaIds([areaId])
      return
    }

    if (!selectedAreaIds.includes(areaId) && selectedAreaIds.length >= 5) {
      setValidationError('비교 대상은 최대 다섯 곳까지 선택할 수 있습니다.')
      return
    }

    setSelectedAreaIds((current) => {
      if (current.includes(areaId)) {
        return current.filter((id) => id !== areaId)
      }
      return [...current, areaId]
    })
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const nextValidationError = validateTargets(reportType, selectedAreaIds, hasVisits)
    if (nextValidationError) {
      setValidationError(nextValidationError)
      return
    }

    const input: ReportCreateInput = needsTargets
      ? { reportType, areaIds: selectedAreaIds }
      : { reportType }

    setIsSubmitting(true)
    setValidationError(null)
    setSubmitError(null)

    try {
      // 생성 API는 AI 처리가 끝날 때까지 대기하므로 진행 중에는 중복 제출과 닫기를 막는다.
      const report = await api.createReport(input)
      onCreated(report)
    } catch (requestError) {
      setSubmitError(
        requestError instanceof ApiError
          ? requestError.message
          : '리포트를 생성하지 못했습니다. 잠시 후 다시 시도해주세요.',
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="dialog-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !isSubmitting) onClose()
    }}>
      <section
        className="dialog dialog--report"
        role="dialog"
        aria-modal="true"
        aria-labelledby="report-dialog-title"
      >
        <header className="dialog__header">
          <div>
            <span className="eyebrow">NEW AI REPORT</span>
            <h2 id="report-dialog-title">새 리포트 생성</h2>
            <p>분석 유형과 대상을 선택하면 현재 방문 데이터를 기준으로 새 리포트를 작성합니다.</p>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            aria-label="닫기"
          >
            ×
          </button>
        </header>

        {isLoading && !data && <LoadingPanel label="리포트 대상을 불러오고 있습니다." />}
        {error && !data && <ErrorPanel error={error} onRetry={reload} />}

        {data && (
          <form className="report-create-form" onSubmit={handleSubmit}>
            <fieldset className="report-create-section" disabled={isSubmitting}>
              <legend>1. 분석 유형</legend>
              <div className="report-type-options">
                {reportTypeOptions.map((option) => (
                  <button
                    className={`report-type-option${reportType === option.type ? ' is-selected' : ''}`}
                    type="button"
                    key={option.type}
                    aria-pressed={reportType === option.type}
                    onClick={() => changeReportType(option.type)}
                  >
                    <span>{option.type}</span>
                    <strong>{option.title}</strong>
                    <small>{option.description}</small>
                  </button>
                ))}
              </div>
            </fieldset>

            {needsTargets && (
              <fieldset className="report-create-section" disabled={isSubmitting}>
                <legend>2. 대상 지역</legend>
                <div className="report-target-heading">
                  <p>
                    {reportType === 'AREA'
                      ? '방문 기록이 있는 지역 한 곳을 선택해주세요.'
                      : '비교할 지역을 선택해주세요. 선택 순서는 리포트에 그대로 보존됩니다.'}
                  </p>
                  <strong>
                    {reportType === 'AREA'
                      ? `${selectedAreaIds.length}/1`
                      : `${selectedAreaIds.length}/5`}
                  </strong>
                </div>

                <div className="report-target-grid">
                  {data.areas.map((area) => {
                    const visitCount = visitCountByArea.get(area.id) ?? 0
                    const selectedOrder = selectedAreaIds.indexOf(area.id)
                    const isSelected = selectedOrder >= 0
                    const isUnavailable = visitCount === 0

                    return (
                      <button
                        className={`report-target-option${isSelected ? ' is-selected' : ''}`}
                        type="button"
                        key={area.id}
                        disabled={isUnavailable || isSubmitting}
                        aria-pressed={isSelected}
                        onClick={() => toggleArea(area.id)}
                      >
                        <span className="report-target-option__order">
                          {isSelected ? selectedOrder + 1 : '—'}
                        </span>
                        <span>
                          <strong>{area.name}</strong>
                          <small>{area.prefecture} · {area.city}</small>
                        </span>
                        <em>{visitCount > 0 ? `방문 ${visitCount}회` : '방문 기록 없음'}</em>
                      </button>
                    )
                  })}
                </div>
              </fieldset>
            )}

            {!needsTargets && (
              <section className="report-scope-note" aria-label="분석 대상 안내">
                <strong>{reportTypeLabels[reportType]} 분석 대상</strong>
                <p>현재 등록된 {data.areas.length}개 지역의 방문 기록 {data.visits.length}건을 사용합니다.</p>
              </section>
            )}

            {!hasVisits && (
              <p className="form-alert" role="alert">
                분석할 방문 기록이 없습니다. 방문 기록을 먼저 등록해주세요.
              </p>
            )}
            {validationError && <p className="form-alert" role="alert">{validationError}</p>}
            {submitError && <p className="form-alert" role="alert">{submitError}</p>}

            {isSubmitting && (
              <div className="report-generation-progress" role="status" aria-live="polite">
                <span className="report-generation-progress__spinner" aria-hidden="true" />
                <div>
                  <strong>AI 리포트를 생성하고 있습니다.</strong>
                  <p>{progressMessages[reportType]} 완료될 때까지 이 창을 닫지 마세요.</p>
                </div>
              </div>
            )}

            <p className="report-create-caution">
              생성 버튼을 누를 때마다 새 리포트가 저장되며 OpenAI API 사용 비용이 발생할 수 있습니다.
            </p>

            <footer className="dialog__footer">
              <button
                className="button button--secondary"
                type="button"
                onClick={onClose}
                disabled={isSubmitting}
              >
                취소
              </button>
              <button className="button" type="submit" disabled={isSubmitting || !hasVisits}>
                {isSubmitting ? '생성 중...' : `${reportTypeLabels[reportType]} 생성`}
              </button>
            </footer>
          </form>
        )}
      </section>
    </div>
  )
}

function validateTargets(
  reportType: ReportType,
  selectedAreaIds: number[],
  hasVisits: boolean,
): string | null {
  if (!hasVisits) return '분석할 방문 기록이 없습니다.'
  if (reportType === 'AREA' && selectedAreaIds.length !== 1) {
    return '분석할 지역 한 곳을 선택해주세요.'
  }
  if (reportType === 'COMPARE' && selectedAreaIds.length < 2) {
    return '비교할 지역을 두 곳 이상 선택해주세요.'
  }
  if (reportType === 'COMPARE' && selectedAreaIds.length > 5) {
    return '비교 대상은 최대 다섯 곳까지 선택할 수 있습니다.'
  }
  return null
}

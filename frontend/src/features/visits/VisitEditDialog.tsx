import { useEffect, useState, type FormEvent } from 'react'
import { useAsyncData } from '../../hooks/useAsyncData'
import { ApiError, api } from '../../lib/api'
import type { AreaSummary, VisitDetail, VisitInput, VisitScores } from '../../types/api'

interface VisitEditDialogProps {
  visitId: number
  areas: AreaSummary[]
  onClose: () => void
  onSaved: (message: string) => Promise<void>
}

type ScoreField = keyof VisitScores

interface VisitFormValues extends VisitScores {
  areaId: string
  visitDate: string
  memo: string
}

const scoreDefinitions: Array<{
  field: ScoreField
  label: string
  hint: string
}> = [
  { field: 'atmosphereScore', label: '분위기', hint: '거리와 주거지에서 받은 전반적인 인상' },
  { field: 'infraScore', label: '생활 인프라', hint: '마트·병원·식당 등 생활 편의성' },
  { field: 'cleanScore', label: '청결도', hint: '거리와 공공 공간의 정돈 상태' },
  { field: 'sizeScore', label: '넓은 집 가능성', hint: '원하는 면적의 집을 구할 가능성' },
  { field: 'accessScore', label: '접근성', hint: '도쿄 주요 지역으로 이동하기 편한 정도' },
]

const scoreOptions = Array.from({ length: 11 }, (_, score) => score)

/** 저장된 Visit 상세를 불러와 날짜·점수·메모를 전체 수정하는 Dialog이다. */
export function VisitEditDialog({
  visitId,
  areas,
  onClose,
  onSaved,
}: VisitEditDialogProps) {
  const { data: visit, error, isLoading, reload } = useAsyncData(
    () => api.getVisit(visitId),
    [visitId],
  )
  const [values, setValues] = useState<VisitFormValues | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (visit) setValues(toFormValues(visit))
  }, [visit])

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isSubmitting) onClose()
    }
    window.addEventListener('keydown', handleKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [isSubmitting, onClose])

  const updateValue = <Field extends keyof VisitFormValues>(
    field: Field,
    value: VisitFormValues[Field],
  ) => {
    setValues((current) => current && ({ ...current, [field]: value }))
    setSubmitError(null)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!values || !visit) return

    const input = toVisitInput(values)
    if (!input) {
      setSubmitError('지역과 방문일을 확인해주세요.')
      return
    }

    setIsSubmitting(true)
    setSubmitError(null)
    try {
      await api.updateVisit(visit.id, input)
      const areaName = areas.find((area) => area.id === input.areaId)?.name ?? visit.area.name
      await onSaved(`${areaName} 방문 기록을 수정했습니다.`)
    } catch (error) {
      setSubmitError(
        error instanceof ApiError ? error.message : '방문 기록을 수정하지 못했습니다.',
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
        className="dialog dialog--visit"
        role="dialog"
        aria-modal="true"
        aria-labelledby="visit-edit-title"
      >
        <header className="dialog__header">
          <div>
            <span className="eyebrow">EDIT VISIT</span>
            <h2 id="visit-edit-title">방문 기록 수정</h2>
            <p>저장된 지역, 방문일, 점수와 메모를 전체 교체합니다.</p>
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

        {isLoading && !values && (
          <div className="dialog-state" role="status">
            <span className="loader" aria-hidden="true" />
            <p>방문 기록을 불러오고 있습니다.</p>
          </div>
        )}

        {error && !values && (
          <div className="dialog-state" role="alert">
            <p>{error.message}</p>
            <button className="button button--secondary" type="button" onClick={reload}>
              다시 시도
            </button>
          </div>
        )}

        {values && (
          <form className="visit-edit-form" onSubmit={handleSubmit} noValidate>
            <div className="form-row">
              <div className="form-field">
                <label className="form-field__label" htmlFor="edit-visit-area">
                  지역 <small>필수</small>
                </label>
                <select
                  id="edit-visit-area"
                  value={values.areaId}
                  onChange={(event) => updateValue('areaId', event.target.value)}
                >
                  {areas.map((area) => (
                    <option key={area.id} value={area.id}>{area.name}</option>
                  ))}
                </select>
              </div>
              <div className="form-field">
                <label className="form-field__label" htmlFor="edit-visit-date">
                  방문일 <small>필수</small>
                </label>
                <input
                  id="edit-visit-date"
                  type="date"
                  value={values.visitDate}
                  onChange={(event) => updateValue('visitDate', event.target.value)}
                />
              </div>
            </div>

            <div>
              <div className="visit-score-heading">
                <div>
                  <strong>평가 점수</strong>
                  <p>각 항목은 0점부터 10점까지 정수로 수정할 수 있습니다.</p>
                </div>
                <span>모든 항목 필수</span>
              </div>
              <div className="visit-score-grid visit-score-grid--review">
                {scoreDefinitions.map((definition) => (
                  <label className="score-select" htmlFor={`edit-${definition.field}`} key={definition.field}>
                    <span>{definition.label}</span>
                    <small>{definition.hint}</small>
                    <select
                      id={`edit-${definition.field}`}
                      value={values[definition.field]}
                      onChange={(event) => updateValue(definition.field, Number(event.target.value))}
                    >
                      {scoreOptions.map((score) => (
                        <option key={score} value={score}>{score}점</option>
                      ))}
                    </select>
                  </label>
                ))}
              </div>
            </div>

            <div className="form-field">
              <label className="form-field__label" htmlFor="edit-visit-memo">메모</label>
              <textarea
                id="edit-visit-memo"
                rows={6}
                value={values.memo}
                placeholder="방문하며 확인한 내용을 남겨주세요."
                onChange={(event) => updateValue('memo', event.target.value)}
              />
            </div>

            {submitError && <p className="form-alert" role="alert">{submitError}</p>}

            <footer className="dialog__footer">
              <button
                className="button button--secondary"
                type="button"
                onClick={onClose}
                disabled={isSubmitting}
              >
                취소
              </button>
              <button className="button" type="submit" disabled={isSubmitting}>
                {isSubmitting ? '저장 중...' : '수정 저장'}
              </button>
            </footer>
          </form>
        )}
      </section>
    </div>
  )
}

function toFormValues(visit: VisitDetail): VisitFormValues {
  return {
    areaId: String(visit.area.id),
    visitDate: visit.visitDate,
    atmosphereScore: visit.atmosphereScore,
    infraScore: visit.infraScore,
    cleanScore: visit.cleanScore,
    sizeScore: visit.sizeScore,
    accessScore: visit.accessScore,
    memo: visit.memo ?? '',
  }
}

function toVisitInput(values: VisitFormValues): VisitInput | null {
  // Area와 방문일이 유효할 때만 Form 상태를 API 요청 계약으로 변환한다.
  const areaId = Number(values.areaId)
  if (!Number.isSafeInteger(areaId) || !values.visitDate) return null
  return {
    areaId,
    visitDate: values.visitDate,
    atmosphereScore: values.atmosphereScore,
    infraScore: values.infraScore,
    cleanScore: values.cleanScore,
    sizeScore: values.sizeScore,
    accessScore: values.accessScore,
    memo: values.memo.trim() || null,
  }
}

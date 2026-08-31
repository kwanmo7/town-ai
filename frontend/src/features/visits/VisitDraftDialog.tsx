import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { ApiError, api } from '../../lib/api'
import type {
  AreaInput,
  AreaSummary,
  VisitDraft,
  VisitScores,
} from '../../types/api'

interface VisitDraftDialogProps {
  areas: AreaSummary[]
  onClose: () => void
  onSaved: (message: string) => Promise<void>
}

type ScoreField = keyof VisitScores
type ScoreSelections = Record<ScoreField, string>

interface ReviewState {
  selectedAreaId: string
  newArea: {
    name: string
    prefecture: string
    city: string
    station: string
  }
  visitDate: string
  scores: VisitScores
  memo: string
  warnings: string[]
}

interface ReviewErrors {
  area?: string
  name?: string
  prefecture?: string
  city?: string
  station?: string
  visitDate?: string
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

const emptyScores: ScoreSelections = {
  atmosphereScore: '',
  infraScore: '',
  cleanScore: '',
  sizeScore: '',
  accessScore: '',
}

const scoreOptions = Array.from({ length: 11 }, (_, score) => score)

/** 자연어와 선택 점수를 AI Draft로 변환하고 사용자 검토 후 Area·Visit을 저장하는 Dialog이다. */
export function VisitDraftDialog({
  areas,
  onClose,
  onSaved,
}: VisitDraftDialogProps) {
  const [stage, setStage] = useState<'input' | 'review'>('input')
  const [text, setText] = useState('')
  const [scoreSelections, setScoreSelections] = useState(emptyScores)
  const [inputErrors, setInputErrors] = useState<Partial<Record<ScoreField | 'text', string>>>({})
  const [review, setReview] = useState<ReviewState | null>(null)
  const [reviewErrors, setReviewErrors] = useState<ReviewErrors>({})
  const [createdArea, setCreatedArea] = useState<AreaSummary | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isBusy, setIsBusy] = useState(false)
  const textRef = useRef<HTMLTextAreaElement>(null)

  const availableAreas = useMemo(() => {
    if (!createdArea || areas.some((area) => area.id === createdArea.id)) {
      return areas
    }
    return [...areas, createdArea]
  }, [areas, createdArea])

  useEffect(() => {
    if (stage === 'input') textRef.current?.focus()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !isBusy) onClose()
    }
    window.addEventListener('keydown', handleKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [isBusy, onClose, stage])

  const handleScoreSelection = (field: ScoreField, value: string) => {
    setScoreSelections((current) => ({ ...current, [field]: value }))
    setInputErrors((current) => ({ ...current, [field]: undefined }))
    setSubmitError(null)
  }

  const handleParse = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const errors = validateDraftInput(text, scoreSelections)
    if (Object.keys(errors).length > 0) {
      setInputErrors(errors)
      return
    }

    // Web에서 명시적으로 고른 점수는 자연어에서 추론한 값보다 우선하도록 함께 전달한다.
    const selectedScores = toVisitScores(scoreSelections)
    setIsBusy(true)
    setSubmitError(null)

    try {
      const draft = await api.createVisitDraft({
        text: text.trim(),
        ...selectedScores,
      })
      setReview(toReviewState(draft, selectedScores))
      setReviewErrors({})
      setCreatedArea(null)
      setStage('review')
    } catch (error) {
      setSubmitError(toErrorMessage(error, '방문 기록 초안을 만들지 못했습니다.'))
    } finally {
      setIsBusy(false)
    }
  }

  const handleReviewSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!review) return

    const errors = validateReview(review)
    if (Object.keys(errors).length > 0) {
      setReviewErrors(errors)
      return
    }

    setIsBusy(true)
    setSubmitError(null)
    let areaId = Number(review.selectedAreaId)
    let areaName = availableAreas.find((area) => area.id === areaId)?.name ?? '선택 지역'

    try {
      if (review.selectedAreaId === 'new') {
        // AI가 신규 후보를 제안해도 사용자가 확정한 Area를 먼저 저장해 실제 ID를 얻는다.
        const areaInput = toAreaInput(review)
        const savedArea = await api.createArea(areaInput)
        areaId = savedArea.id
        areaName = savedArea.name
        setCreatedArea(savedArea)
        setReview((current) => current && ({
          ...current,
          selectedAreaId: String(savedArea.id),
        }))
      }

      await api.createVisit({
        areaId,
        visitDate: review.visitDate,
        ...review.scores,
        memo: review.memo.trim() || null,
      })
      await onSaved(`${areaName} 방문 기록을 등록했습니다.`)
    } catch (error) {
      setSubmitError(toErrorMessage(error, '방문 기록을 저장하지 못했습니다.'))
    } finally {
      setIsBusy(false)
    }
  }

  const updateReview = (values: Partial<ReviewState>) => {
    setReview((current) => current && ({ ...current, ...values }))
    setSubmitError(null)
  }

  const updateNewArea = (field: keyof ReviewState['newArea'], value: string) => {
    setReview((current) => current && ({
      ...current,
      newArea: { ...current.newArea, [field]: value },
    }))
    setReviewErrors((current) => ({ ...current, [field]: undefined, area: undefined }))
    setSubmitError(null)
  }

  const updateReviewScore = (field: ScoreField, value: number) => {
    setReview((current) => current && ({
      ...current,
      scores: { ...current.scores, [field]: value },
    }))
  }

  return (
    <div className="dialog-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !isBusy) onClose()
    }}>
      <section
        className="dialog dialog--visit"
        role="dialog"
        aria-modal="true"
        aria-labelledby="visit-dialog-title"
      >
        <header className="dialog__header">
          <div>
            <span className="eyebrow">
              {stage === 'input' ? 'NEW VISIT' : 'REVIEW DRAFT'}
            </span>
            <h2 id="visit-dialog-title">
              {stage === 'input' ? '자연어로 방문 기록 등록' : 'AI 초안 확인'}
            </h2>
            <p>
              {stage === 'input'
                ? '지역과 방문일, 느낀 점은 편하게 적고 점수는 직접 선택해주세요.'
                : 'AI가 정리한 지역·날짜·메모를 확인하고 필요한 항목만 고쳐주세요.'}
            </p>
          </div>
          <button
            className="icon-button"
            type="button"
            onClick={onClose}
            disabled={isBusy}
            aria-label="닫기"
          >
            ×
          </button>
        </header>

        {stage === 'input' ? (
          <form className="visit-draft-form" onSubmit={handleParse} noValidate>
            <div className="form-field">
              <label className="form-field__label" htmlFor="visit-natural-text">
                방문 내용 <small>필수</small>
              </label>
              <textarea
                ref={textRef}
                id="visit-natural-text"
                value={text}
                rows={7}
                placeholder="예: 어제 센터미나미를 둘러봤어. 역 앞 광장이 넓고 쇼핑 동선이 편했지만 주거 단지는 다음에 더 확인해보고 싶어."
                aria-invalid={Boolean(inputErrors.text)}
                aria-describedby={inputErrors.text ? 'visit-natural-text-error' : 'visit-natural-text-hint'}
                onChange={(event) => {
                  setText(event.target.value)
                  setInputErrors((current) => ({ ...current, text: undefined }))
                  setSubmitError(null)
                }}
              />
              <span className="form-field__meta">
                {inputErrors.text
                  ? <em id="visit-natural-text-error">{inputErrors.text}</em>
                  : <span id="visit-natural-text-hint">도도부현·시구정촌은 AI가 지역명으로 보완합니다.</span>}
              </span>
            </div>

            <div>
              <div className="visit-score-heading">
                <div>
                  <strong>직접 평가한 점수</strong>
                  <p>선택값은 AI가 바꾸지 않고 최종 초안에 그대로 반영합니다.</p>
                </div>
                <span>0 낮음 · 10 높음</span>
              </div>
              <div className="visit-score-grid">
                {scoreDefinitions.map((definition) => (
                  <ScoreSelect
                    key={definition.field}
                    id={`input-${definition.field}`}
                    definition={definition}
                    value={scoreSelections[definition.field]}
                    error={inputErrors[definition.field]}
                    onChange={(value) => handleScoreSelection(definition.field, value)}
                  />
                ))}
              </div>
            </div>

            {submitError && <p className="form-alert" role="alert">{submitError}</p>}

            <footer className="dialog__footer">
              <button
                className="button button--secondary"
                type="button"
                onClick={onClose}
                disabled={isBusy}
              >
                취소
              </button>
              <button className="button" type="submit" disabled={isBusy}>
                {isBusy ? 'AI가 정리하는 중...' : 'AI 초안 만들기'}
              </button>
            </footer>
          </form>
        ) : review && (
          <form className="visit-review-form" onSubmit={handleReviewSubmit} noValidate>
            {review.warnings.length > 0 && (
              <aside className="draft-warnings" aria-label="확인할 항목">
                <strong>확인할 항목</strong>
                <ul>
                  {review.warnings.map((warning, index) => (
                    <li key={`${index}-${warning}`}>{warning}</li>
                  ))}
                </ul>
              </aside>
            )}

            <section className="review-section">
              <div className="review-section__heading">
                <span>01</span>
                <div>
                  <strong>지역 연결</strong>
                  <p>기존 지역을 선택하거나 AI가 찾은 신규 지역을 확인합니다.</p>
                </div>
              </div>
              <div className="form-field">
                <label className="form-field__label" htmlFor="review-area">지역</label>
                <select
                  id="review-area"
                  value={review.selectedAreaId}
                  aria-invalid={Boolean(reviewErrors.area)}
                  onChange={(event) => {
                    updateReview({ selectedAreaId: event.target.value })
                    setReviewErrors((current) => ({ ...current, area: undefined }))
                  }}
                >
                  {availableAreas.map((area) => (
                    <option key={area.id} value={area.id}>{area.name} · {area.city}</option>
                  ))}
                  <option value="new">새 지역으로 등록</option>
                </select>
                {reviewErrors.area && <span className="field-error">{reviewErrors.area}</span>}
              </div>

              {review.selectedAreaId === 'new' && (
                <div className="new-area-fields">
                  <ReviewInput
                    id="review-area-name"
                    label="지역명"
                    value={review.newArea.name}
                    maxLength={25}
                    error={reviewErrors.name}
                    onChange={(value) => updateNewArea('name', value)}
                  />
                  <div className="form-row">
                    <ReviewInput
                      id="review-prefecture"
                      label="도도부현"
                      value={review.newArea.prefecture}
                      maxLength={20}
                      error={reviewErrors.prefecture}
                      onChange={(value) => updateNewArea('prefecture', value)}
                    />
                    <ReviewInput
                      id="review-city"
                      label="시구정촌"
                      value={review.newArea.city}
                      maxLength={20}
                      error={reviewErrors.city}
                      onChange={(value) => updateNewArea('city', value)}
                    />
                  </div>
                  <ReviewInput
                    id="review-station"
                    label="인접 역 (선택)"
                    value={review.newArea.station}
                    maxLength={50}
                    error={reviewErrors.station}
                    onChange={(value) => updateNewArea('station', value)}
                  />
                </div>
              )}
            </section>

            <section className="review-section">
              <div className="review-section__heading">
                <span>02</span>
                <div>
                  <strong>방문 정보</strong>
                  <p>날짜와 직접 선택한 점수를 마지막으로 확인합니다.</p>
                </div>
              </div>
              <div className="form-field">
                <label className="form-field__label" htmlFor="review-visit-date">방문일</label>
                <input
                  id="review-visit-date"
                  type="date"
                  value={review.visitDate}
                  aria-invalid={Boolean(reviewErrors.visitDate)}
                  onChange={(event) => {
                    updateReview({ visitDate: event.target.value })
                    setReviewErrors((current) => ({ ...current, visitDate: undefined }))
                  }}
                />
                {reviewErrors.visitDate && <span className="field-error">{reviewErrors.visitDate}</span>}
              </div>
              <div className="visit-score-grid visit-score-grid--review">
                {scoreDefinitions.map((definition) => (
                  <ScoreSelect
                    key={definition.field}
                    id={`review-${definition.field}`}
                    definition={definition}
                    value={String(review.scores[definition.field])}
                    showPlaceholder={false}
                    onChange={(value) => updateReviewScore(definition.field, Number(value))}
                  />
                ))}
              </div>
              <div className="form-field">
                <label className="form-field__label" htmlFor="review-memo">메모</label>
                <textarea
                  id="review-memo"
                  rows={5}
                  value={review.memo}
                  placeholder="AI가 정리한 메모가 표시됩니다. 필요한 부분만 수정해주세요."
                  onChange={(event) => updateReview({ memo: event.target.value })}
                />
              </div>
            </section>

            {submitError && <p className="form-alert" role="alert">{submitError}</p>}

            <footer className="dialog__footer">
              <button
                className="button button--secondary"
                type="button"
                onClick={() => {
                  setStage('input')
                  setSubmitError(null)
                }}
                disabled={isBusy}
              >
                입력으로 돌아가기
              </button>
              <button className="button" type="submit" disabled={isBusy}>
                {isBusy ? '저장 중...' : '확인 후 저장'}
              </button>
            </footer>
          </form>
        )}
      </section>
    </div>
  )
}

interface ScoreSelectProps {
  id: string
  definition: (typeof scoreDefinitions)[number]
  value: string
  error?: string
  showPlaceholder?: boolean
  onChange: (value: string) => void
}

function ScoreSelect({
  id,
  definition,
  value,
  error,
  showPlaceholder = true,
  onChange,
}: ScoreSelectProps) {
  return (
    <label className="score-select" htmlFor={id}>
      <span>{definition.label}</span>
      <small>{definition.hint}</small>
      <select
        id={id}
        value={value}
        aria-invalid={Boolean(error)}
        onChange={(event) => onChange(event.target.value)}
      >
        {showPlaceholder && <option value="">점수 선택</option>}
        {scoreOptions.map((score) => (
          <option key={score} value={score}>{score}점</option>
        ))}
      </select>
      {error && <em>{error}</em>}
    </label>
  )
}

interface ReviewInputProps {
  id: string
  label: string
  value: string
  maxLength: number
  error?: string
  onChange: (value: string) => void
}

function ReviewInput({
  id,
  label,
  value,
  maxLength,
  error,
  onChange,
}: ReviewInputProps) {
  return (
    <div className="form-field">
      <label className="form-field__label" htmlFor={id}>{label}</label>
      <input
        id={id}
        value={value}
        maxLength={maxLength}
        aria-invalid={Boolean(error)}
        onChange={(event) => onChange(event.target.value)}
      />
      <span className="form-field__meta">
        {error ? <em>{error}</em> : <span />}
        <small>{value.length}/{maxLength}</small>
      </span>
    </div>
  )
}

function validateDraftInput(
  text: string,
  scores: ScoreSelections,
): Partial<Record<ScoreField | 'text', string>> {
  const errors: Partial<Record<ScoreField | 'text', string>> = {}
  if (text.trim().length === 0) {
    errors.text = '방문한 지역과 느낀 점을 입력해주세요.'
  }
  for (const definition of scoreDefinitions) {
    if (scores[definition.field] === '') {
      errors[definition.field] = '점수를 선택해주세요.'
    }
  }
  return errors
}

function validateReview(review: ReviewState): ReviewErrors {
  const errors: ReviewErrors = {}
  if (review.selectedAreaId === 'new') {
    const requiredFields: Array<keyof ReviewState['newArea']> = ['name', 'prefecture', 'city']
    for (const field of requiredFields) {
      if (review.newArea[field].trim().length === 0) {
        errors[field] = '필수 입력 항목입니다.'
      }
    }
    if (review.newArea.name.trim().length > 25) errors.name = '최대 25자까지 입력할 수 있습니다.'
    if (review.newArea.prefecture.trim().length > 20) errors.prefecture = '최대 20자까지 입력할 수 있습니다.'
    if (review.newArea.city.trim().length > 20) errors.city = '최대 20자까지 입력할 수 있습니다.'
    if (review.newArea.station.trim().length > 50) errors.station = '최대 50자까지 입력할 수 있습니다.'
  } else if (!Number.isSafeInteger(Number(review.selectedAreaId))) {
    errors.area = '연결할 지역을 선택해주세요.'
  }
  if (!review.visitDate) errors.visitDate = '방문일을 확인해주세요.'
  return errors
}

function toVisitScores(scores: ScoreSelections): VisitScores {
  return {
    atmosphereScore: Number(scores.atmosphereScore),
    infraScore: Number(scores.infraScore),
    cleanScore: Number(scores.cleanScore),
    sizeScore: Number(scores.sizeScore),
    accessScore: Number(scores.accessScore),
  }
}

function toReviewState(draft: VisitDraft, scores: VisitScores): ReviewState {
  // AI 응답을 직접 저장하지 않고 수정 가능한 상태로 복사해 최종 확인 단계를 보장한다.
  return {
    selectedAreaId: draft.area?.id ? String(draft.area.id) : 'new',
    newArea: {
      name: draft.area?.name ?? '',
      prefecture: draft.area?.prefecture ?? '',
      city: draft.area?.city ?? '',
      station: draft.area?.station ?? '',
    },
    visitDate: draft.visitDate ?? '',
    scores,
    memo: draft.memo ?? '',
    warnings: draft.warnings,
  }
}

function toAreaInput(review: ReviewState): AreaInput {
  const station = review.newArea.station.trim()
  return {
    name: review.newArea.name.trim(),
    prefecture: review.newArea.prefecture.trim(),
    city: review.newArea.city.trim(),
    station: station || null,
  }
}

function toErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback
}

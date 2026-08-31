import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, api } from '../../lib/api'
import type { AreaInput, AreaSummary } from '../../types/api'

interface AreaFormDialogProps {
  area: AreaSummary | null
  onClose: () => void
  onSaved: (message: string) => Promise<void>
}

interface AreaFormValues {
  name: string
  prefecture: string
  city: string
  station: string
}

type AreaField = keyof AreaFormValues
type AreaFormErrors = Partial<Record<AreaField, string>>

const emptyValues: AreaFormValues = {
  name: '',
  prefecture: '',
  city: '',
  station: '',
}

const maxLengths: Record<AreaField, number> = {
  name: 25,
  prefecture: 20,
  city: 20,
  station: 50,
}

/** Area 등록과 전체 수정을 같은 검증 규칙으로 처리하는 Dialog이다. */
export function AreaFormDialog({ area, onClose, onSaved }: AreaFormDialogProps) {
  const isEditing = area !== null
  const [values, setValues] = useState<AreaFormValues>(() => toFormValues(area))
  const [errors, setErrors] = useState<AreaFormErrors>({})
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const nameInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    nameInputRef.current?.focus()
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

  const handleChange = (field: AreaField, value: string) => {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
    setSubmitError(null)
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const nextErrors = validate(values)

    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors)
      return
    }

    const input = toAreaInput(values)
    setIsSubmitting(true)
    setSubmitError(null)

    try {
      if (area) {
        await api.updateArea(area.id, input)
        await onSaved(`${input.name} 지역 정보를 수정했습니다.`)
      } else {
        await api.createArea(input)
        await onSaved(`${input.name} 지역을 등록했습니다.`)
      }
    } catch (error) {
      if (error instanceof ApiError) {
        const fieldErrors = toFieldErrors(error)
        if (Object.keys(fieldErrors).length > 0) {
          setErrors(fieldErrors)
        }
        setSubmitError(error.message)
      } else {
        setSubmitError('지역 정보를 저장하지 못했습니다.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="dialog-backdrop" onMouseDown={(event) => {
      if (event.target === event.currentTarget && !isSubmitting) onClose()
    }}>
      <section
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="area-dialog-title"
      >
        <header className="dialog__header">
          <div>
            <span className="eyebrow">{isEditing ? 'EDIT AREA' : 'NEW AREA'}</span>
            <h2 id="area-dialog-title">
              {isEditing ? '지역 정보 수정' : '새 지역 등록'}
            </h2>
            <p>리포트에서 구분할 지역과 행정구역 정보를 입력해주세요.</p>
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

        <form className="area-form" onSubmit={handleSubmit} noValidate>
          <FormField
            ref={nameInputRef}
            id="area-name"
            label="지역명"
            value={values.name}
            placeholder="예: 센터미나미"
            maxLength={maxLengths.name}
            error={errors.name}
            required
            onChange={(value) => handleChange('name', value)}
          />

          <div className="form-row">
            <FormField
              id="area-prefecture"
              label="도도부현"
              value={values.prefecture}
              placeholder="예: 가나가와현"
              maxLength={maxLengths.prefecture}
              error={errors.prefecture}
              required
              onChange={(value) => handleChange('prefecture', value)}
            />
            <FormField
              id="area-city"
              label="시구정촌"
              value={values.city}
              placeholder="예: 요코하마시 츠즈키구"
              maxLength={maxLengths.city}
              error={errors.city}
              required
              onChange={(value) => handleChange('city', value)}
            />
          </div>

          <FormField
            id="area-station"
            label="인접 역"
            value={values.station}
            placeholder="예: 센터미나미역 (선택)"
            maxLength={maxLengths.station}
            error={errors.station}
            onChange={(value) => handleChange('station', value)}
          />

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
              {isSubmitting ? '저장 중...' : isEditing ? '수정 저장' : '지역 등록'}
            </button>
          </footer>
        </form>
      </section>
    </div>
  )
}

interface FormFieldProps {
  ref?: React.Ref<HTMLInputElement>
  id: string
  label: string
  value: string
  placeholder: string
  maxLength: number
  error?: string
  required?: boolean
  onChange: (value: string) => void
}

function FormField({
  ref,
  id,
  label,
  value,
  placeholder,
  maxLength,
  error,
  required = false,
  onChange,
}: FormFieldProps) {
  return (
    <div className="form-field">
      <label className="form-field__label" htmlFor={id}>
        {label}
        {required && <small>필수</small>}
      </label>
      <input
        ref={ref}
        id={id}
        value={value}
        placeholder={placeholder}
        maxLength={maxLength}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      <span className="form-field__meta">
        {error ? <em id={`${id}-error`}>{error}</em> : <span />}
        <small>{value.length}/{maxLength}</small>
      </span>
    </div>
  )
}

function toFormValues(area: AreaSummary | null): AreaFormValues {
  if (!area) return emptyValues
  return {
    name: area.name,
    prefecture: area.prefecture,
    city: area.city,
    station: area.station ?? '',
  }
}

function toAreaInput(values: AreaFormValues): AreaInput {
  const station = values.station.trim()
  return {
    name: values.name.trim(),
    prefecture: values.prefecture.trim(),
    city: values.city.trim(),
    station: station.length > 0 ? station : null,
  }
}

function validate(values: AreaFormValues): AreaFormErrors {
  const errors: AreaFormErrors = {}
  const requiredFields: AreaField[] = ['name', 'prefecture', 'city']

  for (const field of requiredFields) {
    if (values[field].trim().length === 0) {
      errors[field] = '필수 입력 항목입니다.'
    }
  }

  for (const field of Object.keys(maxLengths) as AreaField[]) {
    if (values[field].trim().length > maxLengths[field]) {
      errors[field] = `최대 ${maxLengths[field]}자까지 입력할 수 있습니다.`
    }
  }

  return errors
}

function toFieldErrors(error: ApiError): AreaFormErrors {
  // Backend Validation 필드명을 Form 상태에 연결해 Client·Server 오류를 같은 위치에 표시한다.
  const fieldErrors: AreaFormErrors = {}
  for (const validationError of error.details?.errors ?? []) {
    if (validationError.field in emptyValues) {
      fieldErrors[validationError.field as AreaField] = validationError.reason
    }
  }
  return fieldErrors
}

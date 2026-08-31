interface ScoreBarProps {
  label: string
  value: number | null
}

/** 10점 기준 평가값을 숫자와 시각적 Bar로 함께 표시한다. */
export function ScoreBar({ label, value }: ScoreBarProps) {
  const width = value === null ? 0 : Math.min(100, Math.max(0, value * 10))

  return (
    <div className="score-bar">
      <div className="score-bar__meta">
        <span>{label}</span>
        <strong>{value === null ? '—' : value.toFixed(1)}</strong>
      </div>
      <div className="score-bar__track" aria-hidden="true">
        <span style={{ width: `${width}%` }} />
      </div>
    </div>
  )
}

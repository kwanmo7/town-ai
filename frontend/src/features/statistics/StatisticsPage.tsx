import { useEffect, useRef, useState } from 'react'
import { ErrorPanel, LoadingPanel } from '../../components/common/AsyncContent'
import { PageHeader } from '../../components/common/PageHeader'
import { ScoreBar } from '../../components/common/ScoreBar'
import { useAsyncData } from '../../hooks/useAsyncData'
import { ApiError, api } from '../../lib/api'
import { formatScore, scoreLabels } from '../../lib/format'
import type { AreaStatistics, ScoreAverages } from '../../types/api'

const scoreKeys: Array<keyof ScoreAverages> = [
  'atmosphere',
  'infra',
  'clean',
  'size',
  'access',
]

async function loadStatisticsPage() {
  const [statistics, areas] = await Promise.all([
    api.getStatistics(),
    api.getAreas(),
  ])
  return { statistics, areas }
}

export function StatisticsPage() {
  const { data, error, isLoading, reload } = useAsyncData(loadStatisticsPage)
  const [selectedAreaId, setSelectedAreaId] = useState('')
  const [areaStatistics, setAreaStatistics] = useState<AreaStatistics | null>(null)
  const [areaError, setAreaError] = useState<string | null>(null)
  const [isAreaLoading, setIsAreaLoading] = useState(false)
  const areaRequestSequence = useRef(0)
  const areaPanelRef = useRef<HTMLElement>(null)

  useEffect(() => () => {
    areaRequestSequence.current += 1
  }, [])

  const loadAreaStatistics = async (areaId: number) => {
    const requestSequence = ++areaRequestSequence.current
    setIsAreaLoading(true)
    setAreaError(null)
    setAreaStatistics(null)
    try {
      const result = await api.getAreaStatistics(areaId)
      if (requestSequence === areaRequestSequence.current) {
        setAreaStatistics(result)
      }
    } catch (error) {
      if (requestSequence === areaRequestSequence.current) {
        setAreaError(
          error instanceof ApiError ? error.message : '지역 통계를 불러오지 못했습니다.',
        )
      }
    } finally {
      if (requestSequence === areaRequestSequence.current) {
        setIsAreaLoading(false)
      }
    }
  }

  const selectArea = (areaId: string, revealResult = false) => {
    setSelectedAreaId(areaId)
    if (!areaId) {
      areaRequestSequence.current += 1
      setAreaStatistics(null)
      setAreaError(null)
      setIsAreaLoading(false)
      return
    }
    void loadAreaStatistics(Number(areaId))
    if (revealResult) {
      window.requestAnimationFrame(() => {
        areaPanelRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })
    }
  }

  const overallAverage = data
    ? calculateOverallAverage(data.statistics.averageScores)
    : null

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="STATISTICS"
        title="방문 점수 통계"
        description="전체 평균과 항목별 상위 지역을 비교하고, 지역별 누적 평가를 확인합니다."
      />

      {isLoading && !data && <LoadingPanel label="방문 통계를 계산하고 있습니다." />}
      {error && <ErrorPanel error={error} onRetry={reload} />}

      {data && (
        <>
          <section className="metric-grid" aria-label="통계 현황">
            <article className="metric-card metric-card--primary">
              <span>집계 지역</span>
              <strong>{data.statistics.areaCount}</strong>
              <p>Soft Delete되지 않은 지역</p>
            </article>
            <article className="metric-card">
              <span>집계 방문</span>
              <strong>{data.statistics.visitCount}</strong>
              <p>전체 평균에 반영된 방문 기록</p>
            </article>
            <article className="metric-card">
              <span>전체 점수 평균</span>
              <strong>{formatScore(overallAverage)}</strong>
              <p>다섯 평가 항목 평균</p>
            </article>
          </section>

          <div className="statistics-overview-grid">
            <section className="panel">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">OVERALL AVERAGE</span>
                  <h2>전체 방문 평균</h2>
                </div>
                <span className="section-heading__hint">10점 기준</span>
              </div>
              <div className="score-list">
                {scoreKeys.map((key) => (
                  <ScoreBar
                    key={key}
                    label={scoreLabels[key]}
                    value={data.statistics.averageScores[key]}
                  />
                ))}
              </div>
            </section>

            <section className="panel area-statistics-panel" ref={areaPanelRef}>
              <div className="section-heading">
                <div>
                  <span className="eyebrow">AREA DETAIL</span>
                  <h2>지역별 누적 평가</h2>
                </div>
              </div>
              <label className="statistics-area-select" htmlFor="statistics-area">
                <span>확인할 지역</span>
                <select
                  id="statistics-area"
                  value={selectedAreaId}
                  onChange={(event) => selectArea(event.target.value)}
                >
                  <option value="">지역을 선택해주세요</option>
                  {data.areas.map((area) => (
                    <option key={area.id} value={area.id}>{area.name}</option>
                  ))}
                </select>
              </label>

              {!selectedAreaId && (
                <p className="statistics-placeholder">지역을 선택하면 방문 횟수와 평균을 표시합니다.</p>
              )}
              {isAreaLoading && (
                <div className="statistics-inline-state" role="status">
                  <span className="loader" aria-hidden="true" />
                  통계를 불러오고 있습니다.
                </div>
              )}
              {areaError && (
                <div className="statistics-area-error" role="alert">
                  <p>{areaError}</p>
                  <button
                    className="button button--secondary"
                    type="button"
                    onClick={() => void loadAreaStatistics(Number(selectedAreaId))}
                  >
                    다시 시도
                  </button>
                </div>
              )}
              {areaStatistics && (
                <div className="area-statistics-result">
                  <div>
                    <strong>{areaStatistics.areaName}</strong>
                    <span>방문 {areaStatistics.visitCount}회</span>
                  </div>
                  <div className="score-list">
                    {scoreKeys.map((key) => (
                      <ScoreBar
                        key={key}
                        label={scoreLabels[key]}
                        value={areaStatistics.averageScores[key]}
                      />
                    ))}
                  </div>
                </div>
              )}
            </section>
          </div>

          <section className="panel">
            <div className="section-heading">
              <div>
                <span className="eyebrow">TOP FIVE</span>
                <h2>항목별 상위 지역</h2>
              </div>
              <span className="section-heading__hint">지역별 방문 평균 기준</span>
            </div>
            <div className="top-five-grid">
              {scoreKeys.map((key) => {
                const ranking = data.statistics.top5[key]
                return (
                  <article className="ranking-card" key={key}>
                    <header>
                      <span>{scoreLabels[key]}</span>
                      <small>TOP {ranking.length}</small>
                    </header>
                    {ranking.length === 0 ? (
                      <p>집계할 방문 기록이 없습니다.</p>
                    ) : (
                      <ol>
                        {ranking.map((area, index) => (
                          <li key={area.areaId}>
                            <button
                              type="button"
                              onClick={() => selectArea(String(area.areaId), true)}
                            >
                              <span>{index + 1}</span>
                              <strong>{area.areaName}</strong>
                              <b>{area.score.toFixed(1)}</b>
                            </button>
                          </li>
                        ))}
                      </ol>
                    )}
                  </article>
                )
              })}
            </div>
          </section>
        </>
      )}
    </div>
  )
}

function calculateOverallAverage(averages: ScoreAverages): number | null {
  const values = scoreKeys
    .map((key) => averages[key])
    .filter((value): value is number => value !== null)
  if (values.length === 0) return null
  return values.reduce((sum, value) => sum + value, 0) / values.length
}

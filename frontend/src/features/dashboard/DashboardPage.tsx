import { Link } from 'react-router-dom'
import { ErrorPanel, LoadingPanel } from '../../components/common/AsyncContent'
import { PageHeader } from '../../components/common/PageHeader'
import { ScoreBar } from '../../components/common/ScoreBar'
import { useAsyncData } from '../../hooks/useAsyncData'
import { api } from '../../lib/api'
import { formatDateTime, reportTypeLabels, scoreLabels } from '../../lib/format'

async function loadDashboard() {
  const [statistics, visits, reports] = await Promise.all([
    api.getStatistics(),
    api.getVisits(),
    api.getReports(),
  ])

  return { statistics, visits, reports }
}

export function DashboardPage() {
  const { data, error, isLoading, reload } = useAsyncData(loadDashboard)

  return (
    <div className="page-stack">
      <PageHeader
        eyebrow="OVERVIEW"
        title="동네 선택의 근거를 한곳에"
        description="방문 기록과 점수의 흐름을 확인하고, 필요한 순간에 AI 분석을 꺼내보세요."
      />

      {isLoading && <LoadingPanel label="Town AI 현황을 정리하고 있습니다." />}
      {error && <ErrorPanel error={error} onRetry={reload} />}

      {data && (
        <>
          <section className="metric-grid" aria-label="전체 현황">
            <article className="metric-card metric-card--primary">
              <span>등록 지역</span>
              <strong>{data.statistics.areaCount}</strong>
              <p>현재 비교 가능한 지역</p>
            </article>
            <article className="metric-card">
              <span>방문 기록</span>
              <strong>{data.statistics.visitCount}</strong>
              <p>판단의 근거가 된 현장 기록</p>
            </article>
            <article className="metric-card">
              <span>생성 리포트</span>
              <strong>{data.reports.length}</strong>
              <p>저장된 AI 분석 결과</p>
            </article>
          </section>

          <div className="dashboard-grid">
            <section className="panel">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">AVERAGE SCORE</span>
                  <h2>전체 방문 평균</h2>
                </div>
                <span className="section-heading__hint">10점 기준</span>
              </div>
              <div className="score-list">
                {Object.entries(scoreLabels).map(([key, label]) => (
                  <ScoreBar
                    key={key}
                    label={label}
                    value={data.statistics.averageScores[key as keyof typeof scoreLabels]}
                  />
                ))}
              </div>
            </section>

            <section className="panel">
              <div className="section-heading">
                <div>
                  <span className="eyebrow">RECENT VISITS</span>
                  <h2>최근 방문</h2>
                </div>
                <Link className="text-link" to="/visits">전체 보기</Link>
              </div>
              {data.visits.length === 0 ? (
                <p className="panel__empty">아직 방문 기록이 없습니다.</p>
              ) : (
                <div className="compact-list">
                  {data.visits.slice(0, 4).map((visit) => {
                    const average = (
                      visit.atmosphereScore + visit.infraScore + visit.cleanScore +
                      visit.sizeScore + visit.accessScore
                    ) / 5

                    return (
                      <div className="compact-list__item" key={visit.id}>
                        <span className="compact-list__mark">{visit.area.name.slice(0, 1)}</span>
                        <div>
                          <strong>{visit.area.name}</strong>
                          <small>{visit.visitDate}</small>
                        </div>
                        <b>{average.toFixed(1)}</b>
                      </div>
                    )
                  })}
                </div>
              )}
            </section>
          </div>

          <section className="panel">
            <div className="section-heading">
              <div>
                <span className="eyebrow">LATEST REPORTS</span>
                <h2>최근 리포트</h2>
              </div>
              <Link className="text-link" to="/reports">리포트 보관함</Link>
            </div>
            {data.reports.length === 0 ? (
              <p className="panel__empty">생성된 리포트가 없습니다.</p>
            ) : (
              <div className="report-row-list">
                {data.reports.slice(0, 3).map((report) => (
                  <Link to={`/reports/${report.id}`} key={report.id}>
                    <span className={`report-type report-type--${report.reportType.toLowerCase()}`}>
                      {reportTypeLabels[report.reportType]}
                    </span>
                    <div>
                      <strong>{reportTypeLabels[report.reportType]} 리포트</strong>
                      <small>{formatDateTime(report.createdAt)} · {report.promptVersion}</small>
                    </div>
                    <span className="row-arrow" aria-hidden="true">→</span>
                  </Link>
                ))}
              </div>
            )}
          </section>
        </>
      )}
    </div>
  )
}

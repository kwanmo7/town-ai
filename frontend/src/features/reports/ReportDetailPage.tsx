import { Link, useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { ErrorPanel, LoadingPanel } from '../../components/common/AsyncContent'
import { useAsyncData } from '../../hooks/useAsyncData'
import { api } from '../../lib/api'
import { formatDateTime, reportTypeLabels } from '../../lib/format'

export function ReportDetailPage() {
  const { reportId: rawReportId } = useParams()
  const reportId = Number(rawReportId)
  const { data, error, isLoading, reload } = useAsyncData(
    async () => {
      if (!Number.isSafeInteger(reportId) || reportId <= 0) {
        throw new Error('유효하지 않은 리포트 ID입니다.')
      }

      const [report, content] = await Promise.all([
        api.getReport(reportId),
        api.getReportContent(reportId),
      ])
      return { report, content }
    },
    [reportId],
  )

  return (
    <div className="page-stack">
      <div className="detail-toolbar">
        <Link className="back-link" to="/reports">← 리포트 보관함</Link>
        {data && (
          <a className="button button--secondary" href={`/api/reports/${reportId}/download`}>
            Markdown 다운로드
          </a>
        )}
      </div>

      {isLoading && <LoadingPanel label="리포트 본문을 불러오고 있습니다." />}
      {error && <ErrorPanel error={error} onRetry={reload} />}

      {data && (
        <article className="report-document">
          <header className="report-document__header">
            <span className={`report-type report-type--${data.report.reportType.toLowerCase()}`}>
              {reportTypeLabels[data.report.reportType]}
            </span>
            <h1>{reportTypeLabels[data.report.reportType]} 리포트</h1>
            <div>
              <span>{formatDateTime(data.report.createdAt)}</span>
              <span>{data.report.model}</span>
              <span>{data.report.promptVersion}</span>
            </div>
          </header>
          <div className="markdown-body">
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                table: ({ children, ...props }) => (
                  <div className="markdown-table-scroll">
                    <table {...props}>{children}</table>
                  </div>
                ),
              }}
            >
              {data.content}
            </ReactMarkdown>
          </div>
        </article>
      )}
    </div>
  )
}

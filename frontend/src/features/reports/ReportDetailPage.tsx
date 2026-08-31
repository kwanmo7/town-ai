import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { ErrorPanel, LoadingPanel } from '../../components/common/AsyncContent'
import { useAsyncData } from '../../hooks/useAsyncData'
import { api } from '../../lib/api'
import { formatDateTime, reportTypeLabels } from '../../lib/format'

/** Report Metadata와 Markdown 본문을 조회하고 파일 다운로드를 제공한다. */
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
  const [downloadError, setDownloadError] = useState<string | null>(null)
  const [isDownloading, setIsDownloading] = useState(false)

  const download = async () => {
    if (!data || isDownloading) {
      return
    }
    setDownloadError(null)
    setIsDownloading(true)
    try {
      const blob = await api.downloadReport(reportId)
      // Browser 임시 URL은 클릭 직후 해제해 장시간 Session의 메모리 점유를 남기지 않는다.
      const objectUrl = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = objectUrl
      anchor.download = `${data.report.reportType.toLowerCase()}-report-${reportId}.md`
      document.body.append(anchor)
      anchor.click()
      anchor.remove()
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
    } catch (caught) {
      setDownloadError(
        caught instanceof Error ? caught.message : '리포트를 다운로드하지 못했습니다.',
      )
    } finally {
      setIsDownloading(false)
    }
  }

  return (
    <div className="page-stack">
      <div className="detail-toolbar">
        <Link className="back-link" to="/reports">← 리포트 보관함</Link>
        {data && (
          <button
            className="button button--secondary"
            type="button"
            disabled={isDownloading}
            onClick={download}
          >
            {isDownloading ? '다운로드 중…' : 'Markdown 다운로드'}
          </button>
        )}
      </div>

      {downloadError && <p className="form-error" role="alert">{downloadError}</p>}

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

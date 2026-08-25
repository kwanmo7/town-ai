import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import { ReportDetailPage } from './ReportDetailPage'

describe('ReportDetailPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('GFM 점수 표를 실제 table 요소로 렌더링한다', async () => {
    vi.spyOn(api, 'getReport').mockResolvedValue({
      id: 10,
      reportType: 'SUMMARY',
      areaIds: [],
      model: 'gpt-5.6-luna',
      promptVersion: 'summary-v1',
      createdAt: '2026-08-11T12:00:00Z',
    })
    vi.spyOn(api, 'getReportContent').mockResolvedValue(`
# 전체 통계 요약

| 평가 항목 | 전체 평균 |
|---|---:|
| 분위기 | 8.2 |
| 생활 인프라 | 8.6 |
    `)

    render(
      <MemoryRouter initialEntries={['/reports/10']}>
        <Routes>
          <Route path="/reports/:reportId" element={<ReportDetailPage />} />
        </Routes>
      </MemoryRouter>,
    )

    const table = await screen.findByRole('table')
    expect(table).toHaveTextContent('평가 항목')
    expect(table).toHaveTextContent('분위기')
    expect(table.parentElement).toHaveClass('markdown-table-scroll')
  })
})

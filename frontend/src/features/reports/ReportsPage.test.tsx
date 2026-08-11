import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import type { ReportSummary, ReportType } from '../../types/api'
import { ReportsPage } from './ReportsPage'

const reports: ReportSummary[] = [
  createReport(3, 'SUMMARY'),
  createReport(2, 'COMPARE'),
  createReport(1, 'AREA'),
]

describe('ReportsPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('전체 목록을 한 번 조회하고 선택한 유형을 즉시 필터링한다', async () => {
    const getReportsSpy = vi.spyOn(api, 'getReports').mockResolvedValue(reports)

    render(
      <MemoryRouter>
        <ReportsPage />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: '한 지역 분석 리포트' }))
      .toBeInTheDocument()
    expect(getReportsSpy).toHaveBeenCalledOnce()

    fireEvent.click(screen.getByRole('button', { name: '지역 비교' }))

    expect(screen.getByRole('heading', { name: '지역 비교 리포트' }))
      .toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '한 지역 분석 리포트' }))
      .not.toBeInTheDocument()
    expect(screen.getByText('1개')).toBeInTheDocument()
    expect(getReportsSpy).toHaveBeenCalledOnce()
  })

  it('확인 후 Report를 삭제하고 현재 필터 목록을 다시 조회한다', async () => {
    const report = createReport(3, 'SUMMARY')
    const getReportsSpy = vi.spyOn(api, 'getReports')
      .mockResolvedValueOnce([report])
      .mockResolvedValueOnce([])
    const deleteReportSpy = vi.spyOn(api, 'deleteReport').mockResolvedValue()

    render(
      <MemoryRouter>
        <ReportsPage />
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByRole('button', {
      name: '전체 요약 리포트 #3 삭제',
    }))

    const dialog = screen.getByRole('alertdialog')
    expect(within(dialog).getByText('전체 요약 #3을 삭제할까요?')).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(deleteReportSpy).toHaveBeenCalledWith(3))
    await waitFor(() => expect(getReportsSpy).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('전체 요약 #3을 삭제했습니다.')).toBeInTheDocument()
    expect(screen.getByText('생성된 리포트가 없습니다.')).toBeInTheDocument()
  })
})

function createReport(id: number, reportType: ReportType): ReportSummary {
  return {
    id,
    reportType,
    model: 'gpt-5.6-luna',
    promptVersion: `${reportType.toLowerCase()}-v1`,
    createdAt: `2026-08-${String(10 + id).padStart(2, '0')}T12:00:00Z`,
  }
}

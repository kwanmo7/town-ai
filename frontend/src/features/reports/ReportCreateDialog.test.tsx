import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import { ReportCreateDialog } from './ReportCreateDialog'

const areas = [
  {
    id: 1,
    name: '센터미나미',
    prefecture: '가나가와현',
    city: '요코하마시 츠즈키구',
    station: '센터미나미역',
  },
  {
    id: 2,
    name: '후타코타마가와',
    prefecture: '도쿄도',
    city: '세타가야구',
    station: '후타코타마가와역',
  },
  {
    id: 3,
    name: '키치죠지',
    prefecture: '도쿄도',
    city: '무사시노시',
    station: '키치죠지역',
  },
]

const visits = [
  createVisit(1, 1, '센터미나미'),
  createVisit(2, 2, '후타코타마가와'),
]

describe('ReportCreateDialog', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('한 지역 분석은 방문 기록이 있는 Area 한 곳을 대상으로 생성한다', async () => {
    mockTargets()
    const createdReport = createReport(20, 'AREA')
    const createReportSpy = vi.spyOn(api, 'createReport').mockResolvedValue(createdReport)
    const onCreated = vi.fn()

    render(<ReportCreateDialog onClose={vi.fn()} onCreated={onCreated} />)

    fireEvent.click(await screen.findByRole('button', { name: /센터미나미/ }))
    fireEvent.click(screen.getByRole('button', { name: '한 지역 분석 생성' }))

    await waitFor(() => {
      expect(createReportSpy).toHaveBeenCalledWith({ reportType: 'AREA', areaIds: [1] })
      expect(onCreated).toHaveBeenCalledWith(createdReport)
    })
  })

  it('지역 비교는 두 곳 미만 선택을 막고 선택 순서를 보존한다', async () => {
    mockTargets()
    const createReportSpy = vi.spyOn(api, 'createReport')
      .mockResolvedValue(createReport(21, 'COMPARE'))

    render(<ReportCreateDialog onClose={vi.fn()} onCreated={vi.fn()} />)

    fireEvent.click(await screen.findByRole('button', { name: /^COMPARE 지역 비교/ }))
    fireEvent.click(screen.getByRole('button', { name: /후타코타마가와/ }))
    fireEvent.click(screen.getByRole('button', { name: '지역 비교 생성' }))

    expect(screen.getByText('비교할 지역을 두 곳 이상 선택해주세요.')).toBeInTheDocument()
    expect(createReportSpy).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /센터미나미/ }))
    fireEvent.click(screen.getByRole('button', { name: '지역 비교 생성' }))

    await waitFor(() => {
      expect(createReportSpy).toHaveBeenCalledWith({
        reportType: 'COMPARE',
        areaIds: [2, 1],
      })
    })
  })

  it('방문 기록이 없으면 모든 유형의 생성을 막는다', async () => {
    vi.spyOn(api, 'getAreas').mockResolvedValue(areas)
    vi.spyOn(api, 'getVisits').mockResolvedValue([])
    const createReportSpy = vi.spyOn(api, 'createReport')

    render(<ReportCreateDialog onClose={vi.fn()} onCreated={vi.fn()} />)

    expect(await screen.findByText('분석할 방문 기록이 없습니다. 방문 기록을 먼저 등록해주세요.'))
      .toBeInTheDocument()
    expect(screen.getByRole('button', { name: '한 지역 분석 생성' })).toBeDisabled()
    expect(createReportSpy).not.toHaveBeenCalled()
  })

  it('전체 요약은 areaIds 없이 생성한다', async () => {
    mockTargets()
    const createReportSpy = vi.spyOn(api, 'createReport')
      .mockResolvedValue(createReport(22, 'SUMMARY'))

    render(<ReportCreateDialog onClose={vi.fn()} onCreated={vi.fn()} />)

    fireEvent.click(await screen.findByRole('button', { name: /^SUMMARY 전체 요약/ }))
    fireEvent.click(screen.getByRole('button', { name: '전체 요약 생성' }))

    await waitFor(() => {
      expect(createReportSpy).toHaveBeenCalledWith({ reportType: 'SUMMARY' })
    })
  })
})

function mockTargets() {
  vi.spyOn(api, 'getAreas').mockResolvedValue(areas)
  vi.spyOn(api, 'getVisits').mockResolvedValue(visits)
}

function createVisit(id: number, areaId: number, areaName: string) {
  return {
    id,
    area: { id: areaId, name: areaName },
    visitDate: '2026-08-10',
    atmosphereScore: 8,
    infraScore: 8,
    cleanScore: 8,
    sizeScore: 7,
    accessScore: 7,
  }
}

function createReport(id: number, reportType: 'AREA' | 'COMPARE' | 'SUMMARY') {
  return {
    id,
    reportType,
    model: 'gpt-5.6-luna',
    promptVersion: `${reportType.toLowerCase()}-v1`,
    createdAt: '2026-08-11T12:00:00Z',
  }
}

import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'

describe('Area API client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('Area 생성 요청을 JSON POST로 전송한다', async () => {
    const responseBody = {
      id: 4,
      name: '후타코타마가와',
      prefecture: '도쿄도',
      city: '세타가야구',
      station: null,
      createdAt: '2026-08-11T12:00:00Z',
      updatedAt: '2026-08-11T12:00:00Z',
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify(responseBody),
      { status: 201, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)

    const input = {
      name: '후타코타마가와',
      prefecture: '도쿄도',
      city: '세타가야구',
      station: null,
    }
    const result = await api.createArea(input)

    expect(fetchMock).toHaveBeenCalledWith('/api/areas', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(input),
      headers: expect.objectContaining({
        Accept: 'application/json',
        'Content-Type': 'application/json',
      }),
    }))
    expect(result).toEqual(responseBody)
  })

  it('Area 삭제의 204 응답을 본문 없이 처리한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.deleteArea(3)).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith('/api/areas/3', expect.objectContaining({
      method: 'DELETE',
    }))
  })
})

describe('Visit API client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('자연어와 Web 선택 점수를 Visit Draft 요청으로 전송한다', async () => {
    const responseBody = {
      area: {
        id: 1,
        name: '센터미나미',
        prefecture: '가나가와현',
        city: '요코하마시 츠즈키구',
        station: '센터미나미역',
      },
      visitDate: '2026-08-11',
      atmosphereScore: 8,
      infraScore: 9,
      cleanScore: 8,
      sizeScore: 7,
      accessScore: 7,
      memo: '역 앞 광장이 넓었다.',
      warnings: [],
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify(responseBody),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)

    const input = {
      text: '센터미나미를 방문했고 역 앞 광장이 넓었어.',
      atmosphereScore: 8,
      infraScore: 9,
      cleanScore: 8,
      sizeScore: 7,
      accessScore: 7,
    }
    const result = await api.createVisitDraft(input)

    expect(fetchMock).toHaveBeenCalledWith('/api/visit-drafts', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify(input),
    }))
    expect(result).toEqual(responseBody)
  })

  it('Visit 전체 수정 요청을 JSON PUT으로 전송한다', async () => {
    const input = {
      areaId: 1,
      visitDate: '2026-08-10',
      atmosphereScore: 8,
      infraScore: 9,
      cleanScore: 8,
      sizeScore: 7,
      accessScore: 7,
      memo: '수정한 메모',
    }
    const responseBody = {
      id: 5,
      area: { id: 1, name: '센터미나미' },
      ...input,
      createdAt: '2026-08-11T12:00:00Z',
      updatedAt: '2026-08-11T13:00:00Z',
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify(responseBody),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.updateVisit(5, input)).resolves.toEqual(responseBody)
    expect(fetchMock).toHaveBeenCalledWith('/api/visits/5', expect.objectContaining({
      method: 'PUT',
      body: JSON.stringify(input),
    }))
  })

  it('Visit 삭제의 204 응답을 본문 없이 처리한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.deleteVisit(5)).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith('/api/visits/5', expect.objectContaining({
      method: 'DELETE',
    }))
  })
})

describe('Report API client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('전체 유형은 areaIds 필드를 생략해 생성 요청을 전송한다', async () => {
    const responseBody = {
      id: 10,
      reportType: 'SUMMARY' as const,
      model: 'gpt-5.6-luna',
      promptVersion: 'summary-v1',
      createdAt: '2026-08-11T12:00:00Z',
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify(responseBody),
      { status: 201, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.createReport({ reportType: 'SUMMARY' })).resolves.toEqual(responseBody)

    expect(fetchMock).toHaveBeenCalledWith('/api/reports', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ reportType: 'SUMMARY' }),
    }))
  })

  it('대상 유형은 선택 순서를 유지한 areaIds를 전송한다', async () => {
    const responseBody = {
      id: 11,
      reportType: 'COMPARE' as const,
      model: 'gpt-5.6-luna',
      promptVersion: 'compare-v1',
      createdAt: '2026-08-11T12:00:00Z',
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify(responseBody),
      { status: 201, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)

    await api.createReport({ reportType: 'COMPARE', areaIds: [3, 1] })

    expect(fetchMock).toHaveBeenCalledWith('/api/reports', expect.objectContaining({
      body: JSON.stringify({ reportType: 'COMPARE', areaIds: [3, 1] }),
    }))
  })

  it('Report 유형을 Query Parameter로 전달한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify([]),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.getReports('COMPARE')).resolves.toEqual([])

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/reports?reportType=COMPARE',
      expect.objectContaining({
        headers: expect.objectContaining({ Accept: 'application/json' }),
      }),
    )
  })

  it('Report 삭제의 204 응답을 본문 없이 처리한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.deleteReport(12)).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledWith('/api/reports/12', expect.objectContaining({
      method: 'DELETE',
    }))
  })
})

describe('Statistics API client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('선택한 Area의 통계 Endpoint를 호출한다', async () => {
    const responseBody = {
      areaId: 1,
      areaName: '센터미나미',
      visitCount: 3,
      averageScores: {
        atmosphere: 8.7,
        infra: 9,
        clean: 8.3,
        size: 8,
        access: 7.7,
      },
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(
      JSON.stringify(responseBody),
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api.getAreaStatistics(1)).resolves.toEqual(responseBody)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/areas/1/statistics',
      expect.objectContaining({
        headers: expect.objectContaining({ Accept: 'application/json' }),
      }),
    )
  })
})

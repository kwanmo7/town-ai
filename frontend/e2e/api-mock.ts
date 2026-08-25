import type { Page, Route } from '@playwright/test'

interface MockVisit {
  id: number
  area: { id: number; name: string }
  visitDate: string
  atmosphereScore: number
  infraScore: number
  cleanScore: number
  sizeScore: number
  accessScore: number
  memo?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ApiMockState {
  areas: Array<{
    id: number
    name: string
    prefecture: string
    city: string
    station: string | null
    createdAt?: string
    updatedAt?: string
  }>
  visits: MockVisit[]
  reports: Array<{
    id: number
    reportType: 'AREA' | 'COMPARE' | 'SUMMARY' | 'ALL'
    model: string
    promptVersion: string
    createdAt: string
    areaIds: number[]
  }>
}

export function createApiMockState(): ApiMockState {
  return {
    areas: [
      {
        id: 1,
        name: '센터미나미',
        prefecture: '가나가와현',
        city: '요코하마시 츠즈키구',
        station: '센터미나미역',
        createdAt: '2026-08-10T03:00:00Z',
        updatedAt: '2026-08-10T03:00:00Z',
      },
      {
        id: 2,
        name: '키치죠지',
        prefecture: '도쿄도',
        city: '무사시노시',
        station: '키치죠지역',
        createdAt: '2026-08-09T03:00:00Z',
        updatedAt: '2026-08-09T03:00:00Z',
      },
    ],
    visits: [
      {
        id: 5,
        area: { id: 1, name: '센터미나미' },
        visitDate: '2026-08-10',
        atmosphereScore: 8,
        infraScore: 9,
        cleanScore: 8,
        sizeScore: 7,
        accessScore: 7,
        memo: '역 앞 광장과 생활 동선을 확인했다.',
        createdAt: '2026-08-10T03:00:00Z',
        updatedAt: '2026-08-10T03:00:00Z',
      },
      {
        id: 4,
        area: { id: 2, name: '키치죠지' },
        visitDate: '2026-08-09',
        atmosphereScore: 9,
        infraScore: 8,
        cleanScore: 8,
        sizeScore: 6,
        accessScore: 8,
        memo: '상권과 공원 주변을 걸었다.',
        createdAt: '2026-08-09T03:00:00Z',
        updatedAt: '2026-08-09T03:00:00Z',
      },
    ],
    reports: [
      {
        id: 10,
        reportType: 'SUMMARY',
        model: 'gpt-5.6-luna',
        promptVersion: 'summary-v1',
        createdAt: '2026-08-11T03:00:00Z',
        areaIds: [],
      },
      {
        id: 9,
        reportType: 'AREA',
        model: 'gpt-5.6-luna',
        promptVersion: 'area-v1',
        createdAt: '2026-08-10T03:00:00Z',
        areaIds: [1],
      },
    ],
  }
}

export async function installApiMock(page: Page, state = createApiMockState()) {
  await page.route('**/api/**', async (route) => handleApiRoute(route, state))
  return state
}

async function handleApiRoute(route: Route, state: ApiMockState) {
  const request = route.request()
  const url = new URL(request.url())
  const path = url.pathname
  const method = request.method()

  const areaStatisticsMatch = path.match(/^\/api\/areas\/(\d+)\/statistics$/)
  if (method === 'GET' && areaStatisticsMatch) {
    const areaId = Number(areaStatisticsMatch[1])
    const area = state.areas.find((candidate) => candidate.id === areaId)
    const visits = state.visits.filter((visit) => visit.area.id === areaId)
    if (!area) return json(route, 404, errorBody(404, 'AREA_NOT_FOUND', '지역을 찾을 수 없습니다.', path))
    return json(route, 200, {
      areaId,
      areaName: area.name,
      visitCount: visits.length,
      averageScores: averageScores(visits),
    })
  }

  if (method === 'GET' && path === '/api/areas') {
    return json(route, 200, state.areas.map(withoutTimestamps))
  }

  if (method === 'GET' && path === '/api/visits') {
    return json(route, 200, state.visits.map(withoutVisitDetail))
  }

  const visitMatch = path.match(/^\/api\/visits\/(\d+)$/)
  if (visitMatch) {
    const visitId = Number(visitMatch[1])
    const visitIndex = state.visits.findIndex((visit) => visit.id === visitId)
    if (visitIndex < 0) {
      return json(route, 404, errorBody(404, 'VISIT_NOT_FOUND', '방문 기록을 찾을 수 없습니다.', path))
    }
    if (method === 'GET') {
      const visit = state.visits[visitIndex]
      const area = state.areas.find((candidate) => candidate.id === visit.area.id)
      return json(route, 200, { ...visit, area })
    }
    if (method === 'PUT') {
      const input = request.postDataJSON()
      const area = state.areas.find((candidate) => candidate.id === input.areaId)
      state.visits[visitIndex] = {
        ...state.visits[visitIndex],
        ...input,
        area: { id: area!.id, name: area!.name },
        updatedAt: '2026-08-12T03:00:00Z',
      }
      return json(route, 200, state.visits[visitIndex])
    }
    if (method === 'DELETE') {
      state.visits.splice(visitIndex, 1)
      return route.fulfill({ status: 204 })
    }
  }

  if (method === 'GET' && path === '/api/statistics') {
    return json(route, 200, overallStatistics(state))
  }

  if (method === 'GET' && path === '/api/reports') {
    const reportType = url.searchParams.get('reportType')
    const reports = reportType
      ? state.reports.filter((report) => report.reportType === reportType)
      : state.reports
    return json(route, 200, reports.map((report) => ({
      id: report.id,
      reportType: report.reportType,
      model: report.model,
      promptVersion: report.promptVersion,
      createdAt: report.createdAt,
    })))
  }

  const reportContentMatch = path.match(/^\/api\/reports\/(\d+)\/content$/)
  if (method === 'GET' && reportContentMatch) {
    return route.fulfill({
      status: 200,
      contentType: 'text/markdown; charset=UTF-8',
      body: '# 전체 요약\n\n| 평가 항목 | 평균 |\n| --- | ---: |\n| 분위기 | 8.5 |',
    })
  }

  const reportMatch = path.match(/^\/api\/reports\/(\d+)$/)
  if (reportMatch) {
    const reportId = Number(reportMatch[1])
    const reportIndex = state.reports.findIndex((report) => report.id === reportId)
    if (reportIndex < 0) {
      return json(route, 404, errorBody(404, 'REPORT_NOT_FOUND', '리포트를 찾을 수 없습니다.', path))
    }
    if (method === 'GET') return json(route, 200, state.reports[reportIndex])
    if (method === 'DELETE') {
      state.reports.splice(reportIndex, 1)
      return route.fulfill({ status: 204 })
    }
  }

  return json(route, 404, errorBody(404, 'NOT_FOUND', 'Mock API가 정의되지 않았습니다.', path))
}

function overallStatistics(state: ApiMockState) {
  const byArea = state.areas.map((area) => ({
    area,
    averages: averageScores(state.visits.filter((visit) => visit.area.id === area.id)),
  }))
  const top5 = Object.fromEntries(scoreKeys.map((key) => [
    key,
    byArea
      .filter((entry) => entry.averages[key] !== null)
      .sort((left, right) => right.averages[key]! - left.averages[key]!)
      .map((entry) => ({
        areaId: entry.area.id,
        areaName: entry.area.name,
        score: entry.averages[key],
      })),
  ]))
  return {
    areaCount: state.areas.length,
    visitCount: state.visits.length,
    averageScores: averageScores(state.visits),
    top5,
  }
}

const scoreKeys = ['atmosphere', 'infra', 'clean', 'size', 'access'] as const

function averageScores(visits: MockVisit[]) {
  return Object.fromEntries(scoreKeys.map((key) => {
    if (visits.length === 0) return [key, null]
    const sourceKey = `${key}Score` as keyof MockVisit
    const total = visits.reduce((sum, visit) => sum + Number(visit[sourceKey]), 0)
    return [key, Math.round((total / visits.length) * 10) / 10]
  }))
}

function withoutVisitDetail(visit: MockVisit) {
  return {
    id: visit.id,
    area: visit.area,
    visitDate: visit.visitDate,
    atmosphereScore: visit.atmosphereScore,
    infraScore: visit.infraScore,
    cleanScore: visit.cleanScore,
    sizeScore: visit.sizeScore,
    accessScore: visit.accessScore,
  }
}

function withoutTimestamps(area: ApiMockState['areas'][number]) {
  return {
    id: area.id,
    name: area.name,
    prefecture: area.prefecture,
    city: area.city,
    station: area.station,
  }
}

function errorBody(status: number, code: string, message: string, path: string) {
  return {
    timestamp: '2026-08-12T03:00:00Z',
    status,
    code,
    message,
    path,
  }
}

function json(route: Route, status: number, body: unknown) {
  return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
}

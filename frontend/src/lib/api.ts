import type {
  ApiErrorResponse,
  AreaDetail,
  AreaInput,
  AreaStatistics,
  AreaSummary,
  AuthenticatedUser,
  OverallStatistics,
  ReportCreateInput,
  ReportDetail,
  ReportSummary,
  ReportType,
  VisitDraft,
  VisitDraftInput,
  VisitDetail,
  VisitInput,
  VisitMutation,
  VisitSummary,
} from '../types/api'
import { getFirebaseIdToken } from './firebase'

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: ApiErrorResponse | null

  constructor(
    message: string,
    status: number,
    code = 'UNKNOWN_ERROR',
    details: ApiErrorResponse | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = await createHeaders('application/json', init?.headers)
  const response = await fetch(path, {
    ...init,
    headers,
  })

  if (!response.ok) {
    const error = await parseError(response)
    throw new ApiError(
      error?.message ?? '요청을 처리하지 못했습니다.',
      response.status,
      error?.code,
      error,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

function jsonRequest(method: 'POST' | 'PUT', body: unknown): RequestInit {
  return {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

async function parseError(response: Response): Promise<ApiErrorResponse | null> {
  try {
    return (await response.json()) as ApiErrorResponse
  } catch {
    return null
  }
}

async function requestText(path: string): Promise<string> {
  const headers = await createHeaders('text/markdown')
  const response = await fetch(path, {
    headers,
  })

  if (!response.ok) {
    const error = await parseError(response)
    throw new ApiError(
      error?.message ?? '리포트 내용을 불러오지 못했습니다.',
      response.status,
      error?.code,
      error,
    )
  }

  return response.text()
}

async function requestBlob(path: string): Promise<Blob> {
  const headers = await createHeaders('text/markdown')
  const response = await fetch(path, { headers })

  if (!response.ok) {
    const error = await parseError(response)
    throw new ApiError(
      error?.message ?? '리포트를 다운로드하지 못했습니다.',
      response.status,
      error?.code,
      error,
    )
  }

  return response.blob()
}

async function createHeaders(
  accept: string,
  existing?: HeadersInit,
): Promise<Headers> {
  const headers = new Headers(existing)
  headers.set('Accept', accept)
  const idToken = await getFirebaseIdToken()
  if (idToken !== null) {
    headers.set('Authorization', `Bearer ${idToken}`)
  }
  return headers
}

export const api = {
  getAuthenticatedUser: () =>
    request<AuthenticatedUser>('/api/auth/me'),
  getAreas: () => request<AreaSummary[]>('/api/areas'),
  createArea: (input: AreaInput) =>
    request<AreaDetail>('/api/areas', jsonRequest('POST', input)),
  updateArea: (areaId: number, input: AreaInput) =>
    request<AreaDetail>(`/api/areas/${areaId}`, jsonRequest('PUT', input)),
  deleteArea: (areaId: number) =>
    request<void>(`/api/areas/${areaId}`, { method: 'DELETE' }),
  getVisits: () => request<VisitSummary[]>('/api/visits'),
  getVisit: (visitId: number) => request<VisitDetail>(`/api/visits/${visitId}`),
  createVisitDraft: (input: VisitDraftInput) =>
    request<VisitDraft>('/api/visit-drafts', jsonRequest('POST', input)),
  createVisit: (input: VisitInput) =>
    request<VisitMutation>('/api/visits', jsonRequest('POST', input)),
  updateVisit: (visitId: number, input: VisitInput) =>
    request<VisitMutation>(`/api/visits/${visitId}`, jsonRequest('PUT', input)),
  deleteVisit: (visitId: number) =>
    request<void>(`/api/visits/${visitId}`, { method: 'DELETE' }),
  getReports: (reportType?: ReportType) => {
    const query = reportType ? `?reportType=${reportType}` : ''
    return request<ReportSummary[]>(`/api/reports${query}`)
  },
  createReport: (input: ReportCreateInput) =>
    request<ReportSummary>('/api/reports', jsonRequest('POST', input)),
  getReport: (reportId: number) =>
    request<ReportDetail>(`/api/reports/${reportId}`),
  getReportContent: (reportId: number) =>
    requestText(`/api/reports/${reportId}/content`),
  downloadReport: (reportId: number) =>
    requestBlob(`/api/reports/${reportId}/download`),
  deleteReport: (reportId: number) =>
    request<void>(`/api/reports/${reportId}`, { method: 'DELETE' }),
  getStatistics: () => request<OverallStatistics>('/api/statistics'),
  getAreaStatistics: (areaId: number) =>
    request<AreaStatistics>(`/api/areas/${areaId}/statistics`),
}

export interface AreaSummary {
  id: number
  name: string
  prefecture: string
  city: string
  station: string | null
}

export interface AuthenticatedUser {
  uid: string
  email: string | null
  name: string | null
}

export interface AreaDetail extends AreaSummary {
  createdAt: string
  updatedAt: string
}

export interface AreaInput {
  name: string
  prefecture: string
  city: string
  station: string | null
}

export interface VisitSummary {
  id: number
  area: {
    id: number
    name: string
  }
  visitDate: string
  atmosphereScore: number
  infraScore: number
  cleanScore: number
  sizeScore: number
  accessScore: number
}

export interface VisitScores {
  atmosphereScore: number
  infraScore: number
  cleanScore: number
  sizeScore: number
  accessScore: number
}

export interface VisitDraftInput extends VisitScores {
  text: string
}

export interface VisitDraftArea {
  id: number | null
  name: string | null
  prefecture: string | null
  city: string | null
  station: string | null
}

export interface VisitDraft {
  area: VisitDraftArea | null
  visitDate: string | null
  atmosphereScore: number | null
  infraScore: number | null
  cleanScore: number | null
  sizeScore: number | null
  accessScore: number | null
  memo: string | null
  warnings: string[]
}

export interface VisitInput extends VisitScores {
  areaId: number
  visitDate: string
  memo: string | null
}

export interface VisitMutation extends VisitScores {
  id: number
  area: {
    id: number
    name: string
  }
  visitDate: string
  memo: string | null
  createdAt: string
  updatedAt: string
}

export interface VisitDetail extends VisitScores {
  id: number
  area: AreaSummary
  visitDate: string
  memo: string | null
  createdAt: string
  updatedAt: string
}

export type ReportType = 'SUMMARY' | 'ALL' | 'AREA' | 'COMPARE'

export interface ReportCreateInput {
  reportType: ReportType
  areaIds?: number[]
}

export interface ReportSummary {
  id: number
  reportType: ReportType
  model: string
  promptVersion: string
  createdAt: string
}

export interface ReportDetail extends ReportSummary {
  areaIds: number[]
}

export interface ScoreAverages {
  atmosphere: number | null
  infra: number | null
  clean: number | null
  size: number | null
  access: number | null
}

export interface TopArea {
  areaId: number
  areaName: string
  score: number
}

export type TopFive = Record<keyof ScoreAverages, TopArea[]>

export interface OverallStatistics {
  areaCount: number
  visitCount: number
  averageScores: ScoreAverages
  top5: TopFive
}

export interface AreaStatistics {
  areaId: number
  areaName: string
  visitCount: number
  averageScores: ScoreAverages
}

export interface ValidationError {
  field: string
  reason: string
}

export interface ApiErrorResponse {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  errors?: ValidationError[]
}

/** Area 목록과 다른 응답에 공통으로 노출되는 요약 정보이다. */
export interface AreaSummary {
  id: number
  name: string
  prefecture: string
  city: string
  station: string | null
}

/** Firebase Token 검증 후 Backend가 허용한 관리 사용자 정보이다. */
export interface AuthenticatedUser {
  uid: string
  email: string | null
  name: string | null
}

/** Area 생성·상세 조회·수정 결과이다. */
export interface AreaDetail extends AreaSummary {
  createdAt: string
  updatedAt: string
}

/** Area 생성과 전체 수정 요청에 사용하는 입력값이다. */
export interface AreaInput {
  name: string
  prefecture: string
  city: string
  station: string | null
}

/** 방문 기록 목록에 표시하는 Visit과 Area 요약 정보이다. */
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

/** Visit과 통계에서 공통으로 사용하는 다섯 가지 평가 점수이다. */
export interface VisitScores {
  atmosphereScore: number
  infraScore: number
  cleanScore: number
  sizeScore: number
  accessScore: number
}

/** 자연어 Draft 생성 시 사용자가 선택한 점수를 함께 전달하는 입력값이다. */
export interface VisitDraftInput extends VisitScores {
  text: string
}

/** AI가 식별한 기존 Area 또는 신규 Area 후보이다. */
export interface VisitDraftArea {
  id: number | null
  name: string | null
  prefecture: string | null
  city: string | null
  station: string | null
}

/** 자연어를 분석한 뒤 사용자 확인 화면에 표시하는 Visit 초안이다. */
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

/** 검토가 끝난 Visit을 실제로 생성·수정할 때 사용하는 입력값이다. */
export interface VisitInput extends VisitScores {
  areaId: number
  visitDate: string
  memo: string | null
}

/** Visit 생성·수정 결과에 포함되는 저장된 값이다. */
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

/** Visit 편집 화면에서 사용하는 단건 상세 정보이다. */
export interface VisitDetail extends VisitScores {
  id: number
  area: AreaSummary
  visitDate: string
  memo: string | null
  createdAt: string
  updatedAt: string
}

/** Backend가 지원하는 AI Report 분석 유형이다. */
export type ReportType = 'SUMMARY' | 'ALL' | 'AREA' | 'COMPARE'

/** Report 유형과 분석 대상 Area를 지정하는 생성 입력값이다. */
export interface ReportCreateInput {
  reportType: ReportType
  areaIds?: number[]
}

/** Report 목록과 생성 결과에 사용하는 Metadata 요약이다. */
export interface ReportSummary {
  id: number
  reportType: ReportType
  model: string
  promptVersion: string
  createdAt: string
}

/** Report Metadata와 생성 당시 분석 대상 Area ID를 포함한 상세 정보이다. */
export interface ReportDetail extends ReportSummary {
  areaIds: number[]
}

/** 전체 또는 Area별 방문 점수 평균이다. 방문이 없으면 각 값은 null이다. */
export interface ScoreAverages {
  atmosphere: number | null
  infra: number | null
  clean: number | null
  size: number | null
  access: number | null
}

/** 평가 항목별 상위 Area 한 곳의 이름과 평균 점수이다. */
export interface TopArea {
  areaId: number
  areaName: string
  score: number
}

/** 다섯 평가 항목 각각의 Area Top 5 결과이다. */
export type TopFive = Record<keyof ScoreAverages, TopArea[]>

/** Dashboard와 전체 통계 화면에서 사용하는 집계 결과이다. */
export interface OverallStatistics {
  areaCount: number
  visitCount: number
  averageScores: ScoreAverages
  top5: TopFive
}

/** 선택한 Area의 방문 수와 점수 평균이다. */
export interface AreaStatistics {
  areaId: number
  areaName: string
  visitCount: number
  averageScores: ScoreAverages
}

/** Backend Bean Validation이 반환한 필드 단위 오류이다. */
export interface ValidationError {
  field: string
  reason: string
}

/** 모든 실패 응답에 공통으로 적용되는 Backend 오류 형식이다. */
export interface ApiErrorResponse {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  errors?: ValidationError[]
}

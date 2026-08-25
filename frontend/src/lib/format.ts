import type { ReportType } from '../types/api'

export const scoreLabels = {
  atmosphere: '분위기',
  infra: '생활 인프라',
  clean: '청결도',
  size: '넓은 집 가능성',
  access: '접근성',
} as const

export const reportTypeLabels: Record<ReportType, string> = {
  SUMMARY: '전체 요약',
  ALL: '전체 상세 분석',
  AREA: '한 지역 분석',
  COMPARE: '지역 비교',
}

const dateTimeFormatter = new Intl.DateTimeFormat('ko-KR', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
})

export function formatDateTime(value: string) {
  return dateTimeFormatter.format(new Date(value))
}

export function formatScore(value: number | null) {
  return value === null ? '—' : value.toFixed(1)
}

import type { ReportType } from '../types/api'

/** Backend 점수 필드명을 사용자 화면의 한국어 Label로 변환한다. */
export const scoreLabels = {
  atmosphere: '분위기',
  infra: '생활 인프라',
  clean: '청결도',
  size: '넓은 집 가능성',
  access: '접근성',
} as const

/** Report 유형별 화면 표시 이름이다. */
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

/** UTC ISO 일시를 사용자의 한국어 Local 일시로 표시한다. */
export function formatDateTime(value: string) {
  return dateTimeFormatter.format(new Date(value))
}

/** 값이 없는 평균은 대시로, 값이 있으면 소수점 한 자리로 표시한다. */
export function formatScore(value: number | null) {
  return value === null ? '—' : value.toFixed(1)
}

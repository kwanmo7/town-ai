import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import { VisitDraftDialog } from './VisitDraftDialog'

const areas = [{
  id: 1,
  name: '센터미나미',
  prefecture: '가나가와현',
  city: '요코하마시 츠즈키구',
  station: '센터미나미역',
}]

describe('VisitDraftDialog', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('자연어와 다섯 점수가 없으면 초안 API를 호출하지 않는다', () => {
    const createVisitDraft = vi.spyOn(api, 'createVisitDraft')

    render(
      <VisitDraftDialog areas={areas} onClose={vi.fn()} onSaved={vi.fn()} />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'AI 초안 만들기' }))

    expect(screen.getByText('방문한 지역과 느낀 점을 입력해주세요.')).toBeInTheDocument()
    expect(screen.getAllByText('점수를 선택해주세요.')).toHaveLength(5)
    expect(createVisitDraft).not.toHaveBeenCalled()
  })

  it('Web 선택 점수를 유지한 AI 초안을 확인하고 기존 Area에 Visit을 저장한다', async () => {
    const createVisitDraft = vi.spyOn(api, 'createVisitDraft').mockResolvedValue({
      area: areas[0],
      visitDate: '2026-08-10',
      atmosphereScore: 8,
      infraScore: 9,
      cleanScore: 8,
      sizeScore: 7,
      accessScore: 7,
      memo: '역 앞 광장이 넓고 쇼핑 동선이 편했다.',
      warnings: [],
    })
    const createVisit = vi.spyOn(api, 'createVisit').mockResolvedValue({
      id: 6,
      area: { id: 1, name: '센터미나미' },
      visitDate: '2026-08-10',
      atmosphereScore: 8,
      infraScore: 9,
      cleanScore: 8,
      sizeScore: 7,
      accessScore: 7,
      memo: '역 앞 광장이 넓고 쇼핑 동선이 편했다.',
      createdAt: '2026-08-11T12:00:00Z',
      updatedAt: '2026-08-11T12:00:00Z',
    })
    const createArea = vi.spyOn(api, 'createArea')
    const onSaved = vi.fn().mockResolvedValue(undefined)

    render(
      <VisitDraftDialog areas={areas} onClose={vi.fn()} onSaved={onSaved} />,
    )

    fireEvent.change(screen.getByLabelText(/방문 내용/), {
      target: { value: '어제 센터미나미를 걸었고 역 앞 광장이 편했어.' },
    })
    selectScore('분위기', '8')
    selectScore('생활 인프라', '9')
    selectScore('청결도', '8')
    selectScore('넓은 집 가능성', '7')
    selectScore('접근성', '7')
    fireEvent.click(screen.getByRole('button', { name: 'AI 초안 만들기' }))

    expect(await screen.findByRole('heading', { name: 'AI 초안 확인' })).toBeInTheDocument()
    expect(createVisitDraft).toHaveBeenCalledWith({
      text: '어제 센터미나미를 걸었고 역 앞 광장이 편했어.',
      atmosphereScore: 8,
      infraScore: 9,
      cleanScore: 8,
      sizeScore: 7,
      accessScore: 7,
    })

    fireEvent.click(screen.getByRole('button', { name: '확인 후 저장' }))

    await waitFor(() => {
      expect(createVisit).toHaveBeenCalledWith({
        areaId: 1,
        visitDate: '2026-08-10',
        atmosphereScore: 8,
        infraScore: 9,
        cleanScore: 8,
        sizeScore: 7,
        accessScore: 7,
        memo: '역 앞 광장이 넓고 쇼핑 동선이 편했다.',
      })
    })
    expect(createArea).not.toHaveBeenCalled()
    expect(onSaved).toHaveBeenCalledWith('센터미나미 방문 기록을 등록했습니다.')
  })

  it('신규 Area 후보를 확인한 뒤 Area와 Visit을 순서대로 저장한다', async () => {
    vi.spyOn(api, 'createVisitDraft').mockResolvedValue({
      area: {
        id: null,
        name: '후타코타마가와',
        prefecture: '도쿄도',
        city: '세타가야구',
        station: '후타코타마가와역',
      },
      visitDate: '2026-08-10',
      atmosphereScore: 8,
      infraScore: 8,
      cleanScore: 8,
      sizeScore: 6,
      accessScore: 8,
      memo: '강변과 상업 시설을 함께 둘러봤다.',
      warnings: ['AI가 보완한 위치 정보를 확인해주세요.'],
    })
    const createArea = vi.spyOn(api, 'createArea').mockResolvedValue({
      id: 4,
      name: '후타코타마가와',
      prefecture: '도쿄도',
      city: '세타가야구',
      station: '후타코타마가와역',
      createdAt: '2026-08-11T12:00:00Z',
      updatedAt: '2026-08-11T12:00:00Z',
    })
    const createVisit = vi.spyOn(api, 'createVisit').mockResolvedValue({
      id: 7,
      area: { id: 4, name: '후타코타마가와' },
      visitDate: '2026-08-10',
      atmosphereScore: 8,
      infraScore: 8,
      cleanScore: 8,
      sizeScore: 6,
      accessScore: 8,
      memo: '강변과 상업 시설을 함께 둘러봤다.',
      createdAt: '2026-08-11T12:00:00Z',
      updatedAt: '2026-08-11T12:00:00Z',
    })

    render(
      <VisitDraftDialog areas={areas} onClose={vi.fn()} onSaved={vi.fn()} />,
    )

    fireEvent.change(screen.getByLabelText(/방문 내용/), {
      target: { value: '어제 후타코타마가와의 강변과 상업 시설을 둘러봤어.' },
    })
    selectScore('분위기', '8')
    selectScore('생활 인프라', '8')
    selectScore('청결도', '8')
    selectScore('넓은 집 가능성', '6')
    selectScore('접근성', '8')
    fireEvent.click(screen.getByRole('button', { name: 'AI 초안 만들기' }))

    expect(await screen.findByDisplayValue('후타코타마가와')).toBeInTheDocument()
    expect(screen.getByText('AI가 보완한 위치 정보를 확인해주세요.')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '확인 후 저장' }))

    await waitFor(() => {
      expect(createArea).toHaveBeenCalledWith({
        name: '후타코타마가와',
        prefecture: '도쿄도',
        city: '세타가야구',
        station: '후타코타마가와역',
      })
      expect(createVisit).toHaveBeenCalledWith(expect.objectContaining({
        areaId: 4,
        visitDate: '2026-08-10',
      }))
    })
    expect(createArea.mock.invocationCallOrder[0])
      .toBeLessThan(createVisit.mock.invocationCallOrder[0])
  })
})

function selectScore(label: string, value: string) {
  fireEvent.change(screen.getByLabelText(new RegExp(label)), {
    target: { value },
  })
}

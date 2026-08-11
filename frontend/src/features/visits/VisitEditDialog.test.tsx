import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import { VisitEditDialog } from './VisitEditDialog'

const area = {
  id: 1,
  name: '센터미나미',
  prefecture: '가나가와현',
  city: '요코하마시 츠즈키구',
  station: '센터미나미역',
}

describe('VisitEditDialog', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('상세 값을 불러와 PUT 전체 수정 요청을 전송한다', async () => {
    vi.spyOn(api, 'getVisit').mockResolvedValue({
      id: 5,
      area,
      visitDate: '2026-08-10',
      atmosphereScore: 8,
      infraScore: 8,
      cleanScore: 8,
      sizeScore: 7,
      accessScore: 7,
      memo: '기존 메모',
      createdAt: '2026-08-11T12:00:00Z',
      updatedAt: '2026-08-11T12:00:00Z',
    })
    const updateVisitSpy = vi.spyOn(api, 'updateVisit').mockResolvedValue({
      id: 5,
      area: { id: 1, name: '센터미나미' },
      visitDate: '2026-08-11',
      atmosphereScore: 8,
      infraScore: 9,
      cleanScore: 8,
      sizeScore: 7,
      accessScore: 7,
      memo: '수정한 메모',
      createdAt: '2026-08-11T12:00:00Z',
      updatedAt: '2026-08-11T13:00:00Z',
    })
    const onSaved = vi.fn().mockResolvedValue(undefined)

    render(
      <VisitEditDialog
        visitId={5}
        areas={[area]}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    )

    expect(await screen.findByDisplayValue('기존 메모')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText(/방문일/), {
      target: { value: '2026-08-11' },
    })
    fireEvent.change(screen.getByLabelText(/생활 인프라/), {
      target: { value: '9' },
    })
    fireEvent.change(screen.getByLabelText('메모'), {
      target: { value: '수정한 메모' },
    })
    fireEvent.click(screen.getByRole('button', { name: '수정 저장' }))

    await waitFor(() => expect(updateVisitSpy).toHaveBeenCalledWith(5, {
      areaId: 1,
      visitDate: '2026-08-11',
      atmosphereScore: 8,
      infraScore: 9,
      cleanScore: 8,
      sizeScore: 7,
      accessScore: 7,
      memo: '수정한 메모',
    }))
    expect(onSaved).toHaveBeenCalledWith('센터미나미 방문 기록을 수정했습니다.')
  })
})

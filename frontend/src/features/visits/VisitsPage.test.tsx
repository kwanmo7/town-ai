import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import { VisitsPage } from './VisitsPage'

const area = {
  id: 1,
  name: '센터미나미',
  prefecture: '가나가와현',
  city: '요코하마시 츠즈키구',
  station: '센터미나미역',
}

const visit = {
  id: 5,
  area: { id: 1, name: '센터미나미' },
  visitDate: '2026-08-10',
  atmosphereScore: 8,
  infraScore: 9,
  cleanScore: 8,
  sizeScore: 7,
  accessScore: 7,
}

describe('VisitsPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('확인 후 Visit을 삭제하고 목록을 다시 조회한다', async () => {
    const getVisitsSpy = vi.spyOn(api, 'getVisits')
      .mockResolvedValueOnce([visit])
      .mockResolvedValueOnce([])
    vi.spyOn(api, 'getAreas').mockResolvedValue([area])
    const deleteVisitSpy = vi.spyOn(api, 'deleteVisit').mockResolvedValue()

    render(<VisitsPage />)

    fireEvent.click(await screen.findByRole('button', { name: '삭제' }))
    const dialog = screen.getByRole('alertdialog')
    expect(within(dialog).getByText('센터미나미 방문 기록을 삭제할까요?'))
      .toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: '삭제' }))

    await waitFor(() => expect(deleteVisitSpy).toHaveBeenCalledWith(5))
    await waitFor(() => expect(getVisitsSpy).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('센터미나미 방문 기록을 삭제했습니다.'))
      .toBeInTheDocument()
    expect(screen.getByText('아직 방문 기록이 없습니다.')).toBeInTheDocument()
  })
})

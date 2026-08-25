import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import { StatisticsPage } from './StatisticsPage'

const area = {
  id: 1,
  name: '센터미나미',
  prefecture: '가나가와현',
  city: '요코하마시 츠즈키구',
  station: '센터미나미역',
}

describe('StatisticsPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('전체 평균과 Top 5를 표시하고 선택한 지역 통계를 조회한다', async () => {
    vi.spyOn(api, 'getStatistics').mockResolvedValue({
      areaCount: 2,
      visitCount: 5,
      averageScores: {
        atmosphere: 8.2,
        infra: 8.6,
        clean: 8,
        size: 6.6,
        access: 7.8,
      },
      top5: {
        atmosphere: [{ areaId: 1, areaName: '센터미나미', score: 9.2 }],
        infra: [],
        clean: [],
        size: [],
        access: [],
      },
    })
    vi.spyOn(api, 'getAreas').mockResolvedValue([area])
    const getAreaStatisticsSpy = vi.spyOn(api, 'getAreaStatistics').mockResolvedValue({
      areaId: 1,
      areaName: '센터미나미',
      visitCount: 3,
      averageScores: {
        atmosphere: 9.2,
        infra: 9,
        clean: 8.7,
        size: 7.3,
        access: 8.1,
      },
    })

    render(<StatisticsPage />)

    expect(await screen.findByRole('heading', { name: '항목별 상위 지역' }))
      .toBeInTheDocument()
    expect(screen.getByRole('button', { name: /센터미나미/ })).toHaveTextContent('9.2')

    fireEvent.change(screen.getByLabelText('확인할 지역'), {
      target: { value: '1' },
    })

    await waitFor(() => expect(getAreaStatisticsSpy).toHaveBeenCalledWith(1))
    expect(await screen.findByText('방문 3회')).toBeInTheDocument()
  })
})

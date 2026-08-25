import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from '../../lib/api'
import { AreaFormDialog } from './AreaFormDialog'

describe('AreaFormDialog', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('필수 입력이 비어 있으면 API를 호출하지 않는다', () => {
    const createArea = vi.spyOn(api, 'createArea')

    render(
      <AreaFormDialog
        area={null}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: '지역 등록' }))

    expect(screen.getAllByText('필수 입력 항목입니다.')).toHaveLength(3)
    expect(createArea).not.toHaveBeenCalled()
  })

  it('입력값을 trim하고 빈 인접 역을 null로 등록한다', async () => {
    const createArea = vi.spyOn(api, 'createArea').mockResolvedValue({
      id: 4,
      name: '후타코타마가와',
      prefecture: '도쿄도',
      city: '세타가야구',
      station: null,
      createdAt: '2026-08-11T12:00:00Z',
      updatedAt: '2026-08-11T12:00:00Z',
    })
    const onSaved = vi.fn().mockResolvedValue(undefined)

    render(
      <AreaFormDialog area={null} onClose={vi.fn()} onSaved={onSaved} />,
    )

    fireEvent.change(screen.getByLabelText(/지역명/), {
      target: { value: '  후타코타마가와  ' },
    })
    fireEvent.change(screen.getByLabelText(/도도부현/), {
      target: { value: ' 도쿄도 ' },
    })
    fireEvent.change(screen.getByLabelText(/시구정촌/), {
      target: { value: ' 세타가야구 ' },
    })
    fireEvent.change(screen.getByLabelText('인접 역'), {
      target: { value: '   ' },
    })
    fireEvent.click(screen.getByRole('button', { name: '지역 등록' }))

    await waitFor(() => {
      expect(createArea).toHaveBeenCalledWith({
        name: '후타코타마가와',
        prefecture: '도쿄도',
        city: '세타가야구',
        station: null,
      })
    })
    expect(onSaved).toHaveBeenCalledWith('후타코타마가와 지역을 등록했습니다.')
  })

  it('Backend 필드 검증 오류를 해당 입력 아래에 표시한다', async () => {
    vi.spyOn(api, 'createArea').mockRejectedValue(new ApiError(
      '입력값을 확인해주세요.',
      400,
      'VALIDATION_FAILED',
      {
        timestamp: '2026-08-11T12:00:00Z',
        status: 400,
        code: 'VALIDATION_FAILED',
        message: '입력값을 확인해주세요.',
        path: '/api/areas',
        errors: [{ field: 'city', reason: '시구정촌을 확인해주세요.' }],
      },
    ))

    render(
      <AreaFormDialog area={null} onClose={vi.fn()} onSaved={vi.fn()} />,
    )

    fireEvent.change(screen.getByLabelText(/지역명/), { target: { value: '테스트' } })
    fireEvent.change(screen.getByLabelText(/도도부현/), { target: { value: '도쿄도' } })
    fireEvent.change(screen.getByLabelText(/시구정촌/), { target: { value: '테스트구' } })
    fireEvent.click(screen.getByRole('button', { name: '지역 등록' }))

    expect(await screen.findByText('시구정촌을 확인해주세요.')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('입력값을 확인해주세요.')
  })
})

import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AreaDeleteDialog } from './AreaDeleteDialog'

const area = {
  id: 1,
  name: '센터미나미',
  prefecture: '가나가와현',
  city: '요코하마시 츠즈키구',
  station: '센터미나미역',
}

describe('AreaDeleteDialog', () => {
  it('삭제 정책을 안내하고 확인 동작을 전달한다', () => {
    const onConfirm = vi.fn()

    render(
      <AreaDeleteDialog
        area={area}
        isDeleting={false}
        error={null}
        onCancel={vi.fn()}
        onConfirm={onConfirm}
      />,
    )

    expect(screen.getByText('센터미나미 지역을 삭제할까요?')).toBeInTheDocument()
    expect(screen.getByText(/기존 방문 기록은/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '삭제' }))
    expect(onConfirm).toHaveBeenCalledOnce()
  })
})

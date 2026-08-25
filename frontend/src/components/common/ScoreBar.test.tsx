import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ScoreBar } from './ScoreBar'

describe('ScoreBar', () => {
  it('점수를 소수점 첫째 자리까지 표시한다', () => {
    render(<ScoreBar label="분위기" value={8.4} />)

    expect(screen.getByText('분위기')).toBeInTheDocument()
    expect(screen.getByText('8.4')).toBeInTheDocument()
  })

  it('집계값이 없으면 대시를 표시한다', () => {
    render(<ScoreBar label="접근성" value={null} />)

    expect(screen.getByText('—')).toBeInTheDocument()
  })
})

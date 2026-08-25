import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AuthBoundary } from './AuthBoundary'
import { AuthContext } from './AuthContext'

const baseValue = {
  user: null,
  enabled: true,
  loading: false,
  error: null,
  firebaseEmail: null,
  signIn: vi.fn(async () => undefined),
  signOut: vi.fn(async () => undefined),
}

describe('AuthBoundary', () => {
  it('인증을 사용하지 않는 Local 환경에서는 화면을 바로 표시한다', () => {
    render(
      <AuthContext.Provider value={{ ...baseValue, enabled: false }}>
        <AuthBoundary><div>관리 화면</div></AuthBoundary>
      </AuthContext.Provider>,
    )

    expect(screen.getByText('관리 화면')).toBeInTheDocument()
  })

  it('인증 상태를 확인하는 동안 Loading 화면을 표시한다', () => {
    render(
      <AuthContext.Provider value={{ ...baseValue, loading: true }}>
        <AuthBoundary><div>관리 화면</div></AuthBoundary>
      </AuthContext.Provider>,
    )

    expect(screen.getByText('로그인 상태를 확인하고 있습니다.')).toBeInTheDocument()
    expect(screen.queryByText('관리 화면')).not.toBeInTheDocument()
  })

  it('인증된 허용 사용자에게 관리 화면을 표시한다', () => {
    render(
      <AuthContext.Provider value={{
        ...baseValue,
        user: { uid: 'allowed-uid', email: 'owner@example.com', name: 'Owner' },
      }}>
        <AuthBoundary><div>관리 화면</div></AuthBoundary>
      </AuthContext.Provider>,
    )

    expect(screen.getByText('관리 화면')).toBeInTheDocument()
  })
})

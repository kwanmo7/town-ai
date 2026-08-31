import type { PropsWithChildren } from 'react'
import { LoginPage } from './LoginPage'
import { useAuth } from './AuthContext'

/** 인증 설정과 권한 확인 상태에 따라 관리 화면 또는 로그인 화면을 선택한다. */
export function AuthBoundary({ children }: PropsWithChildren) {
  const auth = useAuth()

  if (!auth.enabled) {
    // Local Backend 개발에서는 Firebase 없이 동일한 관리 화면을 검증한다.
    return children
  }
  if (auth.loading) {
    return (
      <main className="auth-page" aria-busy="true">
        <section className="auth-card auth-card--loading">
          <span className="auth-card__mark">T</span>
          <p>로그인 상태를 확인하고 있습니다.</p>
        </section>
      </main>
    )
  }
  if (auth.user === null) {
    return <LoginPage />
  }
  return children
}

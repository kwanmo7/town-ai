import type { PropsWithChildren } from 'react'
import { LoginPage } from './LoginPage'
import { useAuth } from './AuthContext'

export function AuthBoundary({ children }: PropsWithChildren) {
  const auth = useAuth()

  if (!auth.enabled) {
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

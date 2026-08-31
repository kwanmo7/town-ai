import { useState } from 'react'
import { useAuth } from './AuthContext'

/** Google 로그인과 Backend 권한 오류를 안내하는 관리 화면 진입점이다. */
export function LoginPage() {
  const auth = useAuth()
  const [submitting, setSubmitting] = useState(false)

  const handleSignIn = async () => {
    setSubmitting(true)
    try {
      await auth.signIn()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby="login-title">
        <span className="auth-card__eyebrow">PERSONAL WORKSPACE</span>
        <span className="auth-card__mark" aria-hidden="true">T</span>
        <h1 id="login-title">Town AI</h1>
        <p>지역 방문 기록과 AI 리포트를 관리하려면 Google 계정으로 로그인해주세요.</p>

        {auth.error && <div className="auth-card__error" role="alert">{auth.error}</div>}
        {auth.firebaseEmail && (
          <p className="auth-card__account">현재 계정: {auth.firebaseEmail}</p>
        )}

        <div className="auth-card__actions">
          <button
            type="button"
            className="button button--primary auth-card__button"
            disabled={submitting}
            onClick={handleSignIn}
          >
            {submitting ? '로그인 중...' : 'Google로 로그인'}
          </button>
          {auth.firebaseEmail && (
            <button
              type="button"
              className="button button--secondary auth-card__button"
              onClick={() => void auth.signOut()}
            >
              다른 계정 사용
            </button>
          )}
        </div>
      </section>
    </main>
  )
}

import { useEffect, useMemo, useState, type PropsWithChildren } from 'react'
import {
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signOut as firebaseSignOut,
  type User,
} from 'firebase/auth'
import { api, ApiError } from '../../lib/api'
import { firebaseAuth, isWebAuthEnabled } from '../../lib/firebase'
import type { AuthenticatedUser } from '../../types/api'
import { AuthContext } from './AuthContext'

/** Firebase 로그인 상태와 Backend의 단일 UID 권한 확인 결과를 하나의 Context로 제공한다. */
export function AuthProvider({ children }: PropsWithChildren) {
  const [firebaseUser, setFirebaseUser] = useState<User | null>(null)
  const [user, setUser] = useState<AuthenticatedUser | null>(null)
  const [loading, setLoading] = useState(isWebAuthEnabled)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!isWebAuthEnabled) {
      return undefined
    }

    return onAuthStateChanged(firebaseAuth, async (nextUser) => {
      setFirebaseUser(nextUser)
      setUser(null)
      setError(null)

      if (nextUser === null) {
        setLoading(false)
        return
      }

      setLoading(true)
      try {
        // Google 로그인 성공만으로 관리 권한을 부여하지 않고 Backend에서 UID를 다시 확인한다.
        setUser(await api.getAuthenticatedUser())
      } catch (caught) {
        setError(toAuthMessage(caught))
      } finally {
        setLoading(false)
      }
    })
  }, [])

  const value = useMemo(() => ({
    user,
    enabled: isWebAuthEnabled,
    loading,
    error,
    firebaseEmail: firebaseUser?.email ?? null,
    signIn: async () => {
      setError(null)
      const provider = new GoogleAuthProvider()
      // 이전 Google Session이 있어도 개인 관리 계정을 명시적으로 선택할 수 있게 한다.
      provider.setCustomParameters({ prompt: 'select_account' })
      try {
        await signInWithPopup(firebaseAuth, provider)
      } catch (caught) {
        setError(toSignInMessage(caught))
      }
    },
    signOut: async () => {
      await firebaseSignOut(firebaseAuth)
      setUser(null)
      setError(null)
    },
  }), [error, firebaseUser, loading, user])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

function toAuthMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 403) {
    return '이 Google 계정은 Town AI 관리 권한이 없습니다.'
  }
  if (error instanceof ApiError && error.status === 401) {
    return '로그인 확인에 실패했습니다. 다시 로그인해주세요.'
  }
  return 'Backend 인증 상태를 확인하지 못했습니다.'
}

function toSignInMessage(error: unknown): string {
  if (typeof error === 'object' && error !== null && 'code' in error
    && error.code === 'auth/popup-closed-by-user') {
    return 'Google 로그인이 취소되었습니다.'
  }
  return 'Google 로그인 중 오류가 발생했습니다.'
}

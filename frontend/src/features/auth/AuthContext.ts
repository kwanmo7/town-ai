import { createContext, useContext } from 'react'
import type { AuthenticatedUser } from '../../types/api'

interface AuthContextValue {
  user: AuthenticatedUser | null
  enabled: boolean
  loading: boolean
  error: string | null
  firebaseEmail: string | null
  signIn: () => Promise<void>
  signOut: () => Promise<void>
}

/** 애플리케이션 전체에서 공유하는 Firebase·Backend 통합 인증 Context이다. */
export const AuthContext = createContext<AuthContextValue | null>(null)

/** AuthProvider가 제공하는 Firebase·Backend 통합 인증 상태를 읽는다. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used within AuthProvider.')
  }
  return context
}

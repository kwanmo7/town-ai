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

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (context === null) {
    throw new Error('useAuth must be used within AuthProvider.')
  }
  return context
}

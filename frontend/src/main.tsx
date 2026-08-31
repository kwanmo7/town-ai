import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { App } from './app/App'
import { AuthBoundary } from './features/auth/AuthBoundary'
import { AuthProvider } from './features/auth/AuthProvider'
import './styles/index.css'

// 인증 상태를 먼저 복원한 뒤 AuthBoundary를 통과한 화면만 Router에 노출한다.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <AuthBoundary>
          <App />
        </AuthBoundary>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)

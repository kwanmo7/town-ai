import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../../features/auth/AuthContext'

const navigation = [
  { to: '/', label: '대시보드', shortLabel: '홈', icon: '⌂', end: true },
  { to: '/areas', label: '지역 관리', shortLabel: '지역', icon: '◇' },
  { to: '/visits', label: '방문 기록', shortLabel: '방문', icon: '✓' },
  { to: '/statistics', label: '통계 분석', shortLabel: '통계', icon: '◎' },
  { to: '/reports', label: 'AI 리포트', shortLabel: '리포트', icon: '≋' },
]

const pageNames: Record<string, string> = {
  '/': '대시보드',
  '/areas': '지역 관리',
  '/visits': '방문 기록',
  '/statistics': '통계 분석',
  '/reports': 'AI 리포트',
}

export function AppShell() {
  const auth = useAuth()
  const location = useLocation()
  const pageName = location.pathname.startsWith('/reports/')
    ? '리포트 상세'
    : pageNames[location.pathname] ?? 'Town AI'

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <NavLink className="brand" to="/" aria-label="Town AI 대시보드">
          <span className="brand__mark">T</span>
          <span>
            <strong>Town AI</strong>
            <small>Living decision log</small>
          </span>
        </NavLink>

        <nav className="sidebar__nav" aria-label="주 메뉴">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) => (isActive ? 'nav-item is-active' : 'nav-item')}
            >
              <span className="nav-item__icon" aria-hidden="true">{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar__note">
          <span className="status-dot" />
          <div>
            <strong>{auth.user?.name || 'Personal workspace'}</strong>
            <small>{auth.user?.email || 'Asia / Tokyo'}</small>
          </div>
        </div>
      </aside>

      <div className="app-frame">
        <header className="topbar">
          <div>
            <span className="topbar__kicker">TOWN AI</span>
            <strong>{pageName}</strong>
          </div>
          <div className="topbar__tools">
            <span className="topbar__date">
              {new Intl.DateTimeFormat('ko-KR', {
                timeZone: 'Asia/Tokyo',
                month: 'long',
                day: 'numeric',
                weekday: 'short',
              }).format(new Date())}
            </span>
            {auth.enabled && (
              <button
                type="button"
                className="topbar__logout"
                onClick={() => void auth.signOut()}
              >
                로그아웃
              </button>
            )}
          </div>
        </header>

        <main className="main-content">
          <Outlet />
        </main>
      </div>

      <nav className="bottom-nav" aria-label="모바일 주 메뉴">
        {navigation.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) => (isActive ? 'is-active' : '')}
          >
            <span aria-hidden="true">{item.icon}</span>
            <small>{item.shortLabel}</small>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

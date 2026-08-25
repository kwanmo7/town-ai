import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from '../components/layout/AppShell'
import { AreasPage } from '../features/areas/AreasPage'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { ReportDetailPage } from '../features/reports/ReportDetailPage'
import { ReportsPage } from '../features/reports/ReportsPage'
import { StatisticsPage } from '../features/statistics/StatisticsPage'
import { VisitsPage } from '../features/visits/VisitsPage'

export function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<DashboardPage />} />
        <Route path="areas" element={<AreasPage />} />
        <Route path="visits" element={<VisitsPage />} />
        <Route path="statistics" element={<StatisticsPage />} />
        <Route path="reports" element={<ReportsPage />} />
        <Route path="reports/:reportId" element={<ReportDetailPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

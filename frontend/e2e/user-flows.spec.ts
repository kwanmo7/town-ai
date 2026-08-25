import { expect, test } from '@playwright/test'
import { createApiMockState, installApiMock } from './api-mock'

test('방문 기록을 수정한 뒤 Hard Delete한다', async ({ page }) => {
  const state = createApiMockState()
  await installApiMock(page, state)
  await page.goto('/visits')

  await page.getByRole('button', { name: '수정' }).first().click()
  await expect(page.getByRole('heading', { name: '방문 기록 수정' })).toBeVisible()
  await page.getByLabel('생활 인프라', { exact: false }).selectOption('10')
  await page.getByLabel('메모').fill('E2E에서 수정한 방문 메모')
  await page.getByRole('button', { name: '수정 저장' }).click()
  await expect(page.getByRole('status')).toContainText('방문 기록을 수정했습니다.')
  expect(state.visits[0].infraScore).toBe(10)
  expect(state.visits[0].memo).toBe('E2E에서 수정한 방문 메모')

  await page.getByRole('button', { name: '삭제' }).first().click()
  const dialog = page.getByRole('alertdialog')
  await expect(dialog).toContainText('복구할 수 없으며 이후 통계와 새 리포트에서 제외됩니다.')
  await dialog.getByRole('button', { name: '삭제' }).click()
  await expect(page.getByRole('status')).toContainText('방문 기록을 삭제했습니다.')
  expect(state.visits).toHaveLength(1)
})

test('리포트 유형을 즉시 필터링하고 선택한 리포트를 삭제한다', async ({ page }) => {
  const state = createApiMockState()
  await installApiMock(page, state)
  await page.goto('/reports')

  await page.getByRole('button', { name: '전체 요약' }).click()
  await expect(page.getByRole('heading', { name: '전체 요약 리포트' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '한 지역 분석 리포트' })).toHaveCount(0)

  await page.getByRole('button', { name: '전체 요약 리포트 #10 삭제' }).click()
  await page.getByRole('alertdialog').getByRole('button', { name: '삭제' }).click()
  await expect(page.getByRole('status')).toContainText('전체 요약 #10을 삭제했습니다.')
  await expect(page.getByText('전체 요약 리포트가 없습니다.')).toBeVisible()
  expect(state.reports.map((report) => report.id)).not.toContain(10)
})

test('Top 5 지역에서 Area별 누적 통계를 조회한다', async ({ page }) => {
  await installApiMock(page)
  await page.goto('/statistics')

  const infrastructureRanking = page.locator('.ranking-card').filter({ hasText: '생활 인프라' })
  await infrastructureRanking.getByRole('button', { name: /센터미나미/ }).click()
  await expect(page.getByText('방문 1회')).toBeVisible()
  await expect(page.locator('#statistics-area')).toHaveValue('1')
})

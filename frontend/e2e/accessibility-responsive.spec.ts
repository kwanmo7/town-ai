import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { installApiMock } from './api-mock'

const screens = [
  { path: '/', heading: '동네 선택의 근거를 한곳에' },
  { path: '/areas', heading: '관심 지역' },
  { path: '/visits', heading: '방문 기록' },
  { path: '/statistics', heading: '방문 점수 통계' },
  { path: '/reports', heading: '리포트 보관함' },
  { path: '/reports/10', heading: '전체 요약 리포트' },
]

test.beforeEach(async ({ page }) => {
  await installApiMock(page)
})

for (const screen of screens) {
  test(`${screen.path} 화면은 심각한 접근성 위반과 가로 넘침이 없다`, async ({ page }) => {
    await page.goto(screen.path)
    await expect(page.getByRole('heading', { level: 1, name: screen.heading })).toBeVisible()

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze()
    const blockingViolations = results.violations
      .filter((violation) => (
        violation.impact === 'serious' || violation.impact === 'critical'
      ))
      .flatMap((violation) => violation.nodes.map((node) => ({
        rule: violation.id,
        target: node.target.join(' '),
        summary: node.failureSummary,
      })))
    expect(blockingViolations).toEqual([])

    const hasHorizontalOverflow = await page.evaluate(() => (
      document.documentElement.scrollWidth > document.documentElement.clientWidth
    ))
    expect(hasHorizontalOverflow).toBe(false)
  })
}

test('화면 크기에 따라 Sidebar와 모바일 Navigation을 전환한다', async ({ page, isMobile }) => {
  await page.goto('/')
  const sidebar = page.locator('.sidebar')
  const bottomNavigation = page.locator('.bottom-nav')

  if (isMobile) {
    await expect(sidebar).toBeHidden()
    await expect(bottomNavigation).toBeVisible()
    await expect(bottomNavigation.getByRole('link')).toHaveCount(5)
  } else {
    await expect(sidebar).toBeVisible()
    await expect(bottomNavigation).toBeHidden()
    await expect(sidebar.getByRole('link')).toHaveCount(6)
  }
})

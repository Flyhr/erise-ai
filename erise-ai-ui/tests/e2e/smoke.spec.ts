import { test, expect } from '@playwright/test'

test('login page renders', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByText('项目知识与 AI 主链路')).toBeVisible()
})

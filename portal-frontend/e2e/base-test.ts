import { test as base } from '@playwright/test'
import path from 'path'

export { expect } from '@playwright/test'

/**
 * Custom test fixture that automatically captures a screenshot after every
 * page load and navigation. Tests import { test, expect } from this file
 * instead of from @playwright/test to get the behaviour for free.
 */
export const test = base.extend({
  page: async ({ page }, use, testInfo) => {
    let stepIndex = 0

    const snap = async (label: string) => {
      stepIndex++
      const prefix = String(stepIndex).padStart(3, '0')
      const safe = label.replace(/[^a-zA-Z0-9._-]/g, '_').slice(0, 60)
      try {
        await page.screenshot({
          path: path.join(testInfo.outputDir, `${prefix}-${safe}.png`),
          fullPage: true,
        })
      } catch {
        // page may be closed or mid-navigation
      }
    }

    // Screenshot after every full page load (covers goto, link clicks, form submits)
    page.on('load', () => {
      const url = page.url()
      if (url === 'about:blank') return
      const pathname = new URL(url).pathname.replace(/\//g, '_') || 'root'
      snap(`load${pathname}`).catch(() => {})
    })

    // Screenshot after redirects and client-side navigations
    page.on('framenavigated', frame => {
      if (frame !== page.mainFrame()) return
      const url = frame.url()
      if (url === 'about:blank') return
      const pathname = new URL(url).pathname.replace(/\//g, '_') || 'root'
      snap(`nav${pathname}`).catch(() => {})
    })

    // eslint-disable-next-line react-hooks/rules-of-hooks
    await use(page)

    await snap('test-end')
  },
})

'use client'

import { useActivityRefresh } from '@/hooks/use-activity-refresh'

/**
 * Manages automatic session refresh based on user activity
 */
export const SessionManager = () => {
  useActivityRefresh()
  return null
}

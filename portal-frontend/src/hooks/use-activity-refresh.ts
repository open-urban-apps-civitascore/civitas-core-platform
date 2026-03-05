'use client'

import { signOut, useSession } from 'next-auth/react'
import { useCallback, useEffect, useRef } from 'react'

const ACTIVITY_DEBOUNCE_MS = 2 * 60 * 1000
// Must be shorter than Keycloak ssoSessionIdleTimeout (3600s / 60min in production).
// Defaults to 55 minutes (5-min buffer); override via env var (in seconds).
const INACTIVITY_TIMEOUT_MS = (Number(process.env.NEXT_PUBLIC_SESSION_IDLE_TIMEOUT_SECONDS) || 55 * 60) * 1000

/**
 * Hook that refreshes the session on user activity and
 * triggers logout after prolonged inactivity (AR-6 session timeout).
 */
export const useActivityRefresh = () => {
  const { data: session, update } = useSession()
  const lastActivityRef = useRef<number>(Date.now())
  const inactivityTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined)

  const resetInactivityTimer = useCallback(() => {
    clearTimeout(inactivityTimerRef.current)
    inactivityTimerRef.current = setTimeout(() => {
      signOut({ redirectTo: '/login' })
    }, INACTIVITY_TIMEOUT_MS)
  }, [])

  useEffect(() => {
    if (!session) return

    resetInactivityTimer()

    const handleActivity = () => {
      const now = Date.now()
      if (now - lastActivityRef.current > ACTIVITY_DEBOUNCE_MS) {
        lastActivityRef.current = now
        update()
      }
      resetInactivityTimer()
    }

    const events = ['mousedown', 'keydown', 'scroll', 'touchstart']

    events.forEach(event => {
      document.addEventListener(event, handleActivity, { passive: true })
    })

    return () => {
      clearTimeout(inactivityTimerRef.current)
      events.forEach(event => {
        document.removeEventListener(event, handleActivity)
      })
    }
  }, [session, update, resetInactivityTimer])
}

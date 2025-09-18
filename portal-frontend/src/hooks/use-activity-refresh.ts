'use client'

import { useSession } from 'next-auth/react'
import { useEffect, useRef } from 'react'

/**
 * Hook that refreshes the session when user activity is detected
 * Helps keep the session alive while user is actively using the application
 */
export const useActivityRefresh = () => {
  const { data: session, update } = useSession()
  const lastActivityRef = useRef<number>(Date.now())

  useEffect(() => {
    if (!session) return

    const handleActivity = () => {
      const now = Date.now()
      const TWO_MINUTES = 2 * 60 * 1000
      // Only refresh if it's been more than 2 minutes since last activity refresh
      if (now - lastActivityRef.current > TWO_MINUTES) {
        lastActivityRef.current = now
        update() // Triggers session refresh
      }
    }

    const events = ['mousedown', 'keydown', 'scroll', 'touchstart']

    events.forEach(event => {
      document.addEventListener(event, handleActivity, { passive: true })
    })

    return () => {
      events.forEach(event => {
        document.removeEventListener(event, handleActivity)
      })
    }
  }, [session, update])
}

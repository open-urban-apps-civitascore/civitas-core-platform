'use client'

import { usePathname, useRouter, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'

import { UnsavedChangesContext } from './UnsavedChangesContext'

interface UnsavedChangesProviderProps {
  children: React.ReactNode
}

export const UnsavedChangesProvider = ({ children }: UnsavedChangesProviderProps) => {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)
  const [pendingNavigation, setPendingNavigation] = useState<{ type: 'push'; href: string } | { type: 'back' } | null>(
    null,
  )
  const [isSaving, setIsSaving] = useState(false)
  const isNavigatingRef = useRef(false)
  const saveHandlerRef = useRef<(() => Promise<boolean>) | null>(null)
  const refreshAfterNavigateRef = useRef(false)
  const pendingRefreshRef = useRef(false)

  const setSaveHandler = useCallback((handler: (() => Promise<boolean>) | null, shouldRefreshAfterNavigate = false) => {
    saveHandlerRef.current = handler
    refreshAfterNavigateRef.current = shouldRefreshAfterNavigate
  }, [])

  const requestNavigation = useCallback(
    (href: string) => {
      if (hasUnsavedChanges && !isNavigatingRef.current) {
        setPendingNavigation({ type: 'push', href })
      } else {
        router.push(href)
      }
    },
    [hasUnsavedChanges, router],
  )

  const requestBack = useCallback(() => {
    if (hasUnsavedChanges && !isNavigatingRef.current) {
      setPendingNavigation({ type: 'back' })
    } else {
      router.back()
    }
  }, [hasUnsavedChanges, router])

  const navigate = useCallback(() => {
    if (!pendingNavigation) return
    isNavigatingRef.current = true
    setHasUnsavedChanges(false)
    if (pendingNavigation.type === 'back') {
      router.back()
    } else {
      router.push(pendingNavigation.href)
    }
    setPendingNavigation(null)
  }, [pendingNavigation, router])

  const discardAndNavigate = useCallback(() => {
    navigate()
  }, [navigate])

  const saveAndNavigate = useCallback(async () => {
    if (!saveHandlerRef.current) return
    setIsSaving(true)
    const hasSuccess = await saveHandlerRef.current()
    setIsSaving(false)
    if (!hasSuccess) return
    // Refresh only after the route change (see effect below); refreshing around the push cancels it.
    pendingRefreshRef.current = refreshAfterNavigateRef.current
    // Defer so the save's state updates commit first, otherwise the re-render discards the push.
    setTimeout(navigate, 0)
  }, [navigate])

  const cancelNavigation = useCallback(() => {
    setPendingNavigation(null)
  }, [])

  // beforeunload: native browser dialog for tab close / URL change
  useEffect(() => {
    if (!hasUnsavedChanges) return

    const handleBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault()
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [hasUnsavedChanges])

  useEffect(() => {
    isNavigatingRef.current = false
    // Route change done: safe to invalidate the router cache now so the source route is fresh on return.
    if (pendingRefreshRef.current) {
      pendingRefreshRef.current = false
      router.refresh()
    }
  }, [pathname, searchParams, router])

  const contextValue = useMemo(
    () => ({
      hasUnsavedChanges,
      setHasUnsavedChanges,
      setSaveHandler,
      requestNavigation,
      requestBack,
    }),
    [hasUnsavedChanges, setSaveHandler, requestNavigation, requestBack],
  )

  return (
    <UnsavedChangesContext.Provider value={contextValue}>
      {children}
      <ExitWarningModal
        open={!!pendingNavigation}
        onOpenChange={open => {
          if (!open) cancelNavigation()
        }}
        onDiscard={discardAndNavigate}
        onConfirm={saveAndNavigate}
        isLoading={isSaving}
      />
    </UnsavedChangesContext.Provider>
  )
}

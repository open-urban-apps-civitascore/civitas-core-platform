'use client'

import { useRouter } from 'next/navigation'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'

import { UnsavedChangesContext } from './UnsavedChangesContext'

interface UnsavedChangesProviderProps {
  children: React.ReactNode
}

export const UnsavedChangesProvider = ({ children }: UnsavedChangesProviderProps) => {
  const router = useRouter()
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false)
  const [pendingNavigation, setPendingNavigation] = useState<{ type: 'push'; href: string } | { type: 'back' } | null>(
    null,
  )
  const [isSaving, setIsSaving] = useState(false)
  const isNavigatingRef = useRef(false)
  const saveHandlerRef = useRef<(() => Promise<boolean>) | null>(null)

  const setSaveHandler = useCallback((handler: (() => Promise<boolean>) | null) => {
    saveHandlerRef.current = handler
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
    setTimeout(() => {
      isNavigatingRef.current = false
    }, 100)
  }, [pendingNavigation, router])

  const discardAndNavigate = useCallback(() => {
    navigate()
  }, [navigate])

  const saveAndNavigate = useCallback(async () => {
    if (!saveHandlerRef.current) return
    setIsSaving(true)
    const hasSuccess = await saveHandlerRef.current()
    setIsSaving(false)
    if (hasSuccess) navigate()
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

'use client'

/**
 * PipelineEditorLayout Component
 *
 * Main layout component for the pipeline editor.
 * Implements a 3-panel layout with:
 * - Left panel: Node palette (drag & drop source)
 * - Center panel: React Flow canvas with tabs and toolbar
 * - Right panel: Node inspector/property editor
 *
 * IMPORTANT: Session management follows the same pattern as UML modeler:
 * - usePipelineSession() is called ONCE in the main component
 * - sessionManager is passed as PROP to both provider and inner layout
 * - This ensures single source of truth for session state
 *
 */

import { Loader2 } from 'lucide-react'
import { useParams, useRouter } from 'next/navigation'
import { useTranslations } from 'next-intl'
import { useCallback, useState } from 'react'

import { ExitWarningModal } from '@/components/modals/exit-warning-modal/ExitWarningModal'
import { PageHeader } from '@/components/page-header/PageHeader'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

import { LAYOUT_DIMENSIONS } from '../../_constants/pipelineStyles'
import { useActivePipeline } from '../../_hooks/use-active-pipeline'
import { usePipelineSession } from '../../_hooks/use-pipeline-session'
import type { UsePipelineSessionReturn } from '../../_types/session'
import { PipelineCanvas } from '../canvas/PipelineCanvas'
import { PipelineInspector } from '../inspector/PipelineInspector'
import { PipelinePalette } from '../palette/PipelinePalette'
import { PipelineEditorProviderComponent } from '../providers/PipelineEditorProvider'
import { PipelineTabBar } from '../tabs/PipelineTabBar'
import { PipelineToolbar } from '../tabs/PipelineToolbar'

// ============================================================================
// Types
// ============================================================================

interface PipelineEditorLayoutProps {
  className?: string
}

interface PipelineEditorLayoutInnerProps {
  className?: string
  sessionManager: UsePipelineSessionReturn
}

// ============================================================================
// Inner Layout Component (receives sessionManager via props)
// ============================================================================

/**
 * Inner layout component that uses the shared session manager.
 * Does NOT call usePipelineSession() - receives it via props.
 */
const PipelineEditorLayoutInner: React.FC<PipelineEditorLayoutInnerProps> = ({ className = '', sessionManager }) => {
  const t = useTranslations('pipelineEditor')
  const { isLoadingPipelines, saveAllPipelines, isSavingAll, hasAnyDirtySession } = useActivePipeline()

  const router = useRouter()
  const params = useParams<{ datasetId: string }>()
  const [isExitModalOpen, setIsExitModalOpen] = useState(false)

  const handleExit = useCallback(() => {
    if (hasAnyDirtySession) {
      setIsExitModalOpen(true)
    } else {
      router.push(`/datasets/${params.datasetId}`)
    }
  }, [hasAnyDirtySession, router, params.datasetId])

  const handleDiscardAndExit = useCallback(() => {
    setIsExitModalOpen(false)
    router.push(`/datasets/${params.datasetId}`)
  }, [router, params.datasetId])

  const handleSaveAndExit = useCallback(async () => {
    try {
      await saveAllPipelines()
      setIsExitModalOpen(false)
      router.push(`/datasets/${params.datasetId}`)
    } catch {
      // Save failed — keep user on page. Mutation error handling in provider shows the error.
      setIsExitModalOpen(false)
    }
  }, [saveAllPipelines, router, params.datasetId])

  const handleSaveAll = useCallback(async () => {
    await saveAllPipelines()
  }, [saveAllPipelines])

  const customElement = (
    <div className="flex items-center gap-4 mr-3.5">
      <Button onClick={handleExit} type="button" variant="secondary">
        {t('header.exit')}
      </Button>
      <Button onClick={handleSaveAll} disabled={!hasAnyDirtySession || isSavingAll}>
        {isSavingAll ? (
          <>
            <Loader2 className="mr-1 h-4 w-4 animate-spin" />
            {t('header.savingAll')}
          </>
        ) : (
          t('header.saveAll')
        )}
      </Button>
    </div>
  )

  // ===== Tab management handlers =====
  const handleCreateSession = useCallback(() => {
    sessionManager.createSession(t('tabs.untitledPipeline'))
  }, [sessionManager, t])

  const handleSelectSession = useCallback(
    (sessionId: string) => {
      sessionManager.switchToSession(sessionId)
    },
    [sessionManager],
  )

  const handleRenameSession = useCallback(
    (sessionId: string, newName: string) => {
      sessionManager.updateSessionName(sessionId, newName)
    },
    [sessionManager],
  )

  return (
    <>
      <PageHeader title={t('title')} customElement={customElement} />
      <div className="h-[calc(100vh-12rem)] w-full overflow-hidden rounded-xl border bg-background">
        <div className={cn('flex h-full w-full flex-col', className)}>
          {/* Tab Bar */}
          <div style={{ height: LAYOUT_DIMENSIONS.tabBarHeight }}>
            <PipelineTabBar
              sessions={sessionManager.sessions}
              activeSessionId={sessionManager.activeSessionId}
              onSelectSession={handleSelectSession}
              onRenameSession={handleRenameSession}
              onCreateSession={handleCreateSession}
            />
          </div>

          {/* Main Content Area */}
          {isLoadingPipelines ? (
            <div className="flex flex-1 items-center justify-center">
              <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
              <span className="ml-2 text-sm text-muted-foreground">{t('toolbar.loading')}</span>
            </div>
          ) : (
            <div className="flex flex-1 overflow-hidden">
              {/* Left Panel - Palette */}
              <PipelinePalette />

              {/* Center Panel - Canvas + Toolbar */}
              <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
                {/* Toolbar */}
                <PipelineToolbar />

                {/* Canvas */}
                <div className="relative flex-1 overflow-hidden">
                  <PipelineCanvas />
                </div>
              </div>

              {/* Right Panel - Inspector */}
              <PipelineInspector />
            </div>
          )}
        </div>
      </div>

      <ExitWarningModal
        open={isExitModalOpen}
        onOpenChange={setIsExitModalOpen}
        onDiscard={handleDiscardAndExit}
        onConfirm={handleSaveAndExit}
        isLoading={isSavingAll}
      />
    </>
  )
}

// ============================================================================
// Main Component
// ============================================================================

/**
 * Pipeline editor layout with provider wrapper.
 * This is the main export used by the page.
 *
 * PATTERN: Following UML modeler's MultiSessionLayout:
 * 1. Call usePipelineSession() ONCE here
 * 2. Pass sessionManager to PipelineEditorProviderComponent (for canvas/inspector)
 * 3. Pass sessionManager to PipelineEditorLayoutInner (for tabs)
 *
 */
export const PipelineEditorLayout: React.FC<PipelineEditorLayoutProps> = ({ className = '' }) => {
  // Single source of truth for session state
  const sessionManager = usePipelineSession()

  return (
    <PipelineEditorProviderComponent sessionManager={sessionManager}>
      <PipelineEditorLayoutInner className={className} sessionManager={sessionManager} />
    </PipelineEditorProviderComponent>
  )
}

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

import { useCallback } from 'react'

import { cn } from '@/lib/utils'

import { LAYOUT_DIMENSIONS } from '../../_constants/pipelineStyles'
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
  // ===== Tab management handlers =====
  const handleCreateSession = useCallback(() => {
    sessionManager.createSession('Untitled Pipeline')
  }, [sessionManager])

  const handleCloseSession = useCallback(
    (sessionId: string) => {
      // Note: Dirty state warning deferred to Phase 5
      sessionManager.closeSession(sessionId)
    },
    [sessionManager],
  )

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
    <div className={cn('flex h-full w-full flex-col', className)}>
      {/* Tab Bar */}
      <div style={{ height: LAYOUT_DIMENSIONS.tabBarHeight }}>
        <PipelineTabBar
          sessions={sessionManager.sessions}
          activeSessionId={sessionManager.activeSessionId}
          onSelectSession={handleSelectSession}
          onCloseSession={handleCloseSession}
          onRenameSession={handleRenameSession}
          onCreateSession={handleCreateSession}
        />
      </div>

      {/* Main Content Area */}
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
    </div>
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

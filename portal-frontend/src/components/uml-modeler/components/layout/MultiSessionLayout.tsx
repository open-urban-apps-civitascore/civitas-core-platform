'use client'

import { useCallback } from 'react'

import { useMultiSessionManager } from '../../hooks/use-multi-session-manager'
import { PropertyInspector } from '../inspector/PropertyInspector'
import { ElementPalette } from '../palette/ElementPalette'
import { ActiveDiagramProviderComponent } from '../providers/ActiveDiagramProvider'
import { TabBar } from '../tabs/TabBar'
import { TabContent } from '../tabs/TabContent'
import { Toolbar } from '../tabs/Toolbar'

interface MultiSessionLayoutProps {
  className?: string
  isReadOnly: boolean
}

export const MultiSessionLayout: React.FC<MultiSessionLayoutProps> = props => {
  const { isReadOnly, className } = props
  const sessionManager = useMultiSessionManager()

  // Tab management handlers
  const handleCreateSession = useCallback(() => {
    sessionManager.createSession('Untitled Diagram')
  }, [sessionManager])

  const handleCloseSession = useCallback(
    (sessionId: string) => {
      const session = sessionManager.sessions.find(s => s.id === sessionId)

      if (session?.isDirty) {
        // TODO: Show confirmation dialog for unsaved changes
        const shouldClose = window.confirm(`"${session.name}" has unsaved changes. Close anyway?`)
        if (!shouldClose) return
      }

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

  // Toolbar handlers
  const handleSave = useCallback(() => {
    const activeSession = sessionManager.getActiveSession()
    if (activeSession) {
      sessionManager.markSessionClean(activeSession.id)
      // TODO: Implement actual save functionality
      console.log('Save diagram:', activeSession.name)
    }
  }, [sessionManager])

  const handleExport = useCallback(() => {
    const activeSession = sessionManager.getActiveSession()
    if (activeSession) {
      // TODO: Implement export functionality
      console.log('Export diagram:', activeSession.name)
    }
  }, [sessionManager])

  const activeSession = sessionManager.getActiveSession()

  return (
    <ActiveDiagramProviderComponent sessionManager={sessionManager}>
      <div className={`h-full flex bg-gray-100 ${className}`}>
        {/* Element Palette - Left Sidebar */}
        <ElementPalette className="flex-shrink-0" />

        {/* Center Area with Tabs, Toolbar, and Canvas */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* Tab Bar */}
          <TabBar
            sessions={sessionManager.sessions}
            activeSessionId={sessionManager.activeSessionId}
            onSelectSession={handleSelectSession}
            onCloseSession={handleCloseSession}
            onRenameSession={handleRenameSession}
            onCreateSession={handleCreateSession}
          />

          {/* Toolbar */}
          <Toolbar onSave={handleSave} onExport={handleExport} hasUnsavedChanges={activeSession?.isDirty || false} />

          {/* Tab Content Area */}
          <div className="flex-1 relative overflow-hidden">
            {sessionManager.sessions.map(session => (
              <TabContent
                key={session.id}
                session={session}
                isActive={session.id === sessionManager.activeSessionId}
                isReadonly={isReadOnly}
              />
            ))}
          </div>
        </div>

        {/* Property Inspector - Right Sidebar */}
        <PropertyInspector className="flex-shrink-0" />
      </div>
    </ActiveDiagramProviderComponent>
  )
}

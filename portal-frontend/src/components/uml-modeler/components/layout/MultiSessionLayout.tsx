'use client'

import { useTranslations } from 'next-intl'
import { useCallback, useMemo, useState } from 'react'

import { WarningModal } from '@/components/modals/warning-modal/WarningModal'

import { useMultiSessionManager } from '../../hooks/use-multi-session-manager'
import { useReadOnly } from '../../hooks/use-read-only'
import { UseMultiSessionReturn } from '../../types/session'
import { PropertyInspector } from '../inspector/PropertyInspector'
import { ElementPalette } from '../palette/ElementPalette'
import { ActiveDiagramProviderComponent } from '../providers/ActiveDiagramProvider'
import { TabBar } from '../tabs/TabBar'
import { TabContent } from '../tabs/TabContent'
import { Toolbar } from '../tabs/Toolbar'

interface MultiSessionLayoutProps {
  className?: string
  externalSessionManager?: UseMultiSessionReturn
  isMultiSessionMode: boolean
  canExportXmi: boolean
  canImportXmi: boolean
}

export const MultiSessionLayout: React.FC<MultiSessionLayoutProps> = props => {
  const { className, externalSessionManager, isMultiSessionMode, canExportXmi, canImportXmi } = props
  const t = useTranslations('umlModeler')
  const tCommon = useTranslations('common')
  const { isReadOnly } = useReadOnly()
  const sessionManager = useMultiSessionManager({ sessionManager: externalSessionManager })
  const isControlledExternally = !!externalSessionManager
  const [isWarningModalOpen, setIsWarningModalOpen] = useState(false)

  const activeSessionId = useMemo(() => sessionManager.activeSessionId || '', [sessionManager.activeSessionId])
  // Tab management handlers
  const handleCreateSession = useCallback(() => {
    sessionManager.createSession('Untitled Diagram')
  }, [sessionManager])

  const handleCloseSession = () => {
    setIsWarningModalOpen(true)
  }

  const handleConfirmCloseSession = () => {
    setIsWarningModalOpen(false)
    sessionManager.closeSession(activeSessionId)
  }

  const handleDiscardCloseSession = () => {
    setIsWarningModalOpen(false)
  }

  const handleSelectSession = useCallback(
    (sessionId: string) => {
      sessionManager.switchToSession(sessionId)
    },
    [sessionManager],
  )

  const handleRenameSession = useCallback(
    (sessionId: string, newName: string) => {
      sessionManager.updateSessionName(sessionId, newName)
      sessionManager.markSessionDirty(sessionId, 'modelName')
    },
    [sessionManager],
  )

  // Toolbar handlers
  const handleSave = useCallback(() => {
    const activeSession = sessionManager.getActiveSession()
    if (activeSession) {
      sessionManager.markSessionClean(activeSession.id, 'model')
      sessionManager.markSessionClean(activeSession.id, 'modelName')
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

  const shouldShowToolBar = !isReadOnly && (!isControlledExternally || canImportXmi || canExportXmi)

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
            isMultiSessionMode={isMultiSessionMode}
          />

          {/* Toolbar */}
          {shouldShowToolBar && (
            <Toolbar
              onSave={isControlledExternally ? undefined : handleSave}
              onExport={handleExport}
              hasUnsavedChanges={activeSession?.isDirty || false}
              canExportXmi={canExportXmi}
              canImportXmi={canImportXmi}
            />
          )}

          {/* Tab Content Area */}
          <div className="flex-1 relative overflow-hidden">
            {sessionManager.sessions.map(session => (
              <TabContent key={session.id} session={session} isActive={session.id === sessionManager.activeSessionId} />
            ))}
          </div>
        </div>

        {/* Property Inspector - Right Sidebar */}
        <PropertyInspector className="flex-shrink-0" />
      </div>
      <WarningModal
        title={t('closeTabModal.title')}
        description={t('closeTabModal.description')}
        confirmButtonTitle={tCommon('actions.close')}
        open={isWarningModalOpen}
        onOpenChange={() => setIsWarningModalOpen(false)}
        onDiscard={handleDiscardCloseSession}
        onConfirm={handleConfirmCloseSession}
      />
    </ActiveDiagramProviderComponent>
  )
}

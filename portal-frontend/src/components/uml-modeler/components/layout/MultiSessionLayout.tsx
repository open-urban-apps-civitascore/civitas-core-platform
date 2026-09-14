'use client'

import { ReactFlowProvider } from '@xyflow/react'
import { useTranslations } from 'next-intl'
import { JSX, useCallback, useMemo, useRef, useState } from 'react'
import { toast } from 'sonner'

import { WarningModal } from '@/components/modals/warning-modal/WarningModal'

import { useMultiSessionManager } from '../../hooks/use-multi-session-manager'
import {
  buildDiagramExport,
  buildDiagramFileName,
  DiagramImportError,
  downloadDiagramFile,
  readDiagramFile,
} from '../../services/diagramFileService'
import { DEFAULT_DIAGRAM_NAME } from '../../services/diagramService'
import { SchemaExportError } from '../../services/jsonSchemaExportService'
import { rootFailureMessage } from '../../services/rootFailureMessage'
import type { UMLDiagram } from '../../types/diagram'
import type { UseMultiSessionReturn } from '../../types/session'
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
  canExportModel: boolean
  placeHolder?: JSX.Element
  onImportFromDatastructure?: () => void
  dataStructureName?: string
  versionName?: string | null
  datastructureId?: string
  canExportDiagram?: boolean
}

const getImportErrorMessage = (error: unknown, t: (key: string) => string): string => {
  if (error instanceof DiagramImportError) {
    return t(`import.errors.${error.code}`)
  }
  return t('import.fileError')
}

export const MultiSessionLayout: React.FC<MultiSessionLayoutProps> = props => {
  const {
    className,
    externalSessionManager,
    isMultiSessionMode,
    canExportModel,
    placeHolder,
    onImportFromDatastructure,
    dataStructureName,
    versionName,
    datastructureId,
    canExportDiagram,
  } = props
  const shouldAllowExport = canExportDiagram ?? Boolean(datastructureId)
  const t = useTranslations('umlModeler')
  const tCommon = useTranslations('common')
  const sessionManager = useMultiSessionManager({ sessionManager: externalSessionManager })
  const isControlledExternally = !!externalSessionManager
  const [isWarningModalOpen, setIsWarningModalOpen] = useState(false)
  const [isOverwriteModalOpen, setIsOverwriteModalOpen] = useState(false)
  const [pendingImportDiagram, setPendingImportDiagram] = useState<UMLDiagram | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const activeSessionId = useMemo(() => sessionManager.activeSessionId || '', [sessionManager.activeSessionId])
  // Tab management handlers
  const handleCreateSession = useCallback(() => {
    sessionManager.createSession(DEFAULT_DIAGRAM_NAME)
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
      sessionManager.markSessionClean(activeSession.id)
    }
  }, [sessionManager])

  const activeSession = sessionManager.getActiveSession()

  const shouldShowToolBar = !isControlledExternally || canExportModel || !!onImportFromDatastructure

  const applyImportedDiagram = useCallback(
    (importedDiagram: UMLDiagram) => {
      const currentActiveSession = sessionManager.getActiveSession()
      if (!currentActiveSession) return

      const hasCustomName = Boolean(currentActiveSession.name) && currentActiveSession.name !== DEFAULT_DIAGRAM_NAME
      const targetName = hasCustomName ? currentActiveSession.name : importedDiagram.name || currentActiveSession.name
      const hasNameChanged = targetName !== currentActiveSession.name

      const dirtyFields = new Set<string>(['model'])
      if (hasNameChanged) {
        dirtyFields.add('modelName')
      }

      sessionManager.setSession(currentActiveSession.id, {
        ...currentActiveSession,
        name: targetName,
        diagram: importedDiagram,
        isDirty: true,
        dirtyFields,
        lastModified: new Date(),
      })
      toast.success(t('import.success'))
    },
    [sessionManager, t],
  )

  const handleImportClick = useCallback(() => {
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
      fileInputRef.current.click()
    }
  }, [])

  const handleFileChange = useCallback(
    async (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return

      try {
        const importedDiagram = await readDiagramFile(file)
        const currentActiveSession = sessionManager.getActiveSession()
        const hasExistingNodes = (currentActiveSession?.diagram.nodes.length || 0) > 0

        if (hasExistingNodes) {
          setPendingImportDiagram(importedDiagram)
          setIsOverwriteModalOpen(true)
        } else {
          applyImportedDiagram(importedDiagram)
        }
      } catch (error) {
        if (!(error instanceof DiagramImportError)) {
          console.error('Diagram import failed', error)
        }
        toast.error(getImportErrorMessage(error, t))
      }
    },
    [sessionManager, applyImportedDiagram, t],
  )

  const handleExportClick = useCallback(() => {
    const currentActiveSession = sessionManager.getActiveSession()
    if (!currentActiveSession) return

    try {
      const fileName = buildDiagramFileName(dataStructureName || currentActiveSession.name, versionName)
      const exportDoc = buildDiagramExport(currentActiveSession.diagram, {
        dataStructureName,
        datastructureId,
        versionName,
      })
      downloadDiagramFile(exportDoc, fileName)
      toast.success(t('export.success'))
    } catch (error) {
      if (error instanceof SchemaExportError) {
        toast.error(rootFailureMessage(t, error.failure))
        return
      }
      console.error('Diagram export failed', error)
      toast.error(t('export.error'))
    }
  }, [sessionManager, dataStructureName, versionName, datastructureId, t])

  return (
    <ActiveDiagramProviderComponent sessionManager={sessionManager}>
      <ReactFlowProvider>
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
              onImportClick={handleImportClick}
              onExportClick={shouldAllowExport ? handleExportClick : undefined}
            />

            {/* Toolbar */}
            {shouldShowToolBar && (
              <Toolbar
                onSave={isControlledExternally ? undefined : handleSave}
                hasUnsavedChanges={activeSession?.isDirty || false}
                canExportModel={canExportModel}
                onImportFromDatastructure={onImportFromDatastructure}
              />
            )}

            {/* Tab Content Area */}
            <div className="flex-1 relative overflow-hidden">
              {sessionManager.sessions.map(session => (
                <TabContent
                  key={session.id}
                  session={session}
                  isActive={session.id === sessionManager.activeSessionId}
                  placeHolder={placeHolder}
                />
              ))}
            </div>
          </div>

          {/* Property Inspector - Right Sidebar */}
          <PropertyInspector className="flex-shrink-0" />
        </div>
      </ReactFlowProvider>
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileChange}
        accept=".json,application/json"
        className="hidden"
        aria-hidden="true"
        data-testid="diagram-file-input"
      />
      <WarningModal
        title={t('closeTabModal.title')}
        description={t('closeTabModal.description')}
        confirmButtonTitle={tCommon('actions.close')}
        open={isWarningModalOpen}
        onOpenChange={() => setIsWarningModalOpen(false)}
        onDiscard={handleDiscardCloseSession}
        onConfirm={handleConfirmCloseSession}
      />
      <WarningModal
        title={t('import.overwriteModal.title')}
        description={t('import.overwriteModal.description')}
        confirmButtonTitle={t('import.overwriteModal.confirm')}
        open={isOverwriteModalOpen}
        onOpenChange={open => {
          setIsOverwriteModalOpen(open)
          if (!open) setPendingImportDiagram(null)
        }}
        onDiscard={() => {
          setIsOverwriteModalOpen(false)
          setPendingImportDiagram(null)
        }}
        onConfirm={() => {
          if (pendingImportDiagram) {
            applyImportedDiagram(pendingImportDiagram)
            setPendingImportDiagram(null)
          }
          setIsOverwriteModalOpen(false)
        }}
      />
    </ActiveDiagramProviderComponent>
  )
}

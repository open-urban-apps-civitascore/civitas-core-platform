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
  DiagramExportError,
  DiagramImportError,
  type DiagramImportFile,
  downloadDiagramFile,
  readImportFile,
} from '../../services/diagramFileService'
import { DEFAULT_DIAGRAM_NAME } from '../../services/diagramService'
import { SchemaExportError } from '../../services/jsonSchemaExportService'
import { SchemaImportError } from '../../services/jsonSchemaImportService'
import { rootFailureMessage } from '../../services/rootFailureMessage'
import { mergeStructureIntoDiagram } from '../../services/structureMergeService'
import type { UMLDiagram } from '../../types/diagram'
import type { DirtyField, UseMultiSessionReturn } from '../../types/session'
import { PropertyInspector } from '../inspector/PropertyInspector'
import { ElementPalette } from '../palette/ElementPalette'
import { ActiveDiagramProviderComponent } from '../providers/ActiveDiagramProvider'
import { TabBar } from '../tabs/TabBar'
import { TabContent } from '../tabs/TabContent'
import { Toolbar } from '../tabs/Toolbar'
import { ImportChoiceModal } from './ImportChoiceModal'

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
  const [pendingImport, setPendingImport] = useState<DiagramImportFile | null>(null)
  const tLoad = useTranslations('umlModeler.loadStandard')
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

  // The toolbar says which published structures the diagram was built from, so a diagram with pins
  // shows it even when it owns nothing else.
  const hasImportedStructures = (activeSession?.diagram.importedStructures?.length ?? 0) > 0
  const shouldShowToolBar =
    !isControlledExternally || canExportModel || !!onImportFromDatastructure || hasImportedStructures

  const applyImportedDiagram = useCallback(
    (importedDiagram: UMLDiagram) => {
      const currentActiveSession = sessionManager.getActiveSession()
      if (!currentActiveSession) return

      const hasCustomName = Boolean(currentActiveSession.name) && currentActiveSession.name !== DEFAULT_DIAGRAM_NAME
      const targetName = hasCustomName ? currentActiveSession.name : importedDiagram.name || currentActiveSession.name
      const hasNameChanged = targetName !== currentActiveSession.name

      const dirtyFields = new Set<DirtyField>(['model'])
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

  /**
   * Adds the file's classes beside the ones the diagram has, the way a standard structure is loaded:
   * a taken name gets a suffix, and the file's root becomes a part of the diagram's root. The layout
   * of the file is not kept — its classes are placed below what is drawn.
   */
  const addImportedDocument = useCallback(
    (imported: DiagramImportFile) => {
      const currentActiveSession = sessionManager.getActiveSession()
      if (!currentActiveSession) return

      let result
      try {
        result = mergeStructureIntoDiagram(
          currentActiveSession.diagram,
          imported.document,
          imported.diagram.importedStructures ?? [],
        )
      } catch (error) {
        if (!(error instanceof SchemaImportError)) throw error
        toast.error(tLoad('unreadable', { construct: error.construct }))
        return
      }
      if (result.nodes.length === 0) {
        toast.warning(tLoad('emptyStructure'))
        return
      }

      sessionManager.setSession(currentActiveSession.id, {
        ...currentActiveSession,
        diagram: {
          ...currentActiveSession.diagram,
          nodes: [...currentActiveSession.diagram.nodes, ...result.nodes],
          edges: [...currentActiveSession.diagram.edges, ...result.edges],
          importedStructures: result.importedStructures,
          isDirty: true,
          lastModified: new Date(),
        },
        isDirty: true,
        dirtyFields: new Set<DirtyField>([...currentActiveSession.dirtyFields, 'model']),
        lastModified: new Date(),
      })
      for (const renamed of result.renamed) {
        toast.warning(tLoad('renamed', { from: renamed.from, to: renamed.to }))
      }
      toast.success(t('import.added'))
    },
    [sessionManager, t, tLoad],
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
        const imported = await readImportFile(file)
        const currentActiveSession = sessionManager.getActiveSession()
        const hasExistingNodes = (currentActiveSession?.diagram.nodes.length || 0) > 0

        if (hasExistingNodes) {
          setPendingImport(imported)
          setIsOverwriteModalOpen(true)
        } else {
          applyImportedDiagram(imported.diagram)
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
      })
      downloadDiagramFile(exportDoc, fileName)
      toast.success(t('export.success'))
    } catch (error) {
      if (error instanceof SchemaExportError) {
        toast.error(rootFailureMessage(t, error.failure))
        return
      }
      // Named separately from the generic failure: retrying cannot help, the data structure needs a
      // name the URN can carry.
      if (error instanceof DiagramExportError) {
        toast.error(t(`export.errors.${error.code}`))
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
      <ImportChoiceModal
        isOpen={isOverwriteModalOpen}
        onCancel={() => {
          setIsOverwriteModalOpen(false)
          setPendingImport(null)
        }}
        onAdd={() => {
          if (pendingImport) addImportedDocument(pendingImport)
          setPendingImport(null)
          setIsOverwriteModalOpen(false)
        }}
        onReplace={() => {
          if (pendingImport) applyImportedDiagram(pendingImport.diagram)
          setPendingImport(null)
          setIsOverwriteModalOpen(false)
        }}
      />
    </ActiveDiagramProviderComponent>
  )
}

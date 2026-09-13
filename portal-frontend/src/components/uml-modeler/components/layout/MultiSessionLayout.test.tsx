import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { toast } from 'sonner'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ReadOnlyProvider } from '../../hooks/use-read-only'
import * as diagramFileService from '../../services/diagramFileService'
import { DiagramImportError } from '../../services/diagramFileService'
import { SchemaExportError } from '../../services/jsonSchemaExportService'
import type { UMLDiagram, UMLNode } from '../../types/diagram'
import type { DiagramSession } from '../../types/session'
import { MultiSessionLayout } from './MultiSessionLayout'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('../canvas/UMLCanvas', () => ({
  UMLCanvas: () => <div data-testid="uml-canvas" />,
}))

vi.mock('../inspector/PropertyInspector', () => ({
  PropertyInspector: () => <div data-testid="property-inspector" />,
}))

vi.mock('../palette/ElementPalette', () => ({
  ElementPalette: () => <div data-testid="element-palette" />,
}))

const createSampleNode = (id: string, name: string): UMLNode => ({
  id,
  type: 'class',
  position: { x: 100, y: 100 },
  data: {
    element: {
      id,
      name,
      type: 'class',
      attributes: [],
      operations: [],
    },
    label: name,
  },
})

const createMockDiagram = (nodes: UMLNode[] = [], name = 'TestDiagram'): UMLDiagram => ({
  id: 'diagram-1',
  name,
  nodes,
  edges: [],
  lastModified: new Date(),
  isDirty: false,
})

const createMockSession = (diagram: UMLDiagram): DiagramSession => ({
  id: 'session-1',
  name: diagram.name,
  diagram,
  isDirty: false,
  dirtyFields: new Set(),
  lastModified: new Date(),
  created: new Date(),
})

describe('MultiSessionLayout Import & Export', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const renderLayout = (diagram = createMockDiagram(), isReadOnly = false) => {
    const session = createMockSession(diagram)
    let currentSession = session
    const mockSessionManager = {
      sessions: [session],
      activeSessionId: session.id,
      activeSession: session,
      createSession: vi.fn(),
      closeSession: vi.fn(),
      setSession: vi.fn((_id, updated) => {
        currentSession = updated
        mockSessionManager.activeSession = updated
        mockSessionManager.sessions = [updated]
      }),
      switchToSession: vi.fn(),
      updateSessionName: vi.fn(),
      updateSessionDiagram: vi.fn(),
      markSessionDirty: vi.fn(),
      markSessionClean: vi.fn(),
      markFieldClean: vi.fn(),
      getActiveSession: vi.fn(() => currentSession),
      getAllSessions: vi.fn(() => [currentSession]),
    }

    const utils = render(
      <ReadOnlyProvider isReadOnly={isReadOnly}>
        <MultiSessionLayout
          externalSessionManager={mockSessionManager as any}
          isMultiSessionMode={false}
          canExportModel={false}
          dataStructureName="School"
          versionName="v1"
          datastructureId="12345"
        />
      </ReadOnlyProvider>,
    )

    return { ...utils, mockSessionManager }
  }

  it('triggers file picker click when Import button is clicked', () => {
    renderLayout()

    const fileInput = screen.getByTestId('diagram-file-input') as HTMLInputElement
    const clickSpy = vi.spyOn(fileInput, 'click')

    fireEvent.click(screen.getByText('import.title'))
    expect(clickSpy).toHaveBeenCalledTimes(1)
  })

  it('shows error toast when importing a file that fails validation', async () => {
    vi.spyOn(diagramFileService, 'readDiagramFile').mockRejectedValueOnce(
      new DiagramImportError('MISSING_UI_STYLES', 'No styles'),
    )

    renderLayout()

    const fileInput = screen.getByTestId('diagram-file-input')
    const file = new File(['{}'], 'bad.json', { type: 'application/json' })

    fireEvent.change(fileInput, { target: { files: [file] } })

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith('import.errors.MISSING_UI_STYLES')
    })
  })

  it('imports directly without modal when the canvas has no existing nodes', async () => {
    const imported = createMockDiagram([createSampleNode('node-1', 'ImportedClass')], 'ImportedModel')
    vi.spyOn(diagramFileService, 'readDiagramFile').mockResolvedValueOnce(imported)

    const { mockSessionManager } = renderLayout(createMockDiagram([]))

    const fileInput = screen.getByTestId('diagram-file-input')
    const file = new File(['{}'], 'valid.json', { type: 'application/json' })

    fireEvent.change(fileInput, { target: { files: [file] } })

    await waitFor(() => {
      expect(mockSessionManager.setSession).toHaveBeenCalledWith(
        'session-1',
        expect.objectContaining({
          diagram: imported,
          name: 'ImportedModel',
          isDirty: true,
        }),
      )
      expect(toast.success).toHaveBeenCalledWith('import.success')
    })
    expect(screen.queryByText('import.overwriteModal.title')).not.toBeInTheDocument()
  })

  it('opens overwrite confirmation modal when canvas has existing nodes', async () => {
    const existingNode = createSampleNode('node-existing', 'ExistingClass')
    const imported = createMockDiagram([createSampleNode('node-imported', 'ImportedClass')], 'ImportedModel')
    vi.spyOn(diagramFileService, 'readDiagramFile').mockResolvedValueOnce(imported)

    const { mockSessionManager } = renderLayout(createMockDiagram([existingNode]))

    const fileInput = screen.getByTestId('diagram-file-input')
    const file = new File(['{}'], 'valid.json', { type: 'application/json' })

    fireEvent.change(fileInput, { target: { files: [file] } })

    await waitFor(() => {
      expect(screen.getByText('import.overwriteModal.title')).toBeInTheDocument()
    })
    expect(mockSessionManager.setSession).not.toHaveBeenCalled()

    // Clicking cancel leaves the canvas untouched
    fireEvent.click(screen.getByTestId('discardButton'))
    expect(mockSessionManager.setSession).not.toHaveBeenCalled()
    expect(screen.queryByText('import.overwriteModal.title')).not.toBeInTheDocument()
  })

  it('replaces diagram and sets session dirty when confirming overwrite modal', async () => {
    const existingNode = createSampleNode('node-existing', 'ExistingClass')
    const imported = createMockDiagram([createSampleNode('node-imported', 'ImportedClass')], 'ImportedModel')
    vi.spyOn(diagramFileService, 'readDiagramFile').mockResolvedValueOnce(imported)

    const { mockSessionManager } = renderLayout(createMockDiagram([existingNode]))

    const fileInput = screen.getByTestId('diagram-file-input')
    const file = new File(['{}'], 'valid.json', { type: 'application/json' })

    fireEvent.change(fileInput, { target: { files: [file] } })

    await waitFor(() => {
      expect(screen.getByText('import.overwriteModal.title')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByTestId('confirmButton'))

    expect(mockSessionManager.setSession).toHaveBeenCalledWith(
      'session-1',
      expect.objectContaining({
        diagram: imported,
        name: 'ImportedModel',
        isDirty: true,
      }),
    )
    expect(toast.success).toHaveBeenCalledWith('import.success')
    expect(screen.queryByText('import.overwriteModal.title')).not.toBeInTheDocument()
  })

  it('exports active diagram with correct filename and download on Export click', () => {
    const buildExportSpy = vi.spyOn(diagramFileService, 'buildDiagramExport').mockReturnValueOnce({ test: true })
    const downloadSpy = vi.spyOn(diagramFileService, 'downloadDiagramFile').mockImplementation(() => {})

    renderLayout()

    fireEvent.click(screen.getByText('export.title'))

    expect(buildExportSpy).toHaveBeenCalledTimes(1)
    expect(downloadSpy).toHaveBeenCalledWith({ test: true }, 'School-v1.json')
    expect(toast.success).toHaveBeenCalledWith('export.success')
  })

  it('shows error toast when export fails with SchemaExportError', () => {
    vi.spyOn(diagramFileService, 'buildDiagramExport').mockImplementationOnce(() => {
      throw new SchemaExportError({ code: 'noRoot' })
    })

    renderLayout()

    fireEvent.click(screen.getByText('export.title'))

    expect(toast.error).toHaveBeenCalledWith('rootValidation.noRoot')
  })
})

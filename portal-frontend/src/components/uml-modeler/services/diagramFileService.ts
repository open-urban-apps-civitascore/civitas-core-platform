import { DataStructureSchema } from '@/generated/core'
import { buildDataStructureLogicalUrn } from '@/utils/urn'

import type { UMLDiagram, UMLEdge, UMLNode } from '../types/diagram'
import type { UMLElement } from '../types/uml'
import { UMLDiagramSchema } from './diagramSchema'
import { buildUMLModelPayload } from './modelUploadService'

export const MAX_IMPORT_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB

export type DiagramImportErrorCode =
  | 'FILE_TOO_LARGE'
  | 'INVALID_JSON'
  | 'INVALID_DATASTRUCTURE_DOCUMENT'
  | 'MISSING_UI_STYLES'
  | 'INVALID_DIAGRAM_SCHEMA'

export class DiagramImportError extends Error {
  readonly code: DiagramImportErrorCode
  readonly details?: unknown

  constructor(code: DiagramImportErrorCode, message: string, details?: unknown) {
    super(message)
    this.name = 'DiagramImportError'
    this.code = code
    this.details = details
  }
}

export interface DiagramExportOptions {
  dataStructureName?: string
  datastructureId?: string
}

/**
 * Builds the default export filename according to the convention:
 * <data structure name>-<version name>.json
 *
 * Non-alphanumeric characters are sanitized to underscores.
 * When version is absent, 'draft' is used as fallback.
 * When name is absent, 'datastructure' is used as fallback.
 */
export const buildDiagramFileName = (dataStructureName?: string, versionName?: string | null): string => {
  const cleanName = dataStructureName?.trim().replace(/[^a-zA-Z0-9]/g, '_') || 'datastructure'
  const cleanVersion = versionName?.trim() ? versionName.trim().replace(/[^a-zA-Z0-9]/g, '_') : 'draft'
  return `${cleanName}-${cleanVersion}.json`
}

/**
 * Strips transient React Flow and runtime editor fields from the diagram:
 * - Diagram: lastModified, isDirty
 * - Nodes: measured, selected, dragging, style, dragHandle, data.isSelected, data.isDirty
 * - Edges: selected, data.isSelected, data.isDirty
 */
export const cleanDiagramForExport = (diagram: UMLDiagram): Record<string, unknown> => {
  const cleanedNodes = diagram.nodes.map(node => {
    const rawData = (node.data || {}) as Record<string, unknown>
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { isSelected: _nodeSelected, isDirty: _nodeDirty, ...cleanData } = rawData

    return {
      id: node.id,
      type: node.type,
      position: {
        x: node.position.x,
        y: node.position.y,
      },
      data: cleanData,
    }
  })

  const cleanedEdges = diagram.edges.map(edge => {
    const rawData = (edge.data || {}) as Record<string, unknown>
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const { isSelected: _edgeSelected, isDirty: _edgeDirty, ...cleanData } = rawData

    return {
      id: edge.id,
      type: edge.type,
      source: edge.source,
      target: edge.target,
      data: cleanData,
    }
  })

  const result: Record<string, unknown> = {
    id: diagram.id,
    name: diagram.name,
    nodes: cleanedNodes,
    edges: cleanedEdges,
  }

  if (diagram.description) {
    result.description = diagram.description
  }

  if (diagram.viewport) {
    result.viewport = {
      x: diagram.viewport.x,
      y: diagram.viewport.y,
      zoom: diagram.viewport.zoom,
    }
  }

  return result
}

/**
 * Builds the complete serialized CORE DataStructure document with the diagram embedded
 * under 'x-ui-styles', matching the format persisted by Model Forge.
 *
 * @throws SchemaExportError if root resolution fails (e.g. cycles or ambiguous roots).
 */
export const buildDiagramExport = (
  diagram: UMLDiagram,
  options?: DiagramExportOptions,
): Record<string, unknown> => {
  const dataStructureName = options?.dataStructureName?.trim() || diagram.name?.trim() || 'DataStructure'
  const datastructureId = options?.datastructureId || crypto.randomUUID()

  let modelUri: string | undefined
  try {
    modelUri = buildDataStructureLogicalUrn(dataStructureName, datastructureId)
  } catch {
    modelUri = undefined
  }

  const { model } = buildUMLModelPayload(diagram, modelUri)
  const cleanedDiagram = cleanDiagramForExport(diagram)

  return {
    ...model,
    'x-ui-styles': cleanedDiagram,
  }
}

/**
 * Triggers a file download dialog in the browser using a Blob and temporary anchor element.
 */
export const downloadDiagramFile = (document: Record<string, unknown>, filename: string): void => {
  const content = JSON.stringify(document, null, 2)
  const blob = new Blob([content], { type: 'application/json' })
  const url = URL.createObjectURL(blob)

  const link = window.document.createElement('a')
  link.href = url
  link.download = filename
  window.document.body.appendChild(link)
  link.click()
  window.document.body.removeChild(link)

  URL.revokeObjectURL(url)
}

/**
 * Reads, parses, and validates an uploaded CORE DataStructure file.
 *
 * Performs a two-stage validation:
 * 1. Stage 1: Validates the root CORE JSON document envelope via DataStructureSchema.
 * 2. Stage 2: Validates the embedded UML diagram in 'x-ui-styles' via UMLDiagramSchema.
 *
 * Generates a fresh diagram ID while preserving all nodes, coordinates, and relationships.
 *
 * @throws DiagramImportError with specific error codes for each failure category.
 */
export const readDiagramFile = async (file: File): Promise<UMLDiagram> => {
  if (file.size > MAX_IMPORT_FILE_SIZE_BYTES) {
    throw new DiagramImportError(
      'FILE_TOO_LARGE',
      `File size (${(file.size / (1024 * 1024)).toFixed(2)} MB) exceeds maximum allowed size of ${MAX_IMPORT_FILE_SIZE_BYTES / (1024 * 1024)} MB.`,
    )
  }

  let text: string
  try {
    if (typeof file.text === 'function') {
      text = await file.text()
    } else {
      text = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(reader.result as string)
        reader.onerror = () => reject(reader.error)
        reader.readAsText(file)
      })
    }
  } catch (error) {
    throw new DiagramImportError('INVALID_JSON', 'Failed to read file contents.', error)
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch (error) {
    throw new DiagramImportError('INVALID_JSON', 'File is not a valid JSON document.', error)
  }

  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new DiagramImportError(
      'INVALID_DATASTRUCTURE_DOCUMENT',
      'Document root must be a JSON object.',
    )
  }

  // Stage 1: Validate CORE DataStructure schema
  const coreValidation = DataStructureSchema.safeParse(parsed)
  if (!coreValidation.success) {
    throw new DiagramImportError(
      'INVALID_DATASTRUCTURE_DOCUMENT',
      'Document does not conform to the CORE DataStructure schema.',
      coreValidation.error.issues,
    )
  }

  const doc = parsed as Record<string, unknown>
  const uiStyles = doc['x-ui-styles'] ?? doc['styles']

  if (!uiStyles || typeof uiStyles !== 'object' || Array.isArray(uiStyles)) {
    throw new DiagramImportError(
      'MISSING_UI_STYLES',
      "Document is missing the 'x-ui-styles' extension property containing diagram layout data.",
    )
  }

  // Stage 2: Validate diagram structure in x-ui-styles
  const diagramValidation = UMLDiagramSchema.safeParse(uiStyles)
  if (!diagramValidation.success) {
    throw new DiagramImportError(
      'INVALID_DIAGRAM_SCHEMA',
      "The diagram data in 'x-ui-styles' failed validation.",
      diagramValidation.error.issues,
    )
  }

  const validDiagram = diagramValidation.data

  const docTitle = typeof doc.title === 'string' && doc.title.trim().length > 0 ? doc.title.trim() : undefined
  const fileTitle = file.name ? file.name.replace(/\.[^/.]+$/, '').trim() : undefined
  const diagramName = validDiagram.name?.trim().length > 0
    ? validDiagram.name.trim()
    : docTitle || fileTitle || 'Imported Diagram'

  const normalizedNodes: UMLNode[] = validDiagram.nodes.map(node => ({
    id: node.id,
    type: node.type,
    position: {
      x: node.position.x,
      y: node.position.y,
    },
    data: {
      ...node.data,
      element: node.data.element as UMLElement,
      label: node.data.label || node.data.element.name,
    },
  }))

  const normalizedEdges: UMLEdge[] = validDiagram.edges.map(edge => ({
    id: edge.id,
    type: edge.type,
    source: edge.source,
    target: edge.target,
    data: {
      ...edge.data,
      relationship: edge.data?.relationship || {
        id: edge.id,
        type: edge.type,
        source: edge.source,
        target: edge.target,
      },
    },
  }))

  const importedDiagram: UMLDiagram = {
    id: crypto.randomUUID(),
    name: diagramName,
    nodes: normalizedNodes,
    edges: normalizedEdges,
    lastModified: new Date(),
    isDirty: true,
  }

  if (validDiagram.description || (typeof doc.description === 'string' && doc.description.trim())) {
    importedDiagram.description = validDiagram.description || (doc.description as string).trim()
  }

  if (validDiagram.viewport) {
    importedDiagram.viewport = {
      x: validDiagram.viewport.x,
      y: validDiagram.viewport.y,
      zoom: validDiagram.viewport.zoom,
    }
  }

  return importedDiagram
}

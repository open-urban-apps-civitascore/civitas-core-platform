import { buildDataStructureLogicalUrn } from '@/utils/urn'

import type { UMLDiagram } from '../types/diagram'
import { buildUMLModelPayload } from './modelUploadService'

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
  const dataStructureName = options?.dataStructureName || diagram.name
  const datastructureId = options?.datastructureId

  let modelUri: string | undefined
  if (dataStructureName && datastructureId) {
    try {
      modelUri = buildDataStructureLogicalUrn(dataStructureName, datastructureId)
    } catch {
      modelUri = undefined
    }
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

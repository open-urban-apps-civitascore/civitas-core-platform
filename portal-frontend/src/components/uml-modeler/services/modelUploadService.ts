/**
 * Model Upload Service
 *
 * Builds the payload for saving UML models to the backend.
 *
 * Separates concerns:
 * - styles: React Flow config (viewport + node positions) for frontend reload
 * - model: JSON Schema document (without layout info)
 */

import type { UMLDiagram } from '../types/diagram'
import { exportToJsonSchema } from './jsonSchemaExportService'

export interface UMLModelStylesPayload {
  viewport?: { x: number; y: number; zoom: number }
  nodePositions: Record<string, { x: number; y: number }>
}

export interface UMLModelPayload {
  name: string
  description: string
  styles: UMLModelStylesPayload
  model: Record<string, unknown>
}

/**
 * Builds the complete UMLModelPayload for backend API submission.
 *
 * @param diagram - The UML diagram with nodes, edges, and viewport
 * @param modelUri - Optional URI used as the JSON Schema `$id`
 * @returns The payload ready to be sent to the backend
 */
export const buildUMLModelPayload = (diagram: UMLDiagram, modelUri?: string): UMLModelPayload => {
  // 1. Extract styles (viewport + node positions for reload)
  const styles: UMLModelStylesPayload = {
    viewport: diagram.viewport,
    nodePositions: Object.fromEntries(diagram.nodes.map(node => [node.id, node.position])),
  }

  // 2. Build the JSON Schema model (without layout info - styles are stored separately)
  const model = exportToJsonSchema(diagram, modelUri)

  // 3. Assemble payload
  return {
    name: diagram.name,
    description: '-',
    styles,
    model,
  }
}

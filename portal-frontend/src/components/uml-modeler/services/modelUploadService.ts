/**
 * Model Upload Service
 *
 * Builds the payload for saving UML models to the backend.
 * Follows the same pattern as the pipeline editor's payloadBuilderService.
 *
 * Separates concerns:
 * - styles: React Flow config (viewport + node positions) for frontend reload
 * - model: Pure XMI without layout info
 */

import type { UMLDiagram } from '../types/diagram'
import { exportToXmi } from './xmiExportService'

export interface UMLModelStylesPayload {
  viewport?: { x: number; y: number; zoom: number }
  nodePositions: Record<string, { x: number; y: number }>
}

export interface UMLModelPayload {
  name: string
  description: string
  styles: UMLModelStylesPayload
  model: string
}

/**
 * Builds the complete UMLModelPayload for backend API submission.
 *
 * @param diagram - The UML diagram with nodes, edges, and viewport
 * @returns The payload ready to be sent to `POST /models`
 */
export const buildUMLModelPayload = (diagram: UMLDiagram, modelUri?: string): UMLModelPayload => {
  // 1. Extract styles (viewport + node positions for reload)
  const styles: UMLModelStylesPayload = {
    viewport: diagram.viewport,
    nodePositions: Object.fromEntries(diagram.nodes.map(node => [node.id, node.position])),
  }

  // 2. Build XMI model (without layout info - styles are stored separately)
  const model = exportToXmi(diagram, modelUri)

  // 3. Assemble payload
  return {
    name: diagram.name,
    description: '-',
    styles,
    model,
  }
}

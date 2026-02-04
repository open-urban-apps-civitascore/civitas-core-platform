/**
 * Validation Service
 *
 * Provides pipeline validation functionality.
 * Validates structure, node configuration, and business rules.
 *
 */

import { type Pipeline, PIPELINE_NODE_TYPES } from '../_types/pipeline'

// ============================================================================
// Validation Types
// ============================================================================

/**
 * Pipeline validation result.
 *
 */
export interface PipelineValidationResult {
  isValid: boolean
  errors: PipelineValidationError[]
  warnings: PipelineValidationWarning[]
}

export interface PipelineValidationError {
  id: string
  type: 'node' | 'edge' | 'structure'
  elementId?: string
  message: string
  severity: 'error'
}

export interface PipelineValidationWarning {
  id: string
  type: 'node' | 'edge' | 'structure'
  elementId?: string
  message: string
  severity: 'warning'
}

// ============================================================================
// Validation Rule Types
// ============================================================================

/**
 * Validation rule definition.
 * Each rule returns errors and/or warnings.
 *
 */
interface ValidationRule {
  id: string
  name: string
  description: string
  validate: (pipeline: Pipeline) => {
    errors: PipelineValidationError[]
    warnings: PipelineValidationWarning[]
  }
}

/**
 * Node validation status for visual indicators.
 * Maps node ID to its validation issues.
 *
 */
export interface NodeValidationStatus {
  nodeId: string
  hasError: boolean
  hasWarning: boolean
  messages: string[]
}

/**
 * Validation result with node statuses for UI rendering.
 *
 */
export interface ValidationResultWithNodeStatus extends PipelineValidationResult {
  nodeStatuses: Map<string, NodeValidationStatus>
}

// ============================================================================
// Validation Rules
// ============================================================================

/**
 * Rule: Pipeline must have at least one Start node.
 */
const validateStartNode: ValidationRule = {
  id: 'start-node-required',
  name: 'Start Node Required',
  description: 'Pipeline must have at least one Start node',
  validate: (pipeline: Pipeline) => {
    const hasStartNode = pipeline.nodes.some(node => node.type === 'start')

    if (!hasStartNode) {
      return {
        errors: [
          {
            id: crypto.randomUUID(),
            type: 'structure',
            message: 'Pipeline must have at least one Start node',
            severity: 'error',
          },
        ],
        warnings: [],
      }
    }

    return { errors: [], warnings: [] }
  },
}

/**
 * Rule: Pipeline must have at least one End node.
 */
const validateEndNode: ValidationRule = {
  id: 'end-node-required',
  name: 'End Node Required',
  description: 'Pipeline must have at least one End node',
  validate: (pipeline: Pipeline) => {
    const hasEndNode = pipeline.nodes.some(node => node.type === 'end')

    if (!hasEndNode) {
      return {
        errors: [
          {
            id: crypto.randomUUID(),
            type: 'structure',
            message: 'Pipeline must have at least one End node',
            severity: 'error',
          },
        ],
        warnings: [],
      }
    }

    return { errors: [], warnings: [] }
  },
}

/**
 * Rule: When API Request node is used, API Response node must also exist.
 */
const validateApiPairing: ValidationRule = {
  id: 'api-pairing',
  name: 'API Request/Response Pairing',
  description: 'When API Request is used, API Response must also exist',
  validate: (pipeline: Pipeline) => {
    const apiRequestNodes = pipeline.nodes.filter(node => node.type === PIPELINE_NODE_TYPES.ApiRequest)
    const apiResponseNodes = pipeline.nodes.filter(node => node.type === PIPELINE_NODE_TYPES.ApiResponse)

    const errors: PipelineValidationError[] = []

    if (apiRequestNodes.length > 0 && apiResponseNodes.length === 0) {
      // Find the API Request nodes to attach errors to
      apiRequestNodes.forEach(node => {
        errors.push({
          id: crypto.randomUUID(),
          type: 'node',
          elementId: node.id,
          message: 'API Request node requires an API Response node in the pipeline',
          severity: 'error',
        })
      })
    }

    return { errors, warnings: [] }
  },
}

/**
 * Rule: All entity-referencing nodes must be configured.
 */
const validateNodeConfiguration: ValidationRule = {
  id: 'node-configuration',
  name: 'Node Configuration',
  description: 'All entity-referencing nodes must be configured',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []

    const entityNodeTypes = ['dataSource', 'apiRequest', 'apiResponse', 'frost', 'cron', 'mapping']

    pipeline.nodes.forEach(node => {
      if (entityNodeTypes.includes(node.type) && !node.data.configured) {
        errors.push({
          id: crypto.randomUUID(),
          type: 'node',
          elementId: node.id,
          message: `${node.data.label || node.type} is not configured`,
          severity: 'error',
        })
      }
    })

    return { errors, warnings: [] }
  },
}

/**
 * Rule: All nodes must be connected to the pipeline flow.
 */
const validateOrphanNodes: ValidationRule = {
  id: 'orphan-nodes',
  name: 'Orphan Nodes',
  description: 'All nodes must be connected to the pipeline flow',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []

    // Get all node IDs that have at least one connection
    const connectedNodeIds = new Set<string>()
    pipeline.edges.forEach(edge => {
      connectedNodeIds.add(edge.source)
      connectedNodeIds.add(edge.target)
    })

    // Find nodes without any connections (except if there's only one node)
    if (pipeline.nodes.length > 1) {
      pipeline.nodes.forEach(node => {
        if (!connectedNodeIds.has(node.id)) {
          errors.push({
            id: crypto.randomUUID(),
            type: 'node',
            elementId: node.id,
            message: `${node.data.label || node.type} is not connected to any other node`,
            severity: 'error',
          })
        }
      })
    }

    return { errors, warnings: [] }
  },
}

// ============================================================================
// All Validation Rules
// ============================================================================

/**
 * All validation rules to run.
 *
 */
export const VALIDATION_RULES: ValidationRule[] = [
  validateStartNode,
  validateEndNode,
  validateApiPairing,
  validateNodeConfiguration,
  validateOrphanNodes,
]

// ============================================================================
// Validation Functions
// ============================================================================

/**
 * Validates a pipeline against all rules.
 * Returns validation result with errors and warnings.
 *
 */
export const validatePipeline = (pipeline: Pipeline): PipelineValidationResult => {
  const allErrors: PipelineValidationError[] = []
  const allWarnings: PipelineValidationWarning[] = []

  VALIDATION_RULES.forEach(rule => {
    const { errors, warnings } = rule.validate(pipeline)
    allErrors.push(...errors)
    allWarnings.push(...warnings)
  })

  return {
    isValid: allErrors.length === 0,
    errors: allErrors,
    warnings: allWarnings,
  }
}

/**
 * Validates a pipeline and returns node-specific validation statuses.
 * Used for visual indicators on canvas nodes.
 *
 */
export const validatePipelineWithNodeStatus = (pipeline: Pipeline): ValidationResultWithNodeStatus => {
  const result = validatePipeline(pipeline)
  const nodeStatuses = new Map<string, NodeValidationStatus>()

  // Initialize all nodes with clean status
  pipeline.nodes.forEach(node => {
    nodeStatuses.set(node.id, {
      nodeId: node.id,
      hasError: false,
      hasWarning: false,
      messages: [],
    })
  })

  // Apply errors to node statuses
  result.errors.forEach(error => {
    if (error.elementId) {
      const status = nodeStatuses.get(error.elementId)
      if (status) {
        status.hasError = true
        status.messages.push(error.message)
      }
    }
  })

  // Apply warnings to node statuses
  result.warnings.forEach(warning => {
    if (warning.elementId) {
      const status = nodeStatuses.get(warning.elementId)
      if (status) {
        status.hasWarning = true
        status.messages.push(warning.message)
      }
    }
  })

  return {
    ...result,
    nodeStatuses,
  }
}

/**
 * Gets validation status for a specific node.
 *
 */
export const getNodeValidationStatus = (
  nodeId: string,
  validationResult: ValidationResultWithNodeStatus | null,
): NodeValidationStatus | null => {
  if (!validationResult) return null
  return validationResult.nodeStatuses.get(nodeId) || null
}

/**
 * Gets the severity level for a node (error > warning > none).
 *
 */
export const getNodeValidationSeverity = (
  nodeId: string,
  validationResult: ValidationResultWithNodeStatus | null,
): 'error' | 'warning' | 'none' => {
  const status = getNodeValidationStatus(nodeId, validationResult)
  if (!status) return 'none'
  if (status.hasError) return 'error'
  if (status.hasWarning) return 'warning'
  return 'none'
}

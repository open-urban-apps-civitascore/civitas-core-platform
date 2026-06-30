/**
 * Validation Service
 *
 * Provides pipeline validation functionality.
 * Validates structure, node configuration, and business rules.
 *
 */

import {
  isCronNodeData,
  isDataSourceNodeData,
  isGeoPersistenceNodeData,
  isMappingNodeData,
} from '../_types/nodes'
import { type Pipeline, PIPELINE_NODE_TYPES } from '../_types/pipeline'

// ============================================================================
// Quartz Cron Validation
// ============================================================================

/**
 * Validates a Quartz cron expression with 6 or 7 fields.
 * Fields: seconds minutes hours day-of-month month day-of-week [year]
 *
 * The 7th field (year) is optional, matching the adapter's backend check
 * (FlowDeploymentPlanner.isValidNifiCron also accepts 6 or 7 fields), so a valid 7-field expression
 * is accepted in both places.
 *
 * Supports: wildcards (*), ranges (-), steps (/), lists (,), and ? for day-of-month/day-of-week.
 */
export const isValidQuartzCron = (expression: string): boolean => {
  const trimmed = expression.trim()
  if (!trimmed) return false

  const fields = trimmed.split(/\s+/)
  if (fields.length !== 6 && fields.length !== 7) return false

  const [seconds, minutes, hours, dayOfMonth, month, dayOfWeek, year] = fields

  // Seconds: 0-59, supports *, */N, ranges, steps, lists
  const secondsPattern = /^(\*(\/\d+)?|(\d|[0-5]\d)([-/]\d+)?)([,](\*(\/\d+)?|(\d|[0-5]\d)([-/]\d+)?))*$/
  // Minutes: 0-59
  const minutesPattern = /^(\*(\/\d+)?|(\d|[0-5]\d)([-/]\d+)?)([,](\*(\/\d+)?|(\d|[0-5]\d)([-/]\d+)?))*$/
  // Hours: 0-23
  const hoursPattern = /^(\*(\/\d+)?|(\d|1\d|2[0-3])([-/]\d+)?)([,](\*(\/\d+)?|(\d|1\d|2[0-3])([-/]\d+)?))*$/
  // Day of month: 1-31 or ? or L or W
  const dayOfMonthPattern =
    /^(\*(\/\d+)?|\?|L|(\d|[12]\d|3[01])([-/]\d+)?[WL]?)([,](\*(\/\d+)?|(\d|[12]\d|3[01])([-/]\d+)?[WL]?))*$/
  // Month: 1-12 or JAN-DEC
  const monthPattern =
    /^(\*(\/\d+)?|(\d|1[0-2]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)([-/](\d|1[0-2]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?)([,](\*(\/\d+)?|(\d|1[0-2]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)([-/](\d|1[0-2]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?))*$/i
  // Day of week: 1-7 or SUN-SAT or ? or L
  const dayOfWeekPattern =
    /^(\*(\/\d+)?|\?|L|([1-7]|SUN|MON|TUE|WED|THU|FRI|SAT)([-/]([1-7]|SUN|MON|TUE|WED|THU|FRI|SAT))?[L#]?(\d)?)([,](\*(\/\d+)?|([1-7]|SUN|MON|TUE|WED|THU|FRI|SAT)([-/]([1-7]|SUN|MON|TUE|WED|THU|FRI|SAT))?[L#]?(\d)?))*$/i
  // Year (optional): a 4-digit year, *, ranges, steps, lists
  const yearPattern = /^(\*(\/\d+)?|\d{4}([-/]\d+)?)([,](\*(\/\d+)?|\d{4}([-/]\d+)?))*$/

  return (
    secondsPattern.test(seconds) &&
    minutesPattern.test(minutes) &&
    hoursPattern.test(hours) &&
    dayOfMonthPattern.test(dayOfMonth) &&
    monthPattern.test(month) &&
    dayOfWeekPattern.test(dayOfWeek) &&
    (year === undefined || yearPattern.test(year))
  )
}

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
  messageKey: string
  messageParams?: Record<string, string | number>
  severity: 'error'
}

export interface PipelineValidationWarning {
  id: string
  type: 'node' | 'edge' | 'structure'
  elementId?: string
  messageKey: string
  messageParams?: Record<string, string | number>
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
            messageKey: 'validation.messages.startNodeRequired',
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
            messageKey: 'validation.messages.endNodeRequired',
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
 * Rule: All entity-referencing nodes must be configured.
 */
const validateNodeConfiguration: ValidationRule = {
  id: 'node-configuration',
  name: 'Node Configuration',
  description: 'All entity-referencing nodes must be configured',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []

    const entityNodeTypes = ['dataSource', 'frost', 'cron', 'mapping', 'geoPersistence']

    pipeline.nodes.forEach(node => {
      if (entityNodeTypes.includes(node.type) && !node.data.configured) {
        const label = node.data.label || node.type
        errors.push({
          id: crypto.randomUUID(),
          type: 'node',
          elementId: node.id,
          messageKey: 'validation.messages.nodeNotConfigured',
          messageParams: { label },
          severity: 'error',
        })
      }
    })

    return { errors, warnings: [] }
  },
}

/**
 * Rule: CRON nodes must have a valid 6- or 7-field Quartz cron expression.
 */
const validateCronExpression: ValidationRule = {
  id: 'cron-expression-valid',
  name: 'Valid Cron Expression',
  description: 'CRON nodes must have a valid 6- or 7-field Quartz cron expression',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []

    pipeline.nodes.forEach(node => {
      if (node.type === PIPELINE_NODE_TYPES.Cron && isCronNodeData(node.data)) {
        const expression = node.data.cronExpression?.trim()
        if (expression && !isValidQuartzCron(expression)) {
          const label = node.data.label || 'CRON'
          errors.push({
            id: crypto.randomUUID(),
            type: 'node',
            elementId: node.id,
            messageKey: 'validation.messages.invalidCronExpression',
            messageParams: { label },
            severity: 'error',
          })
        }
      }
    })

    return { errors, warnings: [] }
  },
}

/**
 * Rule: Pipeline must have at least one functional node between Start and End.
 */
const validateMinimumFunctionalNodes: ValidationRule = {
  id: 'minimum-functional-nodes',
  name: 'Minimum Functional Nodes',
  description: 'Pipeline must have at least one functional node between Start and End',
  validate: (pipeline: Pipeline) => {
    const functionalNodes = pipeline.nodes.filter(node => node.type !== 'start' && node.type !== 'end')

    if (functionalNodes.length === 0) {
      return {
        errors: [
          {
            id: crypto.randomUUID(),
            type: 'structure',
            messageKey: 'validation.messages.minimumFunctionalNodes',
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
          const label = node.data.label || node.type
          errors.push({
            id: crypto.randomUUID(),
            type: 'node',
            elementId: node.id,
            messageKey: 'validation.messages.orphanNode',
            messageParams: { label },
            severity: 'error',
          })
        }
      })
    }

    return { errors, warnings: [] }
  },
}

const validateUniqueGeoPersistenceTableNames: ValidationRule = {
  id: 'unique-geo-persistence-table-names',
  name: 'Unique Geo Persistence Table Names',
  description: 'No two GeoPersistence nodes within a pipeline may have the same table name',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []
    const seen = new Map<string, string>()

    pipeline.nodes.forEach(node => {
      if (!isGeoPersistenceNodeData(node.data)) return
      const tableName = node.data.tableName.trim()
      if (!tableName) return

      if (seen.has(tableName)) {
        errors.push({
          id: crypto.randomUUID(),
          type: 'node',
          elementId: node.id,
          messageKey: 'validation.messages.duplicateTableName',
          severity: 'error',
        })
      } else {
        seen.set(tableName, node.id)
      }
    })

    return { errors, warnings: [] }
  },
}

/**
 * Rule: a CRON trigger schedules the source processor. An MQTT source is push-based (it self-
 * triggers on broker messages), so a cron there cannot drive the data — the pipeline engine rejects
 * this combination at deploy. Surface it here so the user sees it on "Validate" instead of as a
 * failed deployment. Cron belongs with a pull source (SQL).
 */
const validateCronRequiresNonMqttSource: ValidationRule = {
  id: 'cron-requires-non-mqtt-source',
  name: 'CRON Requires Non-MQTT Source',
  description: 'A CRON trigger cannot be combined with an MQTT (push) data source',
  validate: (pipeline: Pipeline) => {
    const hasCron = pipeline.nodes.some(node => node.type === PIPELINE_NODE_TYPES.Cron)
    if (!hasCron) return { errors: [], warnings: [] }

    const hasMqttSource = pipeline.nodes.some(
      node =>
        node.type === PIPELINE_NODE_TYPES.DataSource &&
        isDataSourceNodeData(node.data) &&
        node.data.entityMetadata?.connector === 'MQTT',
    )
    if (!hasMqttSource) return { errors: [], warnings: [] }

    return {
      errors: [
        {
          id: crypto.randomUUID(),
          type: 'structure',
          messageKey: 'validation.messages.cronMqttIncompatible',
          severity: 'error',
        },
      ],
      warnings: [],
    }
  },
}

/** A mapping value counts as assigned only if it is a non-blank string or a (non-null) op node. */
const isNonEmptyMappingValue = (value: unknown): boolean =>
  typeof value === 'string' ? value.trim() !== '' : value != null

/**
 * Rule: a mapping must assign every REQUIRED target field with a non-empty value. The required
 * paths are snapshotted on the node at mapping-save time ({@code targetRequiredFields}, written only
 * by the editor's save). A {@code configured} mapping node WITHOUT that snapshot was therefore never
 * actually saved (the editor was never opened/saved, or a source/target change invalidated it) — or
 * is a legacy node — so it is blocked with an error, not silently accepted. (A node that is not yet
 * {@code configured} is left to {@code validateNodeConfiguration}; this rule does not double-report
 * it.) Optional target fields may stay unmapped.
 */
const validateMappingCoversRequiredTargetFields: ValidationRule = {
  id: 'mapping-required-target-fields',
  name: 'Mapping Covers Required Target Fields',
  description: 'A mapping must assign every required target field',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []

    pipeline.nodes.forEach(node => {
      if (!isMappingNodeData(node.data)) return
      // a not-yet-configured node is reported by validateNodeConfiguration; avoid a double error
      if (!node.data.configured) return
      const required = node.data.targetRequiredFields

      if (required === undefined) {
        errors.push({
          id: crypto.randomUUID(),
          type: 'node',
          elementId: node.id,
          messageKey: 'validation.messages.mappingNotSaved',
          messageParams: { label: node.data.label || node.type },
          severity: 'error',
        })
        return
      }
      if (required.length === 0) return

      const assigned = new Set(
        Object.entries(node.data.mappingConfig?.fields ?? {})
          .filter(([, value]) => isNonEmptyMappingValue(value))
          .map(([key]) => key),
      )
      const missing = required.filter(path => !assigned.has(path))
      if (missing.length === 0) return

      errors.push({
        id: crypto.randomUUID(),
        type: 'node',
        elementId: node.id,
        messageKey: 'validation.messages.mappingRequiredFieldsMissing',
        messageParams: { label: node.data.label || node.type, fields: missing.join(', ') },
        severity: 'error',
      })
    })

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
  validateMinimumFunctionalNodes,
  validateNodeConfiguration,
  validateCronExpression,
  validateOrphanNodes,
  validateUniqueGeoPersistenceTableNames,
  validateCronRequiresNonMqttSource,
  validateMappingCoversRequiredTargetFields,
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
        // Note: messages array is kept empty as translations are handled in UI components
      }
    }
  })

  // Apply warnings to node statuses
  result.warnings.forEach(warning => {
    if (warning.elementId) {
      const status = nodeStatuses.get(warning.elementId)
      if (status) {
        status.hasWarning = true
        // Note: messages array is kept empty as translations are handled in UI components
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

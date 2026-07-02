/**
 * Validation Service
 *
 * Provides pipeline validation functionality.
 * Validates structure, node configuration, and business rules.
 *
 */

import { isCronNodeData, isDataSourceNodeData, isGeoPersistenceNodeData, isMappingNodeData } from '../_types/nodes'
import { type Pipeline, PIPELINE_NODE_TYPES } from '../_types/pipeline'

// ============================================================================
// NiFi Cron Validation
// ============================================================================

/**
 * Validates a NiFi 2.x cron expression: exactly 6 fields.
 * Fields: seconds minutes hours day-of-month month day-of-week
 *
 * NiFi 2.x replaced Quartz with Spring's cron parser: it dropped Quartz's optional 7th "year" field
 * and numbers day-of-week 0–7 (0 and 7 = Sunday, 1 = Monday) instead of Quartz's 1–7 (1 = Sunday).
 * This matches the adapter's field-count check (FlowDeploymentPlanner.isValidNifiCron) and keeps the
 * editor from accepting a schedule NiFi then rejects or runs on the wrong weekday.
 *
 * Supports: wildcards (*), ranges (-), steps (/), lists (,), and ? for day-of-month/day-of-week.
 */
export const isValidNifiCron = (expression: string): boolean => {
  const trimmed = expression.trim()
  if (!trimmed) return false

  const fields = trimmed.split(/\s+/)
  if (fields.length !== 6) return false

  const [seconds, minutes, hours, dayOfMonth, month, dayOfWeek] = fields

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
  // Day of week: 0-7 (Spring numbering: 0 and 7 = Sunday, 1 = Monday) or SUN-SAT or ? or L
  const dayOfWeekPattern =
    /^(\*(\/\d+)?|\?|L|([0-7]|SUN|MON|TUE|WED|THU|FRI|SAT)([-/]([0-7]|SUN|MON|TUE|WED|THU|FRI|SAT))?[L#]?(\d)?)([,](\*(\/\d+)?|([0-7]|SUN|MON|TUE|WED|THU|FRI|SAT)([-/]([0-7]|SUN|MON|TUE|WED|THU|FRI|SAT))?[L#]?(\d)?))*$/i

  return (
    secondsPattern.test(seconds) &&
    minutesPattern.test(minutes) &&
    hoursPattern.test(hours) &&
    dayOfMonthPattern.test(dayOfMonth) &&
    monthPattern.test(month) &&
    dayOfWeekPattern.test(dayOfWeek)
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
 * Rule: CRON nodes must have a valid 6-field NiFi cron expression.
 */
const validateCronExpression: ValidationRule = {
  id: 'cron-expression-valid',
  name: 'Valid Cron Expression',
  description: 'CRON nodes must have a valid 6-field NiFi cron expression',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []

    pipeline.nodes.forEach(node => {
      if (node.type === PIPELINE_NODE_TYPES.Cron && isCronNodeData(node.data)) {
        const expression = node.data.cronExpression?.trim()
        if (expression && !isValidNifiCron(expression)) {
          const label = node.data.label || 'Scheduled Trigger'
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
 * failed deployment. Cron belongs with a pull source (SQL). A source whose connector is unknown
 * (legacy node or a failed metadata fetch) cannot be verified either way, so it warns rather than
 * silently passing.
 */
const validateCronRequiresNonMqttSource: ValidationRule = {
  id: 'cron-requires-non-mqtt-source',
  name: 'CRON Requires Non-MQTT Source',
  description: 'A CRON trigger cannot be combined with an MQTT (push) data source',
  validate: (pipeline: Pipeline) => {
    const hasCron = pipeline.nodes.some(node => node.type === PIPELINE_NODE_TYPES.Cron)
    if (!hasCron) return { errors: [], warnings: [] }

    const sourceConnectors = pipeline.nodes
      .filter(node => node.type === PIPELINE_NODE_TYPES.DataSource && isDataSourceNodeData(node.data))
      .map(node => (isDataSourceNodeData(node.data) ? node.data.entityMetadata?.connector : undefined))

    if (sourceConnectors.some(connector => connector === 'MQTT')) {
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
    }

    if (sourceConnectors.some(connector => !connector)) {
      return {
        errors: [],
        warnings: [
          {
            id: crypto.randomUUID(),
            type: 'structure',
            messageKey: 'validation.messages.cronSourceConnectorUnknown',
            severity: 'warning',
          },
        ],
      }
    }

    return { errors: [], warnings: [] }
  },
}

/**
 * Rule: a SQL source with no CRON node still polls on the engine's hidden default (every 5 minutes).
 * That is easy to miss, so warn — the user can add a CRON node for an explicit schedule.
 */
const validateSqlSourceHasExplicitSchedule: ValidationRule = {
  id: 'sql-source-explicit-schedule',
  name: 'SQL Source Explicit Schedule',
  description: 'A SQL source without a CRON node polls on a hidden default schedule',
  validate: (pipeline: Pipeline) => {
    const hasSqlSource = pipeline.nodes.some(
      node =>
        node.type === PIPELINE_NODE_TYPES.DataSource &&
        isDataSourceNodeData(node.data) &&
        node.data.entityMetadata?.connector === 'SQL',
    )
    const hasCron = pipeline.nodes.some(node => node.type === PIPELINE_NODE_TYPES.Cron)
    if (!hasSqlSource || hasCron) return { errors: [], warnings: [] }

    return {
      errors: [],
      warnings: [
        {
          id: crypto.randomUUID(),
          type: 'structure',
          messageKey: 'validation.messages.sqlSourceNoScheduleDefaultPoll',
          severity: 'warning',
        },
      ],
    }
  },
}

/**
 * Rule: a FROST sink consumes the raw SensorThings envelope from the source as-is; it has no
 * record-mapping stage, so a configured mapping node would be silently ignored — the engine rejects
 * the combination at deploy. Surface it at edit time.
 */
const validateFrostSinkHasNoMapping: ValidationRule = {
  id: 'frost-sink-no-mapping',
  name: 'FROST Sink Has No Mapping',
  description: 'A FROST sink does not support a record mapping',
  validate: (pipeline: Pipeline) => {
    const hasFrostSink = pipeline.nodes.some(node => node.type === PIPELINE_NODE_TYPES.Frost)
    const hasMapping = pipeline.nodes.some(node => node.type === PIPELINE_NODE_TYPES.Mapping)
    if (!hasFrostSink || !hasMapping) return { errors: [], warnings: [] }

    return {
      errors: [
        {
          id: crypto.randomUUID(),
          type: 'structure',
          messageKey: 'validation.messages.frostSinkNoMapping',
          severity: 'error',
        },
      ],
      warnings: [],
    }
  },
}

/**
 * Rule: a FROST sink expects a SensorThings envelope, which only an MQTT SensorThings source emits;
 * a SQL source cannot feed a FROST sink and the engine rejects it at deploy. Surface it at edit
 * time.
 */
const validateSqlSourceNotToFrost: ValidationRule = {
  id: 'sql-source-not-to-frost',
  name: 'SQL Source Not To FROST',
  description: 'A SQL source cannot write to a FROST sink',
  validate: (pipeline: Pipeline) => {
    const hasFrostSink = pipeline.nodes.some(node => node.type === PIPELINE_NODE_TYPES.Frost)
    const hasSqlSource = pipeline.nodes.some(
      node =>
        node.type === PIPELINE_NODE_TYPES.DataSource &&
        isDataSourceNodeData(node.data) &&
        node.data.entityMetadata?.connector === 'SQL',
    )
    if (!hasFrostSink || !hasSqlSource) return { errors: [], warnings: [] }

    return {
      errors: [
        {
          id: crypto.randomUUID(),
          type: 'structure',
          messageKey: 'validation.messages.sqlSourceToFrost',
          severity: 'error',
        },
      ],
      warnings: [],
    }
  },
}

/**
 * Rule: a CRON trigger and a mapping node only take effect when wired into the flow (an incoming AND
 * an outgoing edge). The engine rejects a half-wired node at deploy; a detached node here would
 * otherwise be silently ignored or fail late. Mirrors the connectivity checks in the adapter's
 * PipelineGraph.
 */
const validateCronAndMappingWired: ValidationRule = {
  id: 'cron-mapping-wired',
  name: 'CRON and Mapping Nodes Wired',
  description: 'CRON and mapping nodes need both an incoming and an outgoing edge',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []
    const withIncoming = new Set(pipeline.edges.map(edge => edge.target))
    const withOutgoing = new Set(pipeline.edges.map(edge => edge.source))

    pipeline.nodes.forEach(node => {
      if (node.type !== PIPELINE_NODE_TYPES.Cron && node.type !== PIPELINE_NODE_TYPES.Mapping) return
      if (withIncoming.has(node.id) && withOutgoing.has(node.id)) return
      errors.push({
        id: crypto.randomUUID(),
        type: 'node',
        elementId: node.id,
        messageKey: 'validation.messages.nodeNotWired',
        messageParams: { label: node.data.label || node.type },
        severity: 'error',
      })
    })

    return { errors, warnings: [] }
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

      const assigned = Object.entries(node.data.mappingConfig?.fields ?? {})
        .filter(([, value]) => isNonEmptyMappingValue(value))
        .map(([key]) => key)
      // A required leaf is covered if it is mapped directly OR an ancestor path is mapped — mapping a
      // whole object/array subtree (parent path) covers its leaf required fields. The `.`/`[` suffix
      // anchors the match to a path-segment boundary so `$.loc` does not "cover" `$.location.lat`.
      const isCovered = (leaf: string) =>
        assigned.some(path => leaf === path || leaf.startsWith(`${path}.`) || leaf.startsWith(`${path}[`))
      const missing = required.filter(path => !isCovered(path))
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
  validateSqlSourceHasExplicitSchedule,
  validateFrostSinkHasNoMapping,
  validateSqlSourceNotToFrost,
  validateCronAndMappingWired,
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

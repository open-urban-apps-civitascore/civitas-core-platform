/**
 * Validation Service
 *
 * Provides pipeline validation functionality.
 * Validates structure, node configuration, and business rules.
 *
 */

import { isFormAccepted, NODE_FLOW_DECLARATIONS, type NodeFlowDeclaration } from '../_config/nodeFlow'
import { STA_GROUPS } from '../_constants/staTargetCatalog'
import { isCronNodeData, isDataSourceNodeData, isGeoPersistenceNodeData, isMappingNodeData } from '../_types/nodes'
import { type Pipeline, PIPELINE_NODE_TYPES, type PipelineNode } from '../_types/pipeline'

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
 * Whether the given node has a downstream path (following edge direction) to a FROST sink node. A
 * mapping's target mode depends on the sink it actually feeds — a FROST node standing unconnected
 * elsewhere on the canvas must not flip an unrelated mapping onto the STA envelope. Shared with the
 * MappingPanel, which fixes the mapping target to the STA envelope on the same condition.
 */
export const hasFrostSinkDownstream = (pipeline: Pick<Pipeline, 'nodes' | 'edges'>, nodeId: string): boolean => {
  const frostIds = new Set(pipeline.nodes.filter(node => node.type === PIPELINE_NODE_TYPES.Frost).map(node => node.id))
  if (frostIds.size === 0) return false

  const outgoing = new Map<string, string[]>()
  pipeline.edges.forEach(edge => {
    const targets = outgoing.get(edge.source) ?? []
    targets.push(edge.target)
    outgoing.set(edge.source, targets)
  })

  const queue = [nodeId]
  const visited = new Set(queue)
  while (queue.length > 0) {
    for (const next of outgoing.get(queue.shift()!) ?? []) {
      if (frostIds.has(next)) return true
      if (!visited.has(next)) {
        visited.add(next)
        queue.push(next)
      }
    }
  }
  return false
}

/**
 * Rule: a mapping feeding a FROST sink targets the fixed STA envelope, whose required-ness is
 * conditional per group — once a group ($.things[] / $.observations[]) is mapped at all, its
 * required paths (most importantly the find-or-create lookup keys) must all be assigned, and at
 * least one group must be mapped. The unconditional `targetRequiredFields` snapshot cannot express
 * this (it stays empty for STA targets), so this rule owns it — mirroring the adapter's
 * server-side StaEnvelopeCompiler validation, which would otherwise fail the deploy saga.
 */
const validateFrostMappingCoversStaGroups: ValidationRule = {
  id: 'frost-mapping-sta-group-coverage',
  name: 'FROST Mapping Covers STA Groups',
  description: 'A mapping feeding a FROST sink must cover the required paths of every STA group it touches',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []

    pipeline.nodes.forEach(node => {
      if (!isMappingNodeData(node.data)) return
      // a not-yet-configured/saved node is reported by other rules; avoid double errors
      if (!node.data.configured || node.data.targetRequiredFields === undefined) return
      // only a mapping that actually feeds the FROST sink targets the STA envelope
      if (!hasFrostSinkDownstream(pipeline, node.id)) return
      const label = node.data.label || node.type

      const assigned = Object.entries(node.data.mappingConfig?.fields ?? {})
        .filter(([, value]) => isNonEmptyMappingValue(value))
        .map(([key]) => key)

      const touchedGroups = STA_GROUPS.filter(group =>
        assigned.some(path => path === group.arrayPath || path.startsWith(group.pathPrefix)),
      )
      if (touchedGroups.length === 0) {
        errors.push({
          id: crypto.randomUUID(),
          type: 'node',
          elementId: node.id,
          messageKey: 'validation.messages.frostMappingNoStaElement',
          messageParams: { label },
          severity: 'error',
        })
        return
      }

      touchedGroups.forEach(group => {
        const missing = group.requiredPaths.filter(path => !assigned.includes(path))
        if (missing.length === 0) return
        errors.push({
          id: crypto.randomUUID(),
          type: 'node',
          elementId: node.id,
          messageKey: 'validation.messages.frostMappingGroupIncomplete',
          messageParams: { label, group: group.arrayPath, fields: missing.join(', ') },
          severity: 'error',
        })
      })
    })

    return { errors, warnings: [] }
  },
}

/**
 * Rule: a CRON trigger and a mapping node only take effect when wired into the flow (an incoming AND
 * an outgoing edge). The engine rejects a half-wired node at deploy; a detached node here would
 * otherwise be silently ignored or fail late. Mirrors the wiredness checks in the adapter's
 * FlowPath derivation. Not folded into validateFlowShape: its path and trigger checks deliberately
 * skip half-wired mappings and crons and leave them to this rule (the orphan rule only catches
 * nodes with no edge at all).
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

// ============================================================================
// Graph-driven flow rules (mirror of the adapter's FlowPath derivation)
// ============================================================================

/**
 * The adapter derives the deployed chain by walking the graph's wiring against the node flow
 * declarations (`_config/nodeFlow.ts`) and rejects the same violations these two rules report —
 * anchored at the same nodes, so the user sees at edit time exactly what the deploy would say.
 * Node existence on the canvas never decides anything here, only wiring does.
 */

const flowDeclOf = (node: PipelineNode): NodeFlowDeclaration | undefined =>
  (NODE_FLOW_DECLARATIONS as Partial<Record<string, NodeFlowDeclaration>>)[node.type]

/** Whether the node carries data (source/transform/sink) — edges between such nodes are data flow. */
const isFunctionalNode = (node: PipelineNode): boolean => {
  const role = flowDeclOf(node)?.role
  return role === 'source' || role === 'transform' || role === 'sink'
}

const nodeLabel = (node: PipelineNode): string => node.data.label || node.type

const errorAt = (
  node: PipelineNode,
  messageKey: string,
  messageParams?: Record<string, string | number>,
): PipelineValidationError => ({
  id: crypto.randomUUID(),
  type: 'node',
  elementId: node.id,
  messageKey,
  messageParams,
  severity: 'error',
})

const structureError = (messageKey: string): PipelineValidationError => ({
  id: crypto.randomUUID(),
  type: 'structure',
  messageKey,
  severity: 'error',
})

/** Adjacency of the data edges only — edges whose both endpoints are functional nodes. */
const dataFlowAdjacency = (
  pipeline: Pipeline,
  nodesById: Map<string, PipelineNode>,
): { dataOut: Map<string, PipelineNode[]>; dataIn: Map<string, PipelineNode[]> } => {
  const dataOut = new Map<string, PipelineNode[]>()
  const dataIn = new Map<string, PipelineNode[]>()
  pipeline.edges.forEach(edge => {
    const from = nodesById.get(edge.source)
    const to = nodesById.get(edge.target)
    if (!from || !to || !isFunctionalNode(from) || !isFunctionalNode(to)) return
    dataOut.set(from.id, [...(dataOut.get(from.id) ?? []), to])
    dataIn.set(to.id, [...(dataIn.get(to.id) ?? []), from])
  })
  return { dataOut, dataIn }
}

/**
 * Whether a mapping node lies upstream of the given node (following edge direction backwards).
 * Wiring-based, never existence-based — the upstream twin of {@link hasFrostSinkDownstream}.
 */
const hasMappingUpstream = (pipeline: Pick<Pipeline, 'nodes' | 'edges'>, nodeId: string): boolean => {
  const mappingIds = new Set(
    pipeline.nodes.filter(node => node.type === PIPELINE_NODE_TYPES.Mapping).map(node => node.id),
  )
  if (mappingIds.size === 0) return false

  const incoming = new Map<string, string[]>()
  pipeline.edges.forEach(edge => {
    incoming.set(edge.target, [...(incoming.get(edge.target) ?? []), edge.source])
  })

  const queue = [nodeId]
  const visited = new Set(queue)
  while (queue.length > 0) {
    for (const previous of incoming.get(queue.shift()!) ?? []) {
      if (mappingIds.has(previous)) return true
      if (!visited.has(previous)) {
        visited.add(previous)
        queue.push(previous)
      }
    }
  }
  return false
}

/**
 * Rule: every data edge must deliver a payload form its downstream node accepts, where a consumer
 * of RECORDS also accepts the coercible forms (the engine inserts the convert structurally). The
 * error hangs on the downstream node — the consumer states what it cannot digest.
 */
const validateEdgeCompatibility: ValidationRule = {
  id: 'edge-form-compatibility',
  name: 'Edge Payload-Form Compatibility',
  description: 'Every data edge must deliver a payload form its downstream node accepts',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []
    const nodesById = new Map(pipeline.nodes.map(node => [node.id, node]))

    pipeline.edges.forEach(edge => {
      const upstream = nodesById.get(edge.source)
      const downstream = nodesById.get(edge.target)
      if (!upstream || !downstream || !isFunctionalNode(upstream) || !isFunctionalNode(downstream)) return

      const acceptedInputs = flowDeclOf(downstream)?.acceptedInputs
      if (!acceptedInputs) return
      const offered = flowDeclOf(upstream)?.output?.(upstream.data)
      // an undeterminable form (unknown source connector) cannot be verified either way
      if (offered === undefined) return

      const hasMappedUpstream = hasMappingUpstream(pipeline, downstream.id)
      const accepted = acceptedInputs({ mappedUpstream: hasMappedUpstream })
      if (isFormAccepted(offered, accepted)) return

      // The only conflict reachable today is a records source feeding an unmapped FROST sink —
      // keep the adapter's actionable wording for it instead of the generic form message.
      errors.push(
        downstream.type === PIPELINE_NODE_TYPES.Frost && !hasMappedUpstream
          ? errorAt(downstream, 'validation.messages.sqlSourceToFrost')
          : errorAt(downstream, 'validation.messages.edgeFormIncompatible', {
              label: nodeLabel(downstream),
              upstreamLabel: nodeLabel(upstream),
              form: offered,
              accepted: accepted.join(', '),
            }),
      )
    })

    return { errors, warnings: [] }
  },
}

/**
 * Rule: the graph must describe one linear data flow — exactly one source and one sink (a
 * deliberate product restriction; mappings and future node kinds are unbounded), source first and
 * sink terminal, no branching/cycles/dead ends, every mapping on the path, unknown node kinds not
 * wired into the flow, and cron triggers feeding only the source, whose trigger port holds at most
 * one schedule.
 */
const validateFlowShape: ValidationRule = {
  id: 'flow-shape',
  name: 'Flow Shape',
  description: 'The graph must describe one linear source-to-sink data flow with a valid trigger binding',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []
    const warnings: PipelineValidationWarning[] = []
    const nodesById = new Map(pipeline.nodes.map(node => [node.id, node]))

    // A wired node of an unknown kind would silently deploy a different flow than modelled.
    // A loose one is left to the orphan rule.
    const flaggedUnknown = new Set<string>()
    pipeline.edges.forEach(edge => {
      for (const node of [nodesById.get(edge.source), nodesById.get(edge.target)]) {
        if (!node || flowDeclOf(node) || flaggedUnknown.has(node.id)) continue
        flaggedUnknown.add(node.id)
        errors.push(errorAt(node, 'validation.messages.unknownNodeInFlow', { label: nodeLabel(node) }))
      }
    })

    const sources = pipeline.nodes.filter(node => flowDeclOf(node)?.role === 'source')
    const sinks = pipeline.nodes.filter(node => flowDeclOf(node)?.role === 'sink')
    if (sources.length === 0) errors.push(structureError('validation.messages.sourceNodeRequired'))
    if (sinks.length === 0) errors.push(structureError('validation.messages.sinkNodeRequired'))
    sources.slice(1).forEach(node => {
      errors.push(errorAt(node, 'validation.messages.multipleSourceNodes', { label: nodeLabel(node) }))
    })
    sinks.slice(1).forEach(node => {
      errors.push(errorAt(node, 'validation.messages.multipleSinkNodes', { label: nodeLabel(node) }))
    })
    // Without the 1/1 anchor pair the path is undefined; the cardinality errors are the finding.
    if (sources.length !== 1 || sinks.length !== 1) return { errors, warnings }
    const source = sources[0]
    const sink = sinks[0]

    const { dataOut, dataIn } = dataFlowAdjacency(pipeline, nodesById)
    if ((dataIn.get(source.id) ?? []).length > 0) {
      errors.push(errorAt(source, 'validation.messages.nodeUnsupportedPosition', { label: nodeLabel(source) }))
    }
    if ((dataOut.get(sink.id) ?? []).length > 0) {
      errors.push(errorAt(sink, 'validation.messages.nodeUnsupportedPosition', { label: nodeLabel(sink) }))
    }

    // Walk the single data path source → … → sink.
    const onPath = new Set([source.id])
    let isWalkComplete = false
    let current = source
    for (;;) {
      if (current.id === sink.id) {
        isWalkComplete = true
        break
      }
      const next = dataOut.get(current.id) ?? []
      if (next.length === 0) {
        errors.push(structureError('validation.messages.noDataPath'))
        break
      }
      if (next.length > 1) {
        errors.push(errorAt(current, 'validation.messages.flowBranches', { label: nodeLabel(current) }))
        break
      }
      const step = next[0]
      if (onPath.has(step.id)) {
        errors.push(errorAt(step, 'validation.messages.flowCycle', { label: nodeLabel(step) }))
        break
      }
      onPath.add(step.id)
      current = step
    }

    const withIncoming = new Set(pipeline.edges.map(edge => edge.target))
    const withOutgoing = new Set(pipeline.edges.map(edge => edge.source))
    const isWired = (nodeId: string) => withIncoming.has(nodeId) && withOutgoing.has(nodeId)

    // A wired mapping off the walked path would silently not be applied. Half-wired mappings are
    // validateCronAndMappingWired's finding; an aborted walk leaves no defined path to check against.
    if (isWalkComplete) {
      pipeline.nodes.forEach(node => {
        if (node.type !== PIPELINE_NODE_TYPES.Mapping || onPath.has(node.id) || !isWired(node.id)) return
        errors.push(errorAt(node, 'validation.messages.mappingNotOnPath', { label: nodeLabel(node) }))
      })
    }

    // Trigger binding: a cron schedules the source's entry processor — feeding anything else is
    // meaningless, and the source's trigger port holds at most one schedule.
    const schedulingCrons: PipelineNode[] = []
    for (const cron of pipeline.nodes.filter(node => node.type === PIPELINE_NODE_TYPES.Cron)) {
      const outgoing = pipeline.edges.filter(edge => edge.source === cron.id)
      if (outgoing.some(edge => edge.target !== source.id)) {
        errors.push(errorAt(cron, 'validation.messages.nodeUnsupportedPosition', { label: nodeLabel(cron) }))
        continue
      }
      // a cron without an outgoing edge is validateCronAndMappingWired's finding
      if (outgoing.length > 0) schedulingCrons.push(cron)
    }
    schedulingCrons.slice(1).forEach(cron => {
      errors.push(errorAt(cron, 'validation.messages.cronTriggerCapacity', { label: nodeLabel(cron) }))
    })
    if (schedulingCrons.length > 0) {
      const canSchedule = flowDeclOf(source)?.acceptsSchedule?.(source.data)
      schedulingCrons.forEach(cron => {
        if (canSchedule === false) {
          errors.push(errorAt(cron, 'validation.messages.cronMqttIncompatible'))
        } else if (canSchedule === undefined) {
          warnings.push({
            id: crypto.randomUUID(),
            type: 'node',
            elementId: cron.id,
            messageKey: 'validation.messages.cronSourceConnectorUnknown',
            severity: 'warning',
          })
        }
      })
    }

    return { errors, warnings }
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
  validateFlowShape,
  validateEdgeCompatibility,
  validateSqlSourceHasExplicitSchedule,
  validateFrostMappingCoversStaGroups,
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

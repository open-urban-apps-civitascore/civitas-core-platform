/**
 * Validation Service
 *
 * Provides pipeline validation functionality.
 * Validates structure, node configuration, and business rules.
 *
 */

import { isFormAccepted, NODE_FLOW_DECLARATIONS, type NodeFlowDeclaration } from '../_config/nodeFlow'
import {
  isReservedStaKeyName,
  isSafeStaKeyName,
  STA_ENTITIES,
  STA_FIXED_TARGET_PATHS,
  type StaEntity,
} from '../_constants/staTargetCatalog'
import { isCronNodeData, isDataSourceNodeData, isGeoPersistenceNodeData, isMappingNodeData } from '../_types/nodes'
import { type Pipeline, PIPELINE_NODE_TYPES, type PipelineNode } from '../_types/pipeline'
import { normalizeTableName, type TableNameOwners } from './dataSinkNameService'

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
 * Supports: wildcards (*), ranges (-), steps (/), lists (,), ? for day-of-month/day-of-week,
 * L/W qualifiers, and #n (nth weekday). Range ends and steps are bounded like the base values:
 * the adapter deliberately checks only the field count, so this is the sole guard keeping an
 * out-of-range value (e.g. hours 6-99) from failing only inside NiFi as an opaque saga error.
 */
export const isValidNifiCron = (expression: string): boolean => {
  const trimmed = expression.trim()
  if (!trimmed) return false

  const fields = trimmed.split(/\s+/)
  if (fields.length !== 6) return false

  const [seconds, minutes, hours, dayOfMonth, month, dayOfWeek] = fields

  // Seconds: 0-59, supports *, */N, ranges, steps, lists
  const secondsPattern =
    /^(\*(\/([1-9]|[1-5]\d))?|(\d|[0-5]\d)(-(\d|[0-5]\d)|\/([1-9]|[1-5]\d))?)([,](\*(\/([1-9]|[1-5]\d))?|(\d|[0-5]\d)(-(\d|[0-5]\d)|\/([1-9]|[1-5]\d))?))*$/
  // Minutes: 0-59
  const minutesPattern =
    /^(\*(\/([1-9]|[1-5]\d))?|(\d|[0-5]\d)(-(\d|[0-5]\d)|\/([1-9]|[1-5]\d))?)([,](\*(\/([1-9]|[1-5]\d))?|(\d|[0-5]\d)(-(\d|[0-5]\d)|\/([1-9]|[1-5]\d))?))*$/
  // Hours: 0-23
  const hoursPattern =
    /^(\*(\/([1-9]|1\d|2[0-3]))?|(\d|1\d|2[0-3])(-(\d|1\d|2[0-3])|\/([1-9]|1\d|2[0-3]))?)([,](\*(\/([1-9]|1\d|2[0-3]))?|(\d|1\d|2[0-3])(-(\d|1\d|2[0-3])|\/([1-9]|1\d|2[0-3]))?))*$/
  // Day of month: 1-31 or ? or L/LW; W only on a single day (5-10W is rejected by the parser)
  const dayOfMonthPattern =
    /^(\*(\/([1-9]|[12]\d|3[01]))?|\?|LW?|([1-9]|[12]\d|3[01])(W|-([1-9]|[12]\d|3[01])|\/([1-9]|[12]\d|3[01]))?)([,](\*(\/([1-9]|[12]\d|3[01]))?|LW?|([1-9]|[12]\d|3[01])(W|-([1-9]|[12]\d|3[01])|\/([1-9]|[12]\d|3[01]))?))*$/
  // Month: 1-12 or JAN-DEC
  const monthPattern =
    /^(\*(\/([1-9]|1[0-2]))?|([1-9]|1[0-2]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)([-/]([1-9]|1[0-2]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?)([,](\*(\/([1-9]|1[0-2]))?|([1-9]|1[0-2]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)([-/]([1-9]|1[0-2]|JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC))?))*$/i
  // Day of week: 0-7 (Spring numbering: 0 and 7 = Sunday, 1 = Monday) or SUN-SAT or ? or L;
  // L/#n only on a single day (MON-FRI#3 is rejected by the parser)
  const dayOfWeekPattern =
    /^(\*(\/[1-7])?|\?|L|([0-7]|SUN|MON|TUE|WED|THU|FRI|SAT)(L|#[1-5]|-([0-7]|SUN|MON|TUE|WED|THU|FRI|SAT)|\/[1-7])?)([,](\*(\/[1-7])?|([0-7]|SUN|MON|TUE|WED|THU|FRI|SAT)(L|#[1-5]|-([0-7]|SUN|MON|TUE|WED|THU|FRI|SAT)|\/[1-7])?))*$/i

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

interface PipelineValidationIssue<S extends 'error' | 'warning'> {
  id: string
  type: 'node' | 'edge' | 'structure'
  elementId?: string
  messageKey: string
  messageParams?: Record<string, string | number>
  severity: S
}

export type PipelineValidationError = PipelineValidationIssue<'error'>

export type PipelineValidationWarning = PipelineValidationIssue<'warning'>

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
  validate: (
    pipeline: Pipeline,
    context: PipelineValidationContext,
  ) => {
    errors: PipelineValidationError[]
    warnings: PipelineValidationWarning[]
  }
}

/**
 * Data a rule needs that is not part of the pipeline itself.
 *
 */
export interface PipelineValidationContext {
  /** Table names used outside this pipeline, mapped to the pipeline using them (see dataSinkNameService). */
  tableNameOwners?: TableNameOwners
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
  description: 'A geo persistence table name may be used only once per dataset',
  validate: (pipeline: Pipeline, context: PipelineValidationContext) => {
    const errors: PipelineValidationError[] = []
    const ownersElsewhere = context.tableNameOwners ?? {}
    const seen = new Set<string>()

    pipeline.nodes.forEach(node => {
      if (!isGeoPersistenceNodeData(node.data)) return
      const tableName = normalizeTableName(node.data.tableName)
      if (!tableName) return

      const owner = seen.has(tableName) ? pipeline.name : ownersElsewhere[tableName]
      if (owner) {
        errors.push(
          errorAt(node, 'validation.messages.duplicateTableName', {
            tableName: node.data.tableName.trim(),
            pipeline: owner,
          }),
        )
      }
      seen.add(tableName)
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

const mappingNodeIds = (nodes: Pipeline['nodes']): Set<string> =>
  new Set(nodes.filter(node => node.type === PIPELINE_NODE_TYPES.Mapping).map(node => node.id))

const incomingAdjacency = (edges: Pipeline['edges']): Map<string, string[]> => {
  const incoming = new Map<string, string[]>()
  edges.forEach(edge => {
    incoming.set(edge.target, [...(incoming.get(edge.target) ?? []), edge.source])
  })
  return incoming
}

/**
 * The mappings that directly feed a FROST sink — the last mapping of each chain, found by walking
 * the wiring backwards from every FROST node and stopping at the first mapping per path. In a
 * mapping chain only this final mapping carries the STA target paths (the sink's envelope rebuild
 * compiles exactly it); earlier mappings are ordinary record transformations. Wiring-based — a
 * FROST node standing unconnected elsewhere on the canvas must not flip an unrelated mapping's
 * rules.
 */
const lastMappingsBeforeFrostSinks = (pipeline: Pick<Pipeline, 'nodes' | 'edges'>): Set<string> => {
  const result = new Set<string>()
  const mappingIds = mappingNodeIds(pipeline.nodes)
  const incoming = incomingAdjacency(pipeline.edges)

  const queue = pipeline.nodes.filter(node => node.type === PIPELINE_NODE_TYPES.Frost).map(node => node.id)
  const visited = new Set(queue)
  while (queue.length > 0) {
    for (const previous of incoming.get(queue.shift()!) ?? []) {
      if (visited.has(previous)) continue
      visited.add(previous)
      if (mappingIds.has(previous)) {
        result.add(previous)
      } else {
        queue.push(previous)
      }
    }
  }
  return result
}

/**
 * Rule: the last mapping before a FROST sink targets a Thing-shaped datastructure with
 * record-anchored catalog paths. Mirrors the deploy engine's FrostMappingCompiler validation —
 * which compiles exactly this final mapping and would otherwise fail the deploy saga: only catalog
 * (or match-key) paths may be assigned, the Thing's match keys are always required, each entity's
 * create set is all-or-nothing (all mapped = creatable, none = lookup-only), a Location needs a
 * creatable Thing (it is created only via the Thing's deep insert), a touched Datastream needs its
 * match keys, and a touched Observation needs `result`. The match keys are the target structure's
 * `{id}`-marked attributes (fallback `reference`), snapshotted on the node at mapping-save time
 * ({@code staMatchKeys}) — the unconditional `targetRequiredFields` snapshot cannot express this.
 * Earlier mappings of a chain are ordinary record transformations covered by the
 * required-target-fields rule.
 */
// Kept for easy re-enable; currently commented out of VALIDATION_RULES.
// eslint-disable-next-line unused-imports/no-unused-vars
const validateFrostMappingCoversStaGroups: ValidationRule = {
  id: 'frost-mapping-sta-group-coverage',
  name: 'FROST Mapping Covers STA Entities',
  description: 'The last mapping before a FROST sink must satisfy the FROST catalog rules',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []
    const warnings: PipelineValidationWarning[] = []
    const staMappingIds = lastMappingsBeforeFrostSinks(pipeline)

    pipeline.nodes.forEach(node => {
      if (!isMappingNodeData(node.data)) return
      // a not-yet-configured node is reported by validateNodeConfiguration; avoid a double error
      if (!node.data.configured) return
      if (!staMappingIds.has(node.id)) return
      const label = node.data.label || node.type

      const keys = node.data.staMatchKeys
      if (node.data.targetRequiredFields === undefined || keys === undefined) {
        // Never actually saved (or saved before the match-key snapshot existed) — the generic
        // required-fields rule deliberately skips FROST-final mappings, so this rule must report
        // the unsaved state itself.
        errors.push(errorAt(node, 'validation.messages.mappingNotSaved', { label }))
        return
      }

      const assigned = new Set(
        Object.entries(node.data.mappingConfig?.fields ?? {})
          .filter(([, value]) => isNonEmptyMappingValue(value))
          .map(([key]) => key),
      )
      const allowed = new Set([...STA_FIXED_TARGET_PATHS, ...keys.thingBag, ...keys.datastreamBag])
      const entity = (key: StaEntity['key']): StaEntity =>
        STA_ENTITIES.find(candidate => candidate.key === key) as StaEntity

      // The engine accepts exactly the catalog + match-key paths and fails the deploy saga for
      // anything else — surface that here instead of letting it pass edit-time.
      for (const path of assigned) {
        if (!allowed.has(path)) {
          errors.push(errorAt(node, 'validation.messages.frostMappingUnknownStaTarget', { label, path }))
        }
      }

      const allAssigned = (paths: readonly string[]) => paths.every(path => assigned.has(path))
      const anyAssigned = (paths: readonly string[]) => paths.some(path => assigned.has(path))
      const missingOf = (paths: readonly string[]) => paths.filter(path => !assigned.has(path)).join(', ')

      const requireCompleteCreateSet = (candidate: StaEntity) => {
        if (anyAssigned(candidate.createPaths) && !allAssigned(candidate.createPaths)) {
          errors.push(
            errorAt(node, 'validation.messages.frostMappingCreateSetIncomplete', {
              label,
              entity: candidate.key,
              fields: missingOf(candidate.createPaths),
            }),
          )
        }
      }

      // The engine whitelists every bag attribute name (they reach $filter URLs and template keys)
      // and rejects one named after the 'properties' bag it lives in — surface both at edit time.
      const checkKeyNames = (bagPaths: readonly string[]) => {
        for (const path of bagPaths) {
          const keyName = path.split('.').pop() as string
          if (!isSafeStaKeyName(keyName)) {
            errors.push(errorAt(node, 'validation.messages.frostMappingUnsafeMatchKeyName', { label, keyName }))
          } else if (isReservedStaKeyName(keyName)) {
            errors.push(errorAt(node, 'validation.messages.frostMappingReservedMatchKeyName', { label, keyName }))
          }
        }
      }
      checkKeyNames(keys.thingBag)
      checkKeyNames(keys.datastreamBag)

      // Thing: the match keys are the find-or-create identity — always required.
      if (keys.thing.length === 0) {
        errors.push(
          errorAt(node, 'validation.messages.frostMappingNoMatchKeyInStructure', {
            label,
            entity: 'thing',
          }),
        )
      } else if (!allAssigned(keys.thing)) {
        errors.push(
          errorAt(node, 'validation.messages.frostMappingMissingMatchKeys', {
            label,
            entity: 'thing',
            fields: missingOf(keys.thing),
          }),
        )
      }
      requireCompleteCreateSet(entity('thing'))

      const isThingCreatable = allAssigned(entity('thing').createPaths)
      if (anyAssigned([...entity('location').createPaths, ...entity('location').optionalPaths])) {
        if (!isThingCreatable) {
          errors.push(errorAt(node, 'validation.messages.frostMappingLocationNeedsCreatableThing', { label }))
        }
        requireCompleteCreateSet(entity('location'))
      }

      const isDatastreamTouched = [...assigned].some(path => path.startsWith(entity('datastream').pathPrefix))
      if (isDatastreamTouched) {
        if (keys.datastream.length === 0) {
          errors.push(
            errorAt(node, 'validation.messages.frostMappingNoMatchKeyInStructure', {
              label,
              entity: 'datastream',
            }),
          )
        } else if (!allAssigned(keys.datastream)) {
          errors.push(
            errorAt(node, 'validation.messages.frostMappingMissingMatchKeys', {
              label,
              entity: 'datastream',
              fields: missingOf(keys.datastream),
            }),
          )
        }
        requireCompleteCreateSet(entity('datastream'))
      }

      const observation = entity('observation')
      const isObservationTouched = anyAssigned([...observation.createPaths, ...observation.optionalPaths])
      if (isObservationTouched && !allAssigned(observation.createPaths)) {
        errors.push(errorAt(node, 'validation.messages.frostMappingObservationNeedsResult', { label }))
      }

      const featureOfInterest = entity('featureOfInterest')
      if (anyAssigned(featureOfInterest.createPaths)) {
        if (!isObservationTouched) {
          errors.push(errorAt(node, 'validation.messages.frostMappingFeatureOfInterestNeedsObservation', { label }))
        }
        requireCompleteCreateSet(featureOfInterest)
      }

      if (keys.isFallback && keys.thing.length > 0) {
        warnings.push({
          id: crypto.randomUUID(),
          type: 'node',
          elementId: node.id,
          messageKey: 'validation.messages.frostMappingFallbackMatchKey',
          messageParams: { label },
          severity: 'warning',
        })
      }
    })

    return { errors, warnings }
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
      errors.push(errorAt(node, 'validation.messages.nodeNotWired', { label: nodeLabel(node) }))
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
 * Node existence never silently changes the flow — a loose transform/trigger node fails loud
 * rather than being ignored; only wiring contributes flow semantics.
 */

const flowDeclOf = (node: PipelineNode): NodeFlowDeclaration | undefined =>
  (NODE_FLOW_DECLARATIONS as Partial<Record<string, NodeFlowDeclaration>>)[node.type]

/** Whether the node carries data (source/transform/sink) — edges between such nodes are data flow. */
const isFunctionalNode = (node: PipelineNode): boolean => {
  const role = flowDeclOf(node)?.role
  return role === 'source' || role === 'transform' || role === 'sink'
}

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
 * Wiring-based, never existence-based — the upstream twin of the downstream walk in
 * {@link lastMappingsBeforeFrostSinks}.
 */
const hasMappingUpstream = (pipeline: Pick<Pipeline, 'nodes' | 'edges'>, nodeId: string): boolean => {
  const mappingIds = mappingNodeIds(pipeline.nodes)
  if (mappingIds.size === 0) return false
  const incoming = incomingAdjacency(pipeline.edges)

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

      const downstreamDecl = flowDeclOf(downstream)
      if (!downstreamDecl || !('acceptedInputs' in downstreamDecl)) return
      const acceptedInputs = downstreamDecl.acceptedInputs
      const upstreamDecl = flowDeclOf(upstream)
      const offered = upstreamDecl && 'output' in upstreamDecl ? upstreamDecl.output(upstream.data) : undefined
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
      const sourceDecl = flowDeclOf(source)
      const canSchedule =
        sourceDecl && 'acceptsSchedule' in sourceDecl ? sourceDecl.acceptsSchedule(source.data) : undefined
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

/**
 * Rule: each mapping→mapping data edge must continue the chain — the upstream mapping writes its
 * target datastructure (id + version), so the downstream mapping must read exactly that as its
 * source, or its field paths resolve against a schema that never arrives. Anchored at the
 * downstream mapping (it declares the wrong input). A mapping whose schema selection is still
 * incomplete is left to validateNodeConfiguration.
 */
const validateMappingChainStructure: ValidationRule = {
  id: 'mapping-chain-structure',
  name: 'Mapping Chain Structure',
  description: "Each mapping in a chain must read its predecessor's target datastructure",
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []
    const nodesById = new Map(pipeline.nodes.map(node => [node.id, node]))

    pipeline.edges.forEach(edge => {
      const upstream = nodesById.get(edge.source)
      const downstream = nodesById.get(edge.target)
      if (upstream?.type !== PIPELINE_NODE_TYPES.Mapping || downstream?.type !== PIPELINE_NODE_TYPES.Mapping) return
      if (!isMappingNodeData(upstream.data) || !isMappingNodeData(downstream.data)) return

      const written = { id: upstream.data.targetDatastructureId, version: upstream.data.targetVersionId }
      const read = { id: downstream.data.sourceDatastructureId, version: downstream.data.sourceVersionId }
      if (!written.id || !read.id) return
      const isSameVersion = !written.version || !read.version || written.version === read.version
      if (written.id === read.id && isSameVersion) return

      errors.push(
        errorAt(downstream, 'validation.messages.mappingChainStructureMismatch', {
          label: nodeLabel(downstream),
          upstreamLabel: nodeLabel(upstream),
        }),
      )
    })

    return { errors, warnings: [] }
  },
}

/**
 * Rule: every edge must resolve both endpoints to nodes. The adapter rejects an unknown edge
 * endpoint at graph construction; a dangling edge here (possible only through a corrupt
 * round-trip, the editor removes edges with their nodes) would otherwise count a node as "wired"
 * in the wiredness rules and fail only at deploy.
 */
const validateEdgeEndpoints: ValidationRule = {
  id: 'edge-endpoints',
  name: 'Edge Endpoints',
  description: 'Every edge must connect two existing nodes',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []
    const nodeIds = new Set(pipeline.nodes.map(node => node.id))

    pipeline.edges.forEach(edge => {
      if (nodeIds.has(edge.source) && nodeIds.has(edge.target)) return
      errors.push({
        id: crypto.randomUUID(),
        type: 'edge',
        elementId: edge.id,
        messageKey: 'validation.messages.edgeEndpointMissing',
        severity: 'error',
      })
    })

    return { errors, warnings: [] }
  },
}

/**
 * Rule: a mapping-typed node whose data lost the mapping shape (no `mappingConfig`) is corrupt —
 * graphs round-trip through the backend's opaque JSON, so the TS types cannot guarantee the shape
 * at runtime. Every mapping rule skips such a node (its data cannot be interpreted), so without
 * this rule it would validate clean and fail only at deploy.
 */
const validateMappingDataShape: ValidationRule = {
  id: 'mapping-data-shape',
  name: 'Mapping Data Shape',
  description: 'A mapping node must carry mapping-shaped data',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []

    pipeline.nodes.forEach(node => {
      if (node.type !== PIPELINE_NODE_TYPES.Mapping || isMappingNodeData(node.data)) return
      errors.push(errorAt(node, 'validation.messages.mappingDataCorrupt', { label: nodeLabel(node) }))
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
 * is a legacy node — so it is blocked with an error, not silently accepted. Exception: a node that
 * references an installed registry artifact ({@code mappingRef} set, editor config empty) has its
 * content behind the ref, not in editor state — the bundle-import hydration produces exactly that
 * shape and it is not "unsaved". (A node that is not yet {@code configured} is left to {@code
 * validateNodeConfiguration}; this rule does not double-report it.) Optional target fields may stay
 * unmapped.
 */
const validateMappingCoversRequiredTargetFields: ValidationRule = {
  id: 'mapping-required-target-fields',
  name: 'Mapping Covers Required Target Fields',
  description: 'A mapping must assign every required target field',
  validate: (pipeline: Pipeline) => {
    const errors: PipelineValidationError[] = []
    const staMappingIds = lastMappingsBeforeFrostSinks(pipeline)

    pipeline.nodes.forEach(node => {
      if (!isMappingNodeData(node.data)) return
      // a not-yet-configured node is reported by validateNodeConfiguration; avoid a double error
      if (!node.data.configured) return
      // The last mapping before a FROST sink follows the catalog's conditional requiredness
      // (lookup-only vs creatable) — the unconditional snapshot would wrongly force the create
      // fields of a lookup-only entity. validateFrostMappingCoversStaGroups owns that node.
      if (staMappingIds.has(node.id)) return
      const required = node.data.targetRequiredFields

      if (required === undefined) {
        // A mapping that references an installed registry artifact (bundle import) carries its
        // content behind mappingRef — there is no editor-authored state to check, and "never
        // saved" would be wrong. Only the hydrated shape (ref present, empty editor config)
        // passes; a mapping edited and saved in this editor always carries the snapshot.
        if (node.data.mappingRef && Object.keys(node.data.mappingConfig?.fields ?? {}).length === 0) {
          return
        }
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
  validateMappingChainStructure,
  validateSqlSourceHasExplicitSchedule,
  // FROST mapping/STA-catalog validation temporarily disabled; re-add to re-enable.
  // validateFrostMappingCoversStaGroups,
  validateCronAndMappingWired,
  validateEdgeEndpoints,
  validateMappingDataShape,
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
export const validatePipeline = (
  pipeline: Pipeline,
  context: PipelineValidationContext = {},
): PipelineValidationResult => {
  const allErrors: PipelineValidationError[] = []
  const allWarnings: PipelineValidationWarning[] = []

  VALIDATION_RULES.forEach(rule => {
    const { errors, warnings } = rule.validate(pipeline, context)
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
export const validatePipelineWithNodeStatus = (
  pipeline: Pipeline,
  context: PipelineValidationContext = {},
): ValidationResultWithNodeStatus => {
  const result = validatePipeline(pipeline, context)
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

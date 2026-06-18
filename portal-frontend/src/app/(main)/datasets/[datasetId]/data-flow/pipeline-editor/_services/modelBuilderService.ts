/**
 * RedPandaConnect Model Builder Service
 *
 * Pure-function service that transforms a visual Pipeline graph (React Flow nodes + edges)
 * into a RedPandaConnect configuration JSON object.
 *
 * Uses a node handler registry pattern for extensibility — new node types can be added
 * by registering a handler in the nodeHandlerRegistry Map.
 */

import type { DataSourceNodeData, MappingNodeData } from '../_types/nodes'
import { isDataSourceNodeData, isFrostNodeData, isMappingNodeData } from '../_types/nodes'
import type { Pipeline, PipelineNode, PipelineNodeType } from '../_types/pipeline'
import { PIPELINE_NODE_TYPES } from '../_types/pipeline'

/**
 * Context passed to node handlers during model building.
 */
interface ModelBuildContext {
  pipeline: Pipeline
  orderedNodes: PipelineNode[]
}

/**
 * Node handler interface.
 * Each node type that contributes to the RedPandaConnect model
 * implements this interface.
 */
interface NodeModelHandler {
  /** Which pipeline section this node contributes to */
  section: 'input' | 'processor' | 'output'
  /** Build the config fragment for this node */
  build: (node: PipelineNode, context: ModelBuildContext) => object | null
}

// ============================================================================
// Node Handlers
// ============================================================================

/**
 * Builds the RedPandaConnect input section for a DataSource node.
 *
 * Extracts the connector type (mqtt, sql, csv, rest, s3) from entityMetadata
 * and generates a labeled input config. The backend resolves actual connection
 * credentials from the label.
 *
 * @example
 * // Returns: { mqtt: { label: "datasource_42" } }
 */
const buildDataSourceInput = (node: PipelineNode): object | null => {
  if (!isDataSourceNodeData(node.data)) return null

  const data = node.data as DataSourceNodeData
  const entityId = data.entityId

  return {
    label: '${' + `${entityId}` + '}',
  }
}

/**
 * Builds a mapping processor from a Mapping node's compiled config (spec §13).
 *
 * @example
 * // Returns: { mapping: { fields: { "$.title": "$.name" }, ... } }
 */
const buildMappingProcessor = (node: PipelineNode): object | null => {
  if (!isMappingNodeData(node.data)) return null

  const data = node.data as MappingNodeData

  return {
    mapping: data.mappingConfig,
  }
}

/**
 * Builds the static FROST processing and output sections.
 *
 * This is a fixed template that is the same for all feed-in pipelines.
 * It replicates the switch + branch pattern from mqtt-to-frost.md.
 * Uses ${FROST_BASE} as a placeholder that the config-adapter resolves before deployment.
 *
 * Note: eslint naming-convention is disabled for this function because
 * RedPandaConnect config uses snake_case property names (request_map, result_map,
 * http_client, retry_period, Content-Type).
 */

const buildFrostSection = (): { processors: object[]; output: object } => {
  /* eslint-disable @typescript-eslint/naming-convention */
  const processors: object[] = [
    {
      switch: [
        {
          check: 'this.exists("things") && this.things.length() > 0',
          processors: [
            {
              mapping: 'root = this.things',
            },
            {
              unarchive: {
                format: 'json_array',
              },
            },
            {
              branch: {
                request_map: ['root = ""', 'meta req_id = this.properties.reference'].join('\n'),
                processors: [
                  {
                    http: {
                      url: '${FROST_BASE}/Things?$filter=properties/reference%20eq%20\'${! meta("req_id").string().escape_url_query() }\'',
                      verb: 'GET',
                      headers: {
                        Accept: 'application/json',
                        Authorization: 'Basic YWRtaW46YWRtaW4=',
                      },
                      timeout: '10s',
                    },
                  },
                ],
                result_map: ['meta result = this', 'root = deleted()'].join('\n'),
              },
            },
            {
              mapping: [
                '# Lookup aus Meta holen',
                'let resp   = meta("result").catch("")',
                'let parsed = if $resp == "" { null } else { $resp.parse_json().catch(null) }',
                'let first  = if $parsed == null { null } else { $parsed.value.index(0).catch(null) }',
                '',
                '# Existiert schon ein Thing?',
                'let exists = $first != null',
                '',
                '# POST vs PATCH als Metadaten setzen (für den switch output)',
                'meta is_post   = !$exists',
                'meta frost_url = if $exists {',
                '  "${FROST_BASE}/Things(" + ($first."@iot.id").string() + ")"',
                '} else {',
                '  "${FROST_BASE}/Things"',
                '}',
              ].join('\n'),
            },
          ],
        },
        {
          check: 'this.exists("observations") && this.observations.length() > 0',
          processors: [
            {
              mapping: 'root = this.observations',
            },
            {
              unarchive: {
                format: 'json_array',
              },
            },
            {
              branch: {
                request_map: [
                  'root = ""',
                  'meta req_id = this.parameters.reference',
                  'meta name = this.parameters.name',
                ].join('\n'),
                processors: [
                  {
                    http: {
                      url: '${FROST_BASE}/Datastreams?$filter=properties/reference%20eq%20\'${! meta("req_id").string().escape_url_query() }\'%20and%20name%20eq%20\'${! meta("name").string().escape_url_query() }\'',
                      verb: 'GET',
                      headers: {
                        Accept: 'application/json',
                        Authorization: 'Basic YWRtaW46YWRtaW4=',
                      },
                      timeout: '10s',
                    },
                  },
                ],
                result_map: ['meta result = this', 'root = deleted()'].join('\n'),
              },
            },
            {
              mapping: [
                '# Lookup aus Meta holen',
                'let resp   = meta("result").catch("")',
                'let parsed = if $resp == "" { null } else { $resp.parse_json().catch(null) }',
                'let first  = if $parsed == null { null } else { $parsed.value.index(0).catch(null) }',
                '',
                '# Does a Datastream already exist for this observation?',
                'let exists = $first != null',
                '',
                '# POST vs PATCH als Metadaten setzen (für den switch output)',
                'meta is_post   = $exists',
                'meta frost_url = if $exists {',
                '  "${FROST_BASE}/Observations"',
                '} ',
                '',
                'if $exists {',
                '  root = this.merge({',
                '    "Datastream": { ',
                '      "@iot.id": $first."@iot.id"',
                '      }',
                '  })',
                '} else {',
                '  root = deleted()',
                '}',
              ].join('\n'),
            },
            {
              log: {
                level: 'INFO',
                message: 'META=${! meta() } BODY=${! json() }',
              },
            },
          ],
        },
      ],
    },
  ]

  const output = {
    switch: {
      cases: [
        {
          check: 'meta("is_post") == "true"',
          output: {
            http_client: {
              url: '${! meta("frost_url") }',
              verb: 'POST',
              headers: {
                'Content-Type': 'application/json',
                Accept: 'application/json',
                Authorization: 'Basic YWRtaW46YWRtaW4=',
              },
              timeout: '30s',
              retries: 3,
              retry_period: '2s',
            },
          },
        },
        {
          check: 'meta("is_post") == "false"',
          output: {
            drop: {},
          },
        },
      ],
    },
  }

  return { processors, output }
}

// ============================================================================
// Node Handler Registry
// ============================================================================

const nodeHandlerRegistry = new Map<PipelineNodeType, NodeModelHandler>([
  [
    PIPELINE_NODE_TYPES.DataSource,
    {
      section: 'input',
      build: (node: PipelineNode) => buildDataSourceInput(node),
    },
  ],
  [
    PIPELINE_NODE_TYPES.Mapping,
    {
      section: 'processor',
      build: (node: PipelineNode) => buildMappingProcessor(node),
    },
  ],
  [
    PIPELINE_NODE_TYPES.Frost,
    {
      section: 'output',
      // FROST is handled specially via buildFrostSection() — the handler
      // signals presence but the main entry point calls buildFrostSection() directly
      build: () => ({}),
    },
  ],
])

// ============================================================================
// Graph Traversal
// ============================================================================

/**
 * Traverses the pipeline graph from Start to End following edges.
 * Returns nodes in execution order, excluding Start and End nodes.
 *
 * Algorithm:
 * 1. Find the Start node
 * 2. Follow outgoing edges to find next node
 * 3. Repeat until End node is reached or no more edges
 * 4. Return ordered array (excluding Start/End)
 */
export const getOrderedNodes = (pipeline: Pipeline): PipelineNode[] => {
  const { nodes, edges } = pipeline

  // Find the Start node
  const startNode = nodes.find(n => n.type === PIPELINE_NODE_TYPES.Start)
  if (!startNode) return []

  // Build a quick lookup: source node ID → edge
  const edgeBySource = new Map<string, (typeof edges)[number]>()
  for (const edge of edges) {
    edgeBySource.set(edge.source, edge)
  }

  // Build a quick node lookup by ID
  const nodeById = new Map<string, PipelineNode>()
  for (const node of nodes) {
    nodeById.set(node.id, node)
  }

  const ordered: PipelineNode[] = []
  const visited = new Set<string>()
  let currentId: string | undefined = startNode.id

  while (currentId) {
    // Prevent infinite loops
    if (visited.has(currentId)) break
    visited.add(currentId)

    const edge = edgeBySource.get(currentId)
    if (!edge) break

    const nextNode = nodeById.get(edge.target)
    if (!nextNode) break

    // Stop at End node (don't include it)
    if (nextNode.type === PIPELINE_NODE_TYPES.End) break

    ordered.push(nextNode)
    currentId = nextNode.id
  }

  return ordered
}

// ============================================================================
// Pipeline Type Detection
// ============================================================================

/**
 * The type of pipeline based on node composition.
 *
 * - 'feedin': Contains a DataSource node (data ingestion)
 * - 'unknown': Pattern does not match
 */
export const PIPELINE_TYPES = {
  feedIn: 'feedin',
  unknown: 'unknown',
} as const

export type PipelineType = (typeof PIPELINE_TYPES)[keyof typeof PIPELINE_TYPES]

/**
 * Detects pipeline type based on node composition.
 *
 * - 'feedin': Contains a DataSource node
 * - 'unknown': Pattern does not match
 */
export const detectPipelineType = (nodes: PipelineNode[]): PipelineType => {
  const hasDataSource = nodes.some(n => isDataSourceNodeData(n.data))
  if (hasDataSource) return PIPELINE_TYPES.feedIn

  return PIPELINE_TYPES.unknown
}

// ============================================================================
// Main Entry Point
// ============================================================================

/**
 * Builds a RedPandaConnect configuration from a Pipeline graph.
 *
 * @param pipeline - The pipeline with nodes and edges
 * @returns The config object (caller JSON.stringifies it), or null for provide/unknown pipelines
 *
 * Flow:
 * 1. Traverse graph to get ordered nodes
 * 2. Detect pipeline type
 * 3. If 'provide' or 'unknown' → return null
 * 4. If 'feedin' → build input, processors, output sections
 * 5. Return assembled config object
 */
export const buildRedPandaConnectModel = (pipeline: Pipeline): object | null => {
  const orderedNodes = getOrderedNodes(pipeline)
  const pipelineType = detectPipelineType(orderedNodes)

  if (pipelineType !== PIPELINE_TYPES.feedIn) return null

  const context: ModelBuildContext = { pipeline, orderedNodes }

  // Build input section from DataSource node
  let input: object | null = null
  const dataSourceNode = orderedNodes.find(n => isDataSourceNodeData(n.data))
  if (dataSourceNode) {
    const handler = nodeHandlerRegistry.get(PIPELINE_NODE_TYPES.DataSource)
    input = handler?.build(dataSourceNode, context) ?? null
  }

  if (!input) return null

  // Build processors from Mapping nodes
  const processors: object[] = []
  for (const node of orderedNodes) {
    if (isMappingNodeData(node.data)) {
      const handler = nodeHandlerRegistry.get(PIPELINE_NODE_TYPES.Mapping)
      const processor = handler?.build(node, context)
      if (processor) processors.push(processor)
    }
  }

  // Build FROST section if a FROST node is present
  const hasFrostNode = orderedNodes.some(n => isFrostNodeData(n.data))
  let output: object = {}

  if (hasFrostNode) {
    const frostSection = buildFrostSection()
    processors.push(...frostSection.processors)
    output = frostSection.output
  }

  return {
    input,
    pipeline: {
      processors,
    },
    output,
  }
}

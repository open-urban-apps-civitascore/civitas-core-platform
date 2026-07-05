/**
 * Data-flow declarations per pipeline node type — the frontend mirror of the config-adapter's
 * stage vocabulary (`PayloadForm`, `CONVERTIBLE_TO_RECORDS`, the source/sink stage declarations).
 * Editor validation and the adapter's deploy-time derivation must give the same answer on the same
 * graph, so both sides declare the identical table and pin it with tests.
 *
 * Kept React-free (separate from `nodeRegistry.tsx`) so the pure validation service can consume it
 * without pulling in the registry's inspector panels, which themselves import validation helpers.
 */

import { isDataSourceNodeData, type PipelineNodeData } from '../_types/nodes'
import { PIPELINE_NODE_TYPES, type PipelineNodeType } from '../_types/pipeline'

/** The shape of the data payload flowing between pipeline nodes. */
export type PayloadForm = 'RAW_JSON' | 'STA_ENVELOPE' | 'RECORDS'

/**
 * Forms the engine's implicit ConvertRecord step turns into `RECORDS`. This is the single coercion
 * of the compatibility relation: a sink accepting `RECORDS` also accepts any of these, because the
 * flow builder inserts the convert structurally — it is never user-modelled.
 */
export const CONVERTIBLE_TO_RECORDS: ReadonlySet<PayloadForm> = new Set(['RAW_JSON', 'STA_ENVELOPE'])

export type NodeFlowRole = 'source' | 'transform' | 'sink' | 'trigger' | 'control'

/**
 * What a node type contributes to the data flow. Only wiring decides how these declarations are
 * combined — node existence on the canvas never does.
 */
export interface NodeFlowDeclaration {
  role: NodeFlowRole
  /**
   * The payload form the node emits (source/transform). `undefined` when it cannot be determined
   * from the node data (e.g. a source whose connector is unknown) — the caller must then skip the
   * check rather than guess.
   */
  output?: (data: PipelineNodeData) => PayloadForm | undefined
  /**
   * The payload forms the node consumes (sink/transform). `RECORDS` implicitly also admits the
   * `CONVERTIBLE_TO_RECORDS` forms. `mappedUpstream` reflects the wiring, never node existence.
   */
  acceptedInputs?: (ctx: { mappedUpstream: boolean }) => readonly PayloadForm[]
  /**
   * Whether the node's trigger port takes a cron schedule (source only). The port holds at most
   * one schedule. `undefined` when the connector is unknown and it cannot be verified either way.
   */
  acceptsSchedule?: (data: PipelineNodeData) => boolean | undefined
}

const DATA_SOURCE_OUTPUT_BY_CONNECTOR: Record<string, PayloadForm> = {
  MQTT: 'STA_ENVELOPE',
  SQL: 'RECORDS',
}

const dataSourceConnector = (data: PipelineNodeData): string | undefined =>
  isDataSourceNodeData(data) ? data.entityMetadata?.connector : undefined

export const NODE_FLOW_DECLARATIONS: Record<PipelineNodeType, NodeFlowDeclaration> = {
  [PIPELINE_NODE_TYPES.Start]: { role: 'control' },
  [PIPELINE_NODE_TYPES.End]: { role: 'control' },
  [PIPELINE_NODE_TYPES.Cron]: { role: 'trigger' },
  [PIPELINE_NODE_TYPES.DataSource]: {
    role: 'source',
    output: data => {
      const connector = dataSourceConnector(data)
      return connector === undefined ? undefined : DATA_SOURCE_OUTPUT_BY_CONNECTOR[connector]
    },
    acceptsSchedule: data => {
      // MQTT is push-based (it self-triggers on broker messages), so a cron there would only
      // throttle the drain, not the data. Cron belongs on a pull source (SQL). A connector this
      // table does not know cannot be verified either way.
      switch (dataSourceConnector(data)) {
        case 'MQTT':
          return false
        case 'SQL':
          return true
        default:
          return undefined
      }
    },
  },
  [PIPELINE_NODE_TYPES.Mapping]: {
    role: 'transform',
    output: () => 'RECORDS',
    acceptedInputs: () => ['RECORDS'],
  },
  [PIPELINE_NODE_TYPES.Frost]: {
    role: 'sink',
    // With a mapping upstream, the engine rebuilds the SensorThings envelope from the mapped
    // record — any record-convertible input works. Without one, the sink consumes the source's
    // envelope as-is, so passthrough demands the envelope itself.
    acceptedInputs: ctx => (ctx.mappedUpstream ? ['RECORDS'] : ['STA_ENVELOPE']),
  },
  [PIPELINE_NODE_TYPES.GeoPersistence]: {
    role: 'sink',
    acceptedInputs: () => ['RECORDS'],
  },
}

/** Whether the offered form can feed a consumer with the given accepted forms (incl. coercion). */
export const isFormAccepted = (offered: PayloadForm, accepted: readonly PayloadForm[]): boolean =>
  accepted.includes(offered) || (accepted.includes('RECORDS') && CONVERTIBLE_TO_RECORDS.has(offered))

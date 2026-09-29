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
 * The payload form the node emits. `undefined` when it cannot be determined from the node data
 * (e.g. a source whose connector is unknown) — the caller must then skip the check rather than
 * guess.
 */
type OutputFn = (data: PipelineNodeData) => PayloadForm | undefined

/**
 * The payload forms the node consumes. `RECORDS` implicitly also admits the
 * `CONVERTIBLE_TO_RECORDS` forms. `mappedUpstream` reflects the wiring, never node existence.
 */
type AcceptedInputsFn = (ctx: { mappedUpstream: boolean }) => readonly PayloadForm[]

export interface SourceFlowDeclaration {
  readonly role: 'source'
  readonly output: OutputFn
  /**
   * Whether the node's trigger port takes a cron schedule. The port holds at most one schedule.
   * `undefined` when the connector is unknown and it cannot be verified either way.
   */
  readonly acceptsSchedule: (data: PipelineNodeData) => boolean | undefined
}

export interface TransformFlowDeclaration {
  readonly role: 'transform'
  readonly output: OutputFn
  readonly acceptedInputs: AcceptedInputsFn
}

export interface SinkFlowDeclaration {
  readonly role: 'sink'
  readonly acceptedInputs: AcceptedInputsFn
}

export interface PassiveFlowDeclaration {
  readonly role: 'trigger' | 'control'
}

/**
 * What a node type contributes to the data flow — a union discriminated by role, so a declaration
 * carrying a capability its role does not have (e.g. a sink with an output) is unrepresentable.
 * Only wiring decides how these declarations are combined — node existence on the canvas never
 * does.
 */
export type NodeFlowDeclaration =
  | SourceFlowDeclaration
  | TransformFlowDeclaration
  | SinkFlowDeclaration
  | PassiveFlowDeclaration

const DATA_SOURCE_OUTPUT_BY_CONNECTOR: Record<string, PayloadForm> = {
  MQTT: 'STA_ENVELOPE',
  SQL: 'RECORDS',
}

const dataSourceConnector = (data: PipelineNodeData): string | undefined =>
  isDataSourceNodeData(data) ? data.entityMetadata?.connector : undefined

export const NODE_FLOW_DECLARATIONS: Readonly<Record<PipelineNodeType, NodeFlowDeclaration>> = {
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
    // Records with a mapping and without one: the sink writes every record through its port, and
    // a record without a mapping must already have the structure of that port. A raw MQTT message
    // is convertible, so the engine puts a ConvertRecord in front of the sink.
    acceptedInputs: () => ['RECORDS'],
  },
  [PIPELINE_NODE_TYPES.GeoPersistence]: {
    role: 'sink',
    acceptedInputs: () => ['RECORDS'],
  },
}

/** Whether the offered form can feed a consumer with the given accepted forms (incl. coercion). */
export const isFormAccepted = (offered: PayloadForm, accepted: readonly PayloadForm[]): boolean =>
  accepted.includes(offered) || (accepted.includes('RECORDS') && CONVERTIBLE_TO_RECORDS.has(offered))

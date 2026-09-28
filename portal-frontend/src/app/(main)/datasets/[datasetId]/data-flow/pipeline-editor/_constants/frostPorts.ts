/**
 * The ports a FROST sink offers.
 *
 * The set of ports is not declared here. It is read from `FrostDataSinkSchema`, which the generator
 * emits from the CORE DataSink schema — the single source of truth the backend validates against
 * too. A port added there appears in the editor without a change in this file; what this file adds
 * is the grouping by logic class, which decides the order of the list.
 *
 * The prose for each port — what it writes, what it costs, which references it expects — lives in
 * the message catalogue like every other label in the editor.
 */

import { FrostDataSinkSchema } from '@/generated/core/datasink'

/** The port values the schema publishes. */
export const FROST_SINK_PORTS = FrostDataSinkSchema.shape.port.unwrap().options

export type FrostSinkPort = (typeof FROST_SINK_PORTS)[number]

/** The logic classes the list groups by, in the order the groups appear. */
export const FROST_PORT_LOGIC = ['ownKey', 'parentReference', 'composite'] as const

export type FrostPortLogic = (typeof FROST_PORT_LOGIC)[number]

/**
 * Which logic class a port belongs to. A port the schema publishes but this map does not know falls
 * into the last group rather than disappearing from the list: an unselectable port would look like
 * a platform that cannot write it.
 */
const LOGIC_BY_PORT: Partial<Record<FrostSinkPort, FrostPortLogic>> = {
  Things: 'ownKey',
  Observations: 'parentReference',
  ThingTree: 'composite',
}

export const logicOf = (port: FrostSinkPort): FrostPortLogic => LOGIC_BY_PORT[port] ?? 'composite'

/** The ports of one logic class, in the order the schema declares them. */
export const portsOfLogic = (logic: FrostPortLogic): FrostSinkPort[] =>
  FROST_SINK_PORTS.filter(port => logicOf(port) === logic)

/** Whether a stored value names a port the schema publishes. */
export const isFrostSinkPort = (value: unknown): value is FrostSinkPort =>
  typeof value === 'string' && (FROST_SINK_PORTS as readonly string[]).includes(value)

import type { LucideIcon } from 'lucide-react'

import type { PortStatus } from './PortHandle'

export type PortType = 'scalar' | 'geometry' | 'array' | 'object'

export interface PortDef {
  id: string
  label: string
  type: PortType
  /** Optional primitive subtype (e.g. str/int/date) used for cast detection. */
  dataType?: string
}

export type ConfigControl = 'text' | 'number' | 'select'

export interface ConfigOption {
  label: string
  value: string
}

export interface ConfigField {
  key: string
  label: string
  control: ConfigControl
  default?: string | number
  placeholder?: string
  options?: ConfigOption[]
}

/** A single, definition-driven node type. Renders its own node, inspector and palette item. */
export interface TransformDef {
  type: string
  category: string
  label: string
  description?: string
  icon?: LucideIcon
  inputs: PortDef[]
  outputs: PortDef[]
  config: ConfigField[]
}

/** Data carried by a generic transform node. Ports default to the def's ports. */
export interface TransformNodeData extends Record<string, unknown> {
  defType: string
  label?: string
  config: Record<string, unknown>
  inputs?: PortDef[]
  outputs?: PortDef[]
  /** portId → status; injected for display only, never persisted. */
  portStatus?: Record<string, PortStatus>
}

export interface NodeRegistry<T extends TransformDef = TransformDef> {
  list: T[]
  byType: Record<string, T>
  categories: string[]
}

export const buildRegistry = <T extends TransformDef>(defs: T[]): NodeRegistry<T> => ({
  list: defs,
  byType: Object.fromEntries(defs.map(d => [d.type, d])),
  categories: [...new Set(defs.map(d => d.category))],
})

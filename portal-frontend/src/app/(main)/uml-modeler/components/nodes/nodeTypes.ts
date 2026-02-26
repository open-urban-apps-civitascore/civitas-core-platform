import type { NodeTypes } from '@xyflow/react'

import { AbstractClassNode } from './AbstractClassNode'
import { ClassNode } from './ClassNode'
import { EnumNode } from './EnumNode'
import { InterfaceNode } from './InterfaceNode'

// Node types registry for ReactFlow
export const nodeTypes: NodeTypes = {
  class: ClassNode,
  interface: InterfaceNode,
  abstractClass: AbstractClassNode,
  enumeration: EnumNode,
}

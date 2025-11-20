import type { EdgeTypes } from '@xyflow/react'

import { AggregationEdge } from './AggregationEdge'
import { AssociationEdge } from './AssociationEdge'
import { CompositionEdge } from './CompositionEdge'
import { DependencyEdge } from './DependencyEdge'
import { InheritanceEdge } from './InheritanceEdge'
import { RealizationEdge } from './RealizationEdge'

/**
 * UML Edge Types Registry
 * Maps UML relationship types to their corresponding edge components
 * Used by ReactFlow to render the appropriate edge type
 */
export const edgeTypes: EdgeTypes = {
  association: AssociationEdge,
  aggregation: AggregationEdge,
  composition: CompositionEdge,
  inheritance: InheritanceEdge,
  realization: RealizationEdge,
  dependency: DependencyEdge,
}

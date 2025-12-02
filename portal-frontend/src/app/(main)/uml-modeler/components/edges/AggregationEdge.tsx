'use client'

import type { EdgeProps } from '@xyflow/react'
import React from 'react'

import type { UMLEdge } from '../../types/diagram'
import { BaseUMLEdge } from './BaseUMLEdge'

/**
 * UML Aggregation Edge Component
 * Represents "has-a" relationships with weak ownership
 * Visual: Solid line with hollow diamond
 * UML Rule: Parent can exist independently of child
 */
export const AggregationEdge: React.FC<EdgeProps<UMLEdge>> = props => {
  return <BaseUMLEdge {...props} relationshipType="aggregation" markerEnd="aggregation" />
}

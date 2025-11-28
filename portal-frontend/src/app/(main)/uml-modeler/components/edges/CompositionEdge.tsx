'use client'

import type { EdgeProps } from '@xyflow/react'
import React from 'react'

import type { UMLEdge } from '../../types/diagram'
import { BaseUMLEdge } from './BaseUMLEdge'

/**
 * UML Composition Edge Component
 * Represents "part-of" relationships with strong ownership
 * Visual: Solid line with filled diamond
 * UML Rule: Child cannot exist without parent (strong ownership)
 */
export const CompositionEdge: React.FC<EdgeProps<UMLEdge>> = props => {
  return <BaseUMLEdge {...props} relationshipType="composition" markerEnd="composition" />
}

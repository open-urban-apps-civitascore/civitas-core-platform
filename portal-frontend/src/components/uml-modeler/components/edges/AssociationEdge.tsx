'use client'

import type { EdgeProps } from '@xyflow/react'
import React from 'react'

import type { UMLEdge } from '../../types/diagram'
import { BaseUMLEdge } from './BaseUMLEdge'

/**
 * UML Association Edge Component
 * Represents basic associations between elements
 * Visual: Solid line with arrow
 * UML Rule: Most permissive - any element to any element
 */
export const AssociationEdge: React.FC<EdgeProps<UMLEdge>> = props => {
  return <BaseUMLEdge {...props} relationshipType="association" markerEnd="association" />
}

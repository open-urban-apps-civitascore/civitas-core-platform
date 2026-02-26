'use client'

import type { EdgeProps } from '@xyflow/react'
import React from 'react'

import type { UMLEdge } from '../../types/diagram'
import { BaseUMLEdge } from './BaseUMLEdge'

/**
 * UML Inheritance Edge Component
 * Represents generalization relationships between classes
 * Visual: Solid line with hollow triangle arrow
 * UML Rule: class → class/abstractClass only
 */
export const InheritanceEdge: React.FC<EdgeProps<UMLEdge>> = props => {
  return <BaseUMLEdge {...props} relationshipType="inheritance" markerEnd="inheritance" />
}

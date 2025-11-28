'use client'

import type { EdgeProps } from '@xyflow/react'
import React from 'react'

import type { UMLEdge } from '../../types/diagram'
import { BaseUMLEdge } from './BaseUMLEdge'

/**
 * UML Dependency Edge Component
 * Represents "uses" relationships (temporary dependencies)
 * Visual: Dashed line with arrow
 * UML Rule: One element uses another temporarily
 */
export const DependencyEdge: React.FC<EdgeProps<UMLEdge>> = props => {
  return <BaseUMLEdge {...props} relationshipType="dependency" markerEnd="dependency" />
}

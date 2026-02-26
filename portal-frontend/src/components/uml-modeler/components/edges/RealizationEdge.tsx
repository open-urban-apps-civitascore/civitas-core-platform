'use client'

import type { EdgeProps } from '@xyflow/react'
import React from 'react'

import type { UMLEdge } from '../../types/diagram'
import { BaseUMLEdge } from './BaseUMLEdge'

/**
 * UML Realization Edge Component
 * Represents interface implementation relationships
 * Visual: Dashed line with hollow triangle arrow
 * UML Rule: class → interface only
 */
export const RealizationEdge: React.FC<EdgeProps<UMLEdge>> = props => {
  return <BaseUMLEdge {...props} relationshipType="realization" markerEnd="realization" />
}

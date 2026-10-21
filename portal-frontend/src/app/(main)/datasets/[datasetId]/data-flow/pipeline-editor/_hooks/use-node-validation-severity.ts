'use client'

import { createContext, useContext } from 'react'

import type { ValidationSeverity } from '../_components/nodes/base/BasePipelineNode'

export const NodeValidationContext = createContext<(nodeId: string) => ValidationSeverity>(() => 'none')

export const useNodeValidationSeverity = (nodeId: string) => useContext(NodeValidationContext)(nodeId)

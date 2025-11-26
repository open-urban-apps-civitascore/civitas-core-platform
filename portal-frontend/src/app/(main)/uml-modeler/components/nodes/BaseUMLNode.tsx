'use client'

import { Handle, Position } from '@xyflow/react'
import type { CSSProperties, ReactNode } from 'react'

import { NODE_DIMENSIONS, UML_COLORS } from '../../constants/umlTypes'
import type { UMLElementType } from '../../types/uml'

interface BaseUMLNodeProps {
  elementType: UMLElementType
  isSelected: boolean
  stereotype?: string
  name: string
  children: ReactNode
  className?: string
}

// Get styling for UML node based on type and selection state
export const getNodeStyle = (elementType: UMLElementType, isSelected: boolean): CSSProperties => ({
  background: UML_COLORS[elementType].background,
  border: `2px solid ${isSelected ? UML_COLORS.selected.border : UML_COLORS[elementType].border}`,
  borderRadius: '4px',
  minWidth: NODE_DIMENSIONS.minWidth,
  fontFamily: 'monospace',
  fontSize: '12px',
  color: UML_COLORS[elementType].text,
  boxShadow: isSelected ? UML_COLORS.selected.shadow : 'none',
  transition: 'box-shadow 0.2s ease, border-color 0.2s ease',
  display: 'flex',
  flexDirection: 'column',
  minHeight: '80px', // Match ReactFlow's minimum height
  height: '100%',
})

// Base UML Node component with standard UML structure
export const BaseUMLNode: React.FC<BaseUMLNodeProps> = ({
  elementType,
  isSelected,
  stereotype,
  name,
  children,
  className = '',
}) => {
  return (
    <div className={`uml-node ${className}`} style={getNodeStyle(elementType, isSelected)}>
      {/* Universal connection handles - invisible for clean UML appearance */}

      <Handle type="source" position={Position.Top} id="universal" />
      <Handle type="target" position={Position.Top} id="universal" />

      {/* Header section with stereotype and name */}
      <div
        className="node-header"
        style={{
          height: NODE_DIMENSIONS.headerHeight,
          padding: `${NODE_DIMENSIONS.sectionPadding}px`,
          borderBottom: `${NODE_DIMENSIONS.separatorHeight}px solid ${UML_COLORS[elementType].border}`,
          textAlign: 'center',
          fontWeight: 'bold',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          cursor: 'grab',
        }}
      >
        {stereotype && <div style={{ fontSize: '10px', marginBottom: '2px', fontStyle: 'italic' }}>{stereotype}</div>}
        <div style={{ fontStyle: elementType === 'interface' ? 'italic' : 'normal' }}>{name}</div>
      </div>

      {/* Content sections */}
      {children}
    </div>
  )
}

// Helper component for UML node sections (attributes, operations, literals)
export const NodeSection: React.FC<{
  children: ReactNode
  isEmpty?: boolean
}> = ({ children, isEmpty = false }) => {
  if (isEmpty) return null

  return (
    <div
      style={{
        padding: `${NODE_DIMENSIONS.sectionPadding}px`,
        borderTop: `${NODE_DIMENSIONS.separatorHeight}px solid #ccc`,
        minHeight: NODE_DIMENSIONS.lineHeight,
        flex: 1, // Fill remaining space
      }}
    >
      {children}
    </div>
  )
}

// Helper component for individual lines in UML sections
export const NodeLine: React.FC<{
  children: ReactNode
  isAbstract?: boolean
  className?: string
}> = ({ children, isAbstract = false, className = '' }) => {
  return (
    <div
      className={`node-line ${className}`}
      style={{
        lineHeight: `${NODE_DIMENSIONS.lineHeight}px`,
        fontStyle: isAbstract ? 'italic' : 'normal',
        padding: '1px 0',
      }}
    >
      {children}
    </div>
  )
}

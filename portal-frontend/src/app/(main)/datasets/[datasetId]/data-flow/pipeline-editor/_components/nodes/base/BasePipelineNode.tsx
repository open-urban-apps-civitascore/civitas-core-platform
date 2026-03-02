'use client'

/**
 * BasePipelineNode Component
 *
 * Base component for all pipeline nodes providing:
 * - Left (input) and Right (output) connection handles
 * - Configured/unconfigured visual states
 * - Selection highlighting
 * - Category-based coloring
 *
 * Flow direction: Left → Right (horizontal)
 *
 */

import { Handle, Position } from '@xyflow/react'
import type { LucideIcon } from 'lucide-react'
import { useTranslations } from 'next-intl'
import type { CSSProperties, ReactNode } from 'react'

import type { NodeCategory } from '../../../_constants/nodeCategories'
import {
  CATEGORY_COLORS,
  NODE_DIMENSIONS,
  PIPELINE_COLORS,
  UNCONFIGURED_NODE_STYLE,
} from '../../../_constants/pipelineStyles'

// ============================================================================
// Types
// ============================================================================

/** Validation severity levels for visual indicators */
export type ValidationSeverity = 'error' | 'warning' | 'none'

export interface BasePipelineNodeProps {
  /** Node category for color theming */
  category: NodeCategory
  /** Whether the node is configured */
  isConfigured: boolean
  /** Whether the node is selected */
  isSelected: boolean
  /** Main label text */
  label: string
  /** Optional sublabel (e.g., selected entity name) */
  sublabel?: string
  /** Lucide icon component */
  icon?: LucideIcon
  /** Show left (input) handle */
  hasLeftHandle?: boolean
  /** Show right (output) handle */
  hasRightHandle?: boolean
  /** Additional content */
  children?: ReactNode
  /** Additional CSS class */
  className?: string
  /** Validation severity for visual indicator (Phase 5) */
  validationSeverity?: ValidationSeverity
}

/** Validation indicator colors */
const VALIDATION_COLORS = {
  error: PIPELINE_COLORS.validationError,
  warning: PIPELINE_COLORS.validationWarning,
  none: 'transparent',
}

// ============================================================================
// Styles
// ============================================================================

/**
 * Get node container styles based on state
 *
 * Styling rules:
 * - Configured + Selected: Category color border + glow
 * - Configured + NOT Selected: Neutral grey border, light grey background
 * - NOT Configured: Solid grey border, darker grey background
 */
export const getNodeContainerStyle = (
  category: NodeCategory,
  isConfigured: boolean,
  isSelected: boolean,
): CSSProperties => {
  const categoryColor = CATEGORY_COLORS[category]

  // Determine border color based on state
  let borderColor: string
  if (isSelected) {
    // Selected: use category color
    borderColor = categoryColor.primary
  } else if (isConfigured) {
    // Configured but not selected: neutral grey border
    borderColor = PIPELINE_COLORS.nodeConfiguredBorderNeutral
  } else {
    // Not configured: muted border
    borderColor = PIPELINE_COLORS.nodeUnconfiguredBorder
  }

  return {
    minWidth: NODE_DIMENSIONS.activityNode.minWidth,
    minHeight: NODE_DIMENSIONS.activityNode.minHeight,
    maxWidth: NODE_DIMENSIONS.activityNode.maxWidth,
    padding: NODE_DIMENSIONS.activityNode.padding,
    borderRadius: NODE_DIMENSIONS.activityNode.borderRadius,
    backgroundColor: isConfigured ? PIPELINE_COLORS.nodeConfiguredBg : PIPELINE_COLORS.nodeUnconfiguredBg,
    border: `${isConfigured ? 2 : UNCONFIGURED_NODE_STYLE.borderWidth}px ${isConfigured ? 'solid' : UNCONFIGURED_NODE_STYLE.borderStyle} ${borderColor}`,
    opacity: isConfigured ? 1 : UNCONFIGURED_NODE_STYLE.opacity,
    display: 'flex',
    alignItems: 'center',
    gap: '8px',
    cursor: 'grab',
    transition: 'border-color 0.2s ease, box-shadow 0.2s ease',
    boxShadow: isSelected ? `0 0 0 2px ${categoryColor.primary}40` : 'none',
  }
}

/**
 * Get handle styles - using solid colors for visibility
 */
export const getHandleStyle = (isSelected: boolean): CSSProperties => ({
  width: NODE_DIMENSIONS.handle.size,
  height: NODE_DIMENSIONS.handle.size,
  backgroundColor: isSelected ? '#3b82f6' : '#666666',
  border: '2px solid #ffffff',
})

// ============================================================================
// Component
// ============================================================================

/**
 * Base pipeline node component.
 * Provides the foundational structure for activity-style nodes.
 *
 */
export const BasePipelineNode: React.FC<BasePipelineNodeProps> = ({
  category,
  isConfigured,
  isSelected,
  label,
  sublabel,
  icon,
  hasLeftHandle = true,
  hasRightHandle = true,
  children,
  className = '',
  validationSeverity = 'none',
}) => {
  const t = useTranslations('datastructures.pipelineEditor')
  const containerStyle = getNodeContainerStyle(category, isConfigured, isSelected)
  const handleStyle = getHandleStyle(isSelected)
  const categoryColor = CATEGORY_COLORS[category]
  // Alias for JSX rendering (React components must be PascalCase)
  const NodeIcon = icon

  // Apply validation border color override
  const validationBorderColor = VALIDATION_COLORS[validationSeverity]
  const finalContainerStyle: CSSProperties = {
    ...containerStyle,
    position: 'relative', // Required for validation badge positioning
    ...(validationSeverity !== 'none' && {
      border: `2px solid ${validationBorderColor}`,
      boxShadow: `0 0 0 2px ${validationBorderColor}30`,
    }),
  }

  return (
    <div className={`pipeline-node ${className}`} style={finalContainerStyle}>
      {/* Validation Indicator Badge */}
      {validationSeverity !== 'none' && (
        <div
          style={{
            position: 'absolute',
            top: -6,
            right: -6,
            width: 14,
            height: 14,
            borderRadius: '50%',
            backgroundColor: validationBorderColor,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '10px',
            color: 'white',
            fontWeight: 'bold',
            zIndex: 10,
          }}
        >
          {validationSeverity === 'error' ? '!' : '⚠'}
        </div>
      )}
      {/* Left Handle (Input) */}
      {hasLeftHandle && <Handle type="target" position={Position.Left} id="input" style={handleStyle} />}

      {/* Icon */}
      {NodeIcon && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: 28,
            height: 28,
            borderRadius: 4,
            backgroundColor:
              isConfigured && isSelected
                ? categoryColor.secondary
                : isConfigured
                  ? PIPELINE_COLORS.nodeConfiguredIconBg
                  : 'transparent',
            color:
              isConfigured && isSelected
                ? categoryColor.primary
                : isConfigured
                  ? PIPELINE_COLORS.nodeConfiguredIconColor
                  : PIPELINE_COLORS.nodeUnconfiguredBorder,
            flexShrink: 0,
          }}
        >
          <NodeIcon size={18} strokeWidth={2} />
        </div>
      )}

      {/* Label Content */}
      <div style={{ flex: 1, overflow: 'hidden' }}>
        <div
          style={{
            fontWeight: 500,
            fontSize: '13px',
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            color: isConfigured ? 'hsl(var(--foreground))' : 'hsl(var(--muted-foreground))',
          }}
        >
          {label}
        </div>
        {sublabel && (
          <div
            style={{
              fontSize: '11px',
              color: 'hsl(var(--muted-foreground))',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              marginTop: '2px',
            }}
          >
            {sublabel}
          </div>
        )}
        {!isConfigured && !sublabel && (
          <div
            style={{
              fontSize: '11px',
              color: 'hsl(var(--muted-foreground))',
              fontStyle: 'italic',
              marginTop: '2px',
            }}
          >
            {t('nodeLabels.notConfigured')}
          </div>
        )}
      </div>

      {/* Additional Content */}
      {children}

      {/* Right Handle (Output) */}
      {hasRightHandle && <Handle type="source" position={Position.Right} id="output" style={handleStyle} />}
    </div>
  )
}

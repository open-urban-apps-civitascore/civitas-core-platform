'use client'

/**
 * InspectorHeader Component
 *
 * Displays the header for the inspector panel with node type icon,
 * label, and configuration status badge.
 *
 */

import { Clock, Database, Globe, Play, Reply, Snowflake, Square, Workflow } from 'lucide-react'
import type { ReactNode } from 'react'

import type { PipelineNodeType } from '../../../_types/pipeline'

// ============================================================================
// Props
// ============================================================================

interface InspectorHeaderProps {
  nodeType: PipelineNodeType
  label: string
  isConfigured: boolean
}

// ============================================================================
// Icon Mapping
// ============================================================================

const NODE_TYPE_ICONS: Record<PipelineNodeType, ReactNode> = {
  start: <Play className="h-4 w-4" />,
  end: <Square className="h-4 w-4" />,
  dataSource: <Database className="h-4 w-4" />,
  apiRequest: <Globe className="h-4 w-4" />,
  apiResponse: <Reply className="h-4 w-4" />,
  cron: <Clock className="h-4 w-4" />,
  frost: <Snowflake className="h-4 w-4" />,
  mapping: <Workflow className="h-4 w-4" />,
}

const NODE_TYPE_LABELS: Record<PipelineNodeType, string> = {
  start: 'Start',
  end: 'End',
  dataSource: 'DataSource',
  apiRequest: 'API Request',
  apiResponse: 'API Response',
  cron: 'CRON Trigger',
  frost: 'FROST Storage',
  mapping: 'Mapping',
}

// ============================================================================
// Component
// ============================================================================

export const InspectorHeader: React.FC<InspectorHeaderProps> = ({ nodeType, label, isConfigured }) => {
  const icon = NODE_TYPE_ICONS[nodeType]
  const typeLabel = NODE_TYPE_LABELS[nodeType]

  return (
    <div className="flex items-center gap-3 border-b border-border p-3">
      {/* Icon */}
      <div className="flex h-8 w-8 items-center justify-center rounded-md bg-muted text-muted-foreground">{icon}</div>

      {/* Label & Type */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <span className="truncate text-sm font-medium text-foreground">{label}</span>
        <span className="text-xs text-muted-foreground">{typeLabel}</span>
      </div>

      {/* Configuration Status Badge */}
      <div
        className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium ${
          isConfigured
            ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
            : 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400'
        }`}
      >
        {isConfigured ? 'Configured' : 'Not configured'}
      </div>
    </div>
  )
}

'use client'

/**
 * ValidationIssueItem Component
 *
 * Displays a single validation error or warning item.
 * Used by ValidationPanel to render lists of issues.
 *
 */

import { AlertCircle, AlertTriangle } from 'lucide-react'

// ============================================================================
// Types
// ============================================================================

interface ValidationIssueItemProps {
  /** The validation message to display */
  message: string
  /** The severity of the issue */
  severity: 'error' | 'warning'
}

// ============================================================================
// Component
// ============================================================================

/**
 * Renders a single validation issue with appropriate icon and styling.
 *
 */
export const ValidationIssueItem: React.FC<ValidationIssueItemProps> = ({ message, severity }) => {
  const isError = severity === 'error'

  return (
    <div
      className={`flex items-start gap-2 rounded-md border px-3 py-2 ${
        isError ? 'border-red-200 bg-red-50' : 'border-orange-200 bg-orange-50'
      }`}
    >
      {isError ? (
        <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-500" />
      ) : (
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-orange-500" />
      )}
      <span className={`text-sm ${isError ? 'text-red-700' : 'text-orange-700'}`}>{message}</span>
    </div>
  )
}

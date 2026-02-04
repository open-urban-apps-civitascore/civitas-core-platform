'use client'

/**
 * ValidationPanel Component
 *
 * Displays pipeline validation results in the inspector panel.
 * Shows errors and warnings with appropriate styling.
 *
 */

import { CheckCircle2, ClipboardList } from 'lucide-react'

import type { ValidationResultWithNodeStatus } from '../../../_services/validationService'
import { ValidationIssueItem } from './ValidationIssueItem'

// ============================================================================
// Types
// ============================================================================

interface ValidationPanelProps {
  /** The validation result to display */
  validationResult: ValidationResultWithNodeStatus
}

// ============================================================================
// Component
// ============================================================================

/**
 * Renders a complete validation results panel.
 * Shows success state, errors list, and warnings list.
 *
 */
export const ValidationPanel: React.FC<ValidationPanelProps> = ({ validationResult }) => {
  const { isValid, errors, warnings } = validationResult
  const errorCount = errors.length
  const warningCount = warnings.length

  return (
    <div className="flex h-full flex-col">
      {/* Header */}
      <div className="flex items-center gap-2 border-b border-border px-3 py-2">
        {isValid ? (
          <CheckCircle2 className="h-4 w-4 text-green-500" />
        ) : (
          <ClipboardList className="h-4 w-4 text-muted-foreground" />
        )}
        <h3 className="text-sm font-medium text-foreground">Validation Results</h3>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-4">
        {isValid ? (
          // Success State
          <div className="flex flex-col items-center justify-center py-8 text-center">
            <CheckCircle2 className="mb-3 h-12 w-12 text-green-500" />
            <p className="text-lg font-medium text-green-700">Pipeline is valid</p>
            <p className="mt-1 text-sm text-muted-foreground">
              All validation checks passed. You can save the pipeline.
            </p>
          </div>
        ) : (
          // Errors and Warnings
          <div className="space-y-6">
            {/* Errors Section */}
            {errorCount > 0 && (
              <div>
                <h4 className="mb-2 flex items-center gap-1.5 text-sm font-medium text-red-600">
                  <span className="text-xs">❌</span>
                  <span>Errors ({errorCount})</span>
                </h4>
                <div className="space-y-2">
                  {errors.map(error => (
                    <ValidationIssueItem key={error.id} message={error.message} severity="error" />
                  ))}
                </div>
              </div>
            )}

            {/* Warnings Section */}
            {warningCount > 0 && (
              <div>
                <h4 className="mb-2 flex items-center gap-1.5 text-sm font-medium text-orange-600">
                  <span className="text-xs">⚠️</span>
                  <span>Warnings ({warningCount})</span>
                </h4>
                <div className="space-y-2">
                  {warnings.map(warning => (
                    <ValidationIssueItem key={warning.id} message={warning.message} severity="warning" />
                  ))}
                </div>
              </div>
            )}

            {/* Summary footer */}
            {!isValid && (
              <div className="mt-4 rounded-md border border-muted bg-muted/30 p-3">
                <p className="text-xs text-muted-foreground">
                  {errorCount > 0
                    ? 'Fix the errors above before saving the pipeline.'
                    : 'Pipeline has warnings but can be saved.'}
                </p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

'use client'

/**
 * CronPanel Component
 *
 * Inspector panel for CRON trigger nodes.
 * Includes cron expression input and link to cronmaker.com.
 *
 */

import { ExternalLink, Info } from 'lucide-react'

import { Input } from '@/components/ui/input'

import { isValidQuartzCron } from '../../../_services/validationService'
import type { CronNodeData } from '../../../_types/nodes'

interface CronPanelProps {
  data: CronNodeData
  onUpdate: (data: Partial<CronNodeData>) => void
}

export const CronPanel: React.FC<CronPanelProps> = ({ data, onUpdate }) => {
  const handleExpressionChange = (expression: string) => {
    const trimmed = expression.trim()
    onUpdate({
      cronExpression: expression,
      configured: trimmed !== '' && isValidQuartzCron(trimmed),
    })
  }

  const hasValidationError = !!data.cronExpression?.trim() && !isValidQuartzCron(data.cronExpression.trim())

  return (
    <div className="space-y-4 p-4">
      <div className="space-y-2">
        <label className="text-sm font-medium text-foreground">CRON Expression</label>
        <Input
          value={data.cronExpression || ''}
          onChange={e => handleExpressionChange(e.target.value)}
          placeholder="0,30 */2 * * * *"
          className="font-mono"
        />
        {hasValidationError && (
          <p className="text-xs text-destructive">
            Invalid cron expression. Must be 6-field Quartz syntax (seconds minutes hours day-of-month month
            day-of-week).
          </p>
        )}
      </div>

      <div className="flex items-start gap-2 rounded-md bg-blue-50 p-3 dark:bg-blue-950/30">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" />
        <div className="space-y-2 text-sm">
          <p className="text-blue-800 dark:text-blue-200">
            A CRON expression defines when the pipeline should run automatically.
          </p>
          <a
            href="https://uptimerobot.com/free-tools/cron-expression-generator/?input=0%2C30+*%2F2+*+*+*+*&format=quartz"
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 text-blue-600 hover:underline dark:text-blue-400"
          >
            Learn about CRON expressions
            <ExternalLink className="h-3 w-3" />
          </a>
        </div>
      </div>

      {data.cronExpression && (
        <div className="space-y-2">
          <h4 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Current Expression</h4>
          <code className="block rounded-md bg-muted p-2 font-mono text-sm">{data.cronExpression}</code>
        </div>
      )}
    </div>
  )
}

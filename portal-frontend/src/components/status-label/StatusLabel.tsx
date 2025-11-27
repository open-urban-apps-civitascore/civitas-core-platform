import { Check, Minus } from 'lucide-react'
import React, { HTMLAttributes } from 'react'

import { cn } from '@/lib/utils'

interface StatusLabelProps extends HTMLAttributes<HTMLDivElement> {
  isChecked: boolean
  testId?: string
}
export const StatusLabel = (props: StatusLabelProps) => {
  const { isChecked, testId, className } = props
  return (
    <div
      data-testid={testId}
      className={cn(
        `flex justify-center items-center w-9 h-9 bg-status-label rounded-md ${isChecked ? 'bg-status-label' : 'bg-secondary'}`,
        className,
      )}
    >
      {isChecked ? <Check color="var(--primary)" width={16} /> : <Minus color="var(--muted-foreground)" width={16} />}
    </div>
  )
}

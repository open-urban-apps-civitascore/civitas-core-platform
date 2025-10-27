import { cn } from '@/lib/utils'
import { Check, Minus } from 'lucide-react'
import React, { HTMLAttributes } from 'react'

interface StatusLabelProps extends HTMLAttributes<HTMLDivElement> {
  isChecked: boolean
}
export const StatusLabel = (props: StatusLabelProps) => {
  const { isChecked, className } = props
  return (
    <div
      className={cn(
        `flex justify-center items-center w-9 h-9 bg-status-label rounded-md ${isChecked ? 'bg-status-label' : 'bg-secondary'}`,
        className,
      )}
    >
      {isChecked ? <Check color="var(--primary)" width={16} /> : <Minus color="var(--muted-foreground)" width={16} />}
    </div>
  )
}

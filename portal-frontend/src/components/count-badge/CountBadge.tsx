import { cn } from '@/lib/utils'

interface CountBadgeProps {
  count: number
  className?: string
  testId?: string
}

export const CountBadge = (props: CountBadgeProps) => {
  const { count, className, testId = 'countBadge' } = props

  return (
    <span
      data-testid={testId}
      className={cn(
        'inline-flex items-center justify-center rounded-full bg-primary/10 px-2 py-0.5 font-medium text-primary',
        className,
      )}
    >
      {count}
    </span>
  )
}

'use client'
import '@xyflow/react/dist/style.css'

import { cn } from '@/lib/utils'

import { MultiSessionLayout } from './components/layout/MultiSessionLayout'
import { ReadOnlyProvider } from './hooks/use-read-only'

interface UmlModelerProps {
  className?: string
  isReadOnly?: boolean
}

export const UmlModeler = (props: UmlModelerProps) => {
  const { className, isReadOnly = false } = props
  return (
    <div className={cn('flex h-full w-full flex-1 flex-col gap-4 p-4', className)}>
      <div className="h-full w-full rounded-xl border bg-background overflow-hidden">
        <ReadOnlyProvider isReadOnly={isReadOnly}>
          <MultiSessionLayout className="rounded-xl" />
        </ReadOnlyProvider>
      </div>
    </div>
  )
}

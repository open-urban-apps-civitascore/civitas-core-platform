'use client'
import '@xyflow/react/dist/style.css'

import { JSX } from 'react'

import { cn } from '@/lib/utils'

import { MultiSessionLayout } from './components/layout/MultiSessionLayout'
import { ReadOnlyProvider } from './hooks/use-read-only'
import { UseMultiSessionReturn } from './types/session'

interface UmlModelerProps {
  className?: string
  isReadOnly?: boolean
  isMultiSessionMode?: boolean
  modelSessionManager?: UseMultiSessionReturn
  canExportModel?: boolean
  placeHolder?: JSX.Element
  onImportFromDatastructure?: () => void
}

export const UmlModeler = (props: UmlModelerProps) => {
  const {
    className,
    isReadOnly = false,
    isMultiSessionMode = true,
    modelSessionManager,
    canExportModel = true,
    placeHolder,
    onImportFromDatastructure,
  } = props
  return (
    <div data-testid="umlModeler" className={cn('flex h-full w-full flex-1 flex-col gap-4', className)}>
      <div className="h-full w-full rounded-xl border bg-background overflow-hidden">
        <ReadOnlyProvider isReadOnly={isReadOnly}>
          <MultiSessionLayout
            className="rounded-xl"
            externalSessionManager={modelSessionManager}
            isMultiSessionMode={isMultiSessionMode}
            canExportModel={canExportModel}
            placeHolder={placeHolder}
            onImportFromDatastructure={onImportFromDatastructure}
          />
        </ReadOnlyProvider>
      </div>
    </div>
  )
}

'use client'

import type { ReactNode } from 'react'

import { cn } from '@/lib/utils'

interface EditorLayoutProps {
  palette: ReactNode
  inspector: ReactNode
  toolbar?: ReactNode
  children: ReactNode
  className?: string
}

/** Generic 3-panel editor shell: palette (left) · canvas (center) · inspector (right). */
export const EditorLayout = ({ palette, inspector, toolbar, children, className }: EditorLayoutProps) => (
  <div className={cn('flex h-full w-full flex-col overflow-hidden bg-background', className)}>
    {toolbar && <div className="flex-shrink-0 border-b border-border">{toolbar}</div>}
    <div className="flex flex-1 overflow-hidden">
      <div className="flex-shrink-0 border-r border-border">{palette}</div>
      <div className="relative min-w-0 flex-1">{children}</div>
      {inspector}
    </div>
  </div>
)

'use client'

import type { MouseEvent as ReactMouseEvent, ReactNode } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'

import { cn } from '@/lib/utils'

interface InspectorShellProps {
  title: string
  header?: ReactNode
  emptyMessage?: string
  isEmpty?: boolean
  children?: ReactNode
  initialWidth?: number
  minWidth?: number
  maxWidth?: number
  className?: string
}

/** Resizable right panel with header and empty state. No domain knowledge. */
export const InspectorShell = ({
  title,
  header,
  emptyMessage = 'Nothing selected',
  isEmpty = false,
  children,
  initialWidth = 320,
  minWidth = 260,
  maxWidth = 560,
  className,
}: InspectorShellProps) => {
  const [width, setWidth] = useState(initialWidth)
  const isResizing = useRef(false)

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      if (!isResizing.current) return
      setWidth(Math.max(minWidth, Math.min(maxWidth, window.innerWidth - e.clientX)))
    }
    const onUp = () => {
      isResizing.current = false
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
    return () => {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
    }
  }, [minWidth, maxWidth])

  const onResizeStart = useCallback((e: ReactMouseEvent) => {
    e.preventDefault()
    isResizing.current = true
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
  }, [])

  return (
    <div
      className={cn(
        'relative flex flex-shrink-0 flex-col overflow-hidden border-l border-border bg-background',
        className,
      )}
      style={{ width }}
    >
      <div
        onMouseDown={onResizeStart}
        className="absolute left-0 top-0 z-10 h-full w-1 cursor-col-resize hover:bg-primary/50"
      />
      <div className="border-b border-border px-3 py-2">
        <h3 className="text-sm font-medium text-foreground">{title}</h3>
        {header}
      </div>
      {isEmpty ? (
        <div className="flex flex-1 flex-col items-center justify-center p-4 text-center">
          <p className="text-sm text-muted-foreground">{emptyMessage}</p>
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto">{children}</div>
      )}
    </div>
  )
}

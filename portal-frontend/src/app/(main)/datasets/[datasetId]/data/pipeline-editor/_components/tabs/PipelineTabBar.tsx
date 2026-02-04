'use client'

/**
 * PipelineTabBar Component
 *
 * Tab bar for managing multiple pipeline sessions.
 * Adapted from UML modeler's TabBar.tsx
 *
 */

import { Plus, X } from 'lucide-react'
import { useCallback, useState } from 'react'

import type { PipelineSession } from '../../_types/session'

// ============================================================================
// Tab Component
// ============================================================================

interface TabProps {
  session: PipelineSession
  isActive: boolean
  onSelect: (sessionId: string) => void
  onClose: (sessionId: string) => void
  onRename: (sessionId: string, newName: string) => void
}

const Tab: React.FC<TabProps> = ({ session, isActive, onSelect, onClose, onRename }) => {
  const [isEditing, setIsEditing] = useState(false)
  const [editName, setEditName] = useState(session.name)

  const handleDoubleClick = useCallback(() => {
    setIsEditing(true)
    setEditName(session.name)
  }, [session.name])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter') {
        onRename(session.id, editName.trim() || session.name)
        setIsEditing(false)
      } else if (e.key === 'Escape') {
        setEditName(session.name)
        setIsEditing(false)
      }
    },
    [session.id, session.name, editName, onRename],
  )

  const handleBlur = useCallback(() => {
    onRename(session.id, editName.trim() || session.name)
    setIsEditing(false)
  }, [session.id, session.name, editName, onRename])

  const handleClose = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation()
      onClose(session.id)
    },
    [session.id, onClose],
  )

  return (
    <div
      className={`
        group relative flex min-w-0 max-w-48 cursor-pointer items-center border-r border-border px-3 py-2
        transition-colors
        ${
          isActive
            ? 'border-b-transparent bg-background text-foreground'
            : 'bg-muted/50 text-muted-foreground hover:bg-muted hover:text-foreground'
        }
      `}
      onClick={() => onSelect(session.id)}
      onDoubleClick={handleDoubleClick}
    >
      {/* Tab Content */}
      <div className="mr-2 min-w-0 flex-1">
        {isEditing ? (
          <input
            type="text"
            value={editName}
            onChange={e => setEditName(e.target.value)}
            onKeyDown={handleKeyDown}
            onBlur={handleBlur}
            className="w-full border-none bg-transparent text-sm outline-none"
            autoFocus
          />
        ) : (
          <span className="flex items-center truncate text-sm">
            {session.name}
            {session.isDirty && <span className="ml-1 text-primary">•</span>}
          </span>
        )}
      </div>

      {/* Close Button */}
      <button
        onClick={handleClose}
        className="flex-shrink-0 rounded p-1 opacity-0 transition-opacity hover:bg-muted group-hover:opacity-100"
        title="Close tab"
      >
        <X className="h-3 w-3" />
      </button>
    </div>
  )
}

// ============================================================================
// TabBar Component
// ============================================================================

interface PipelineTabBarProps {
  sessions: PipelineSession[]
  activeSessionId: string | null
  onSelectSession: (sessionId: string) => void
  onCloseSession: (sessionId: string) => void
  onRenameSession: (sessionId: string, newName: string) => void
  onCreateSession: () => void
}

/**
 * Tab bar component for pipeline editor.
 *
 */
export const PipelineTabBar: React.FC<PipelineTabBarProps> = ({
  sessions,
  activeSessionId,
  onSelectSession,
  onCloseSession,
  onRenameSession,
  onCreateSession,
}) => {
  return (
    <div className="flex items-center overflow-hidden border-b border-border bg-muted/30">
      {/* Scrollable Tabs Container */}
      <div className="scrollbar-hide flex overflow-x-auto">
        {sessions.map(session => (
          <Tab
            key={session.id}
            session={session}
            isActive={session.id === activeSessionId}
            onSelect={onSelectSession}
            onClose={onCloseSession}
            onRename={onRenameSession}
          />
        ))}
      </div>

      {/* New Tab Button */}
      <button
        onClick={onCreateSession}
        className="flex-shrink-0 border-r border-border p-2 transition-colors hover:bg-muted"
        title="New pipeline"
      >
        <Plus className="h-4 w-4 text-muted-foreground" />
      </button>

      {/* Fill remaining space */}
      <div className="flex-1 bg-muted/30" />
    </div>
  )
}

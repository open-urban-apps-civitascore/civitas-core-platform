'use client'

/**
 * PipelineTabBar Component
 *
 * Tab bar for managing multiple pipeline sessions.
 * Adapted from UML modeler's TabBar.tsx
 *
 */

import { Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useCallback, useState } from 'react'

import type { PipelineSession } from '../../_types/session'

// ============================================================================
// Tab Component
// ============================================================================

interface TabProps {
  session: PipelineSession
  isActive: boolean
  onSelect: (sessionId: string) => void
  onRename: (sessionId: string, newName: string) => void
  canEdit: boolean
}

const Tab: React.FC<TabProps> = ({ session, isActive, onSelect, onRename, canEdit }) => {
  const [isEditing, setIsEditing] = useState(false)
  const [editName, setEditName] = useState(session.name)

  const handleDoubleClick = useCallback(() => {
    if (!canEdit) return
    setIsEditing(true)
    setEditName(session.name)
  }, [canEdit, session.name])

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
      <div className="min-w-0 flex-1">
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
  onRenameSession: (sessionId: string, newName: string) => void
  onCreateSession: () => void
  /** When false, tabs cannot be created or renamed (user lacks edit permission). */
  canEdit?: boolean
  canCreate?: boolean
}

/**
 * Tab bar component for pipeline editor.
 *
 */
export const PipelineTabBar: React.FC<PipelineTabBarProps> = ({
  sessions,
  activeSessionId,
  onSelectSession,
  onRenameSession,
  onCreateSession,
  canEdit = true,
  canCreate = true,
}) => {
  const t = useTranslations('pipelineEditor')

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
            onRename={onRenameSession}
            canEdit={canEdit}
          />
        ))}
      </div>

      {/* New Tab Button */}
      {canCreate && (
        <button
          onClick={onCreateSession}
          className="flex-shrink-0 border-r border-border p-2 transition-colors hover:bg-muted"
          title={t('tabs.newPipeline')}
        >
          <Plus className="h-4 w-4 text-muted-foreground" />
        </button>
      )}

      {/* Fill remaining space */}
      <div className="flex-1 bg-muted/30" />
    </div>
  )
}

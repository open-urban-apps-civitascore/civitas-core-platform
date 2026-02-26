'use client'

import { Plus, X } from 'lucide-react'
import { useCallback, useState } from 'react'

import { useReadOnly } from '../../hooks/use-read-only'
import type { DiagramSession } from '../../types/session'

interface TabProps {
  session: DiagramSession
  isActive: boolean
  onSelect: (sessionId: string) => void
  onClose: (sessionId: string) => void
  onRename: (sessionId: string, newName: string) => void
  isModelerReadOnly: boolean
}

const Tab: React.FC<TabProps> = props => {
  const { session, isActive, onSelect, onClose, onRename, isModelerReadOnly } = props
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
        group relative flex items-center px-3 py-2 border-r border-gray-200 cursor-pointer
        transition-colors min-w-0 max-w-48
        ${
          isActive
            ? 'bg-white border-b-white text-gray-900'
            : 'bg-gray-50 hover:bg-gray-100 text-gray-600 hover:text-gray-800'
        }
      `}
      onClick={() => onSelect(session.id)}
      onDoubleClick={isModelerReadOnly ? undefined : handleDoubleClick}
    >
      {/* Tab Content */}
      <div className="flex-1 min-w-0 mr-2">
        {isEditing ? (
          <input
            type="text"
            value={editName}
            onChange={isModelerReadOnly ? undefined : e => setEditName(e.target.value)}
            onKeyDown={isModelerReadOnly ? undefined : handleKeyDown}
            onBlur={isModelerReadOnly ? undefined : handleBlur}
            className="w-full bg-transparent border-none outline-none text-sm"
            autoFocus
          />
        ) : (
          <span className="text-sm truncate flex items-center">
            {session.name}
            {session.isDirty && <span className="ml-1 text-blue-500">•</span>}
          </span>
        )}
      </div>

      {/* Close Button */}
      {!isModelerReadOnly && (
        <button
          onClick={handleClose}
          className="flex-shrink-0 p-1 rounded hover:bg-gray-200 opacity-0 group-hover:opacity-100 transition-opacity"
          title="Close tab"
        >
          <X className="h-3 w-3" />
        </button>
      )}
    </div>
  )
}

interface TabBarProps {
  sessions: DiagramSession[]
  activeSessionId: string | null
  onSelectSession: (sessionId: string) => void
  onCloseSession: (sessionId: string) => void
  onRenameSession: (sessionId: string, newName: string) => void
  onCreateSession: () => void
}

export const TabBar: React.FC<TabBarProps> = props => {
  const { sessions, activeSessionId, onSelectSession, onCloseSession, onRenameSession, onCreateSession } = props
  const { isReadOnly: isModelerReadOnly } = useReadOnly()
  return (
    <div className="flex items-center bg-gray-50 border-b border-gray-200 overflow-hidden">
      {/* Scrollable Tabs Container */}
      <div className="flex overflow-x-auto scrollbar-hide">
        {sessions.map(session => (
          <Tab
            key={session.id}
            session={session}
            isActive={session.id === activeSessionId}
            onSelect={onSelectSession}
            onClose={onCloseSession}
            onRename={onRenameSession}
            isModelerReadOnly={isModelerReadOnly}
          />
        ))}
      </div>

      {/* New Tab Button */}
      {!isModelerReadOnly && (
        <button
          onClick={onCreateSession}
          className="flex-shrink-0 p-2 hover:bg-gray-100 border-r border-gray-200 transition-colors"
          title="New diagram"
        >
          <Plus className="h-4 w-4 text-gray-600" />
        </button>
      )}

      {/* Fill remaining space */}
      <div className="flex-1 bg-gray-50" />
    </div>
  )
}

'use client'

import { Download, Plus, Upload, X } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useCallback, useState } from 'react'

import { Button } from '@/components/ui/button'

import { useReadOnly } from '../../hooks/use-read-only'
import { DEFAULT_DIAGRAM_NAME } from '../../services/diagramService'
import type { DiagramSession } from '../../types/session'

interface TabProps {
  session: DiagramSession
  isActive: boolean
  isModelerReadOnly: boolean
  onSelect: (sessionId: string) => void
  onClose: (sessionId: string) => void
  onRename: (sessionId: string, newName: string) => void
}

const Tab: React.FC<TabProps> = props => {
  const { session, isActive, onSelect, onClose, onRename, isModelerReadOnly } = props
  const t = useTranslations('umlModeler')
  const isDefaultOrEmpty = !session.name || session.name === DEFAULT_DIAGRAM_NAME
  const displayName = isDefaultOrEmpty ? '' : session.name

  const [isEditing, setIsEditing] = useState(false)
  const [editName, setEditName] = useState(displayName)

  const handleDoubleClick = useCallback(() => {
    setIsEditing(true)
    setEditName(displayName)
  }, [displayName])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter') {
        const trimmed = editName.trim()
        if (trimmed) {
          onRename(session.id, trimmed)
        } else {
          setEditName(displayName)
        }
        setIsEditing(false)
      } else if (e.key === 'Escape') {
        setEditName(displayName)
        setIsEditing(false)
      }
    },
    [session.id, displayName, editName, onRename],
  )

  const handleBlur = useCallback(() => {
    const trimmed = editName.trim()
    if (trimmed) {
      onRename(session.id, trimmed)
    } else {
      setEditName(displayName)
    }
    setIsEditing(false)
  }, [session.id, displayName, editName, onRename])

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
            placeholder={t('modelNamePlaceholder')}
            onChange={isModelerReadOnly ? undefined : e => setEditName(e.target.value)}
            onKeyDown={isModelerReadOnly ? undefined : handleKeyDown}
            onBlur={isModelerReadOnly ? undefined : handleBlur}
            className="w-full bg-transparent border-none outline-none text-sm"
            autoFocus
          />
        ) : (
          <span className="text-sm truncate flex items-center">
            {displayName ? (
              <span>{displayName}</span>
            ) : (
              <span className="text-gray-400 italic">{t('modelNamePlaceholder')}</span>
            )}
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
  isMultiSessionMode: boolean
  onSelectSession: (sessionId: string) => void
  onCloseSession: (sessionId: string) => void
  onRenameSession: (sessionId: string, newName: string) => void
  onCreateSession: () => void
  onImportClick?: () => void
  onExportClick?: () => void
}

export const TabBar: React.FC<TabBarProps> = props => {
  const {
    sessions,
    activeSessionId,
    isMultiSessionMode,
    onSelectSession,
    onCloseSession,
    onRenameSession,
    onCreateSession,
    onImportClick,
    onExportClick,
  } = props
  const t = useTranslations('umlModeler')
  const { isReadOnly: isModelerReadOnly } = useReadOnly()
  const canCreateSession = isMultiSessionMode
  return (
    <div className="flex items-center bg-gray-50 border-b border-gray-200 overflow-hidden min-h-[38px]">
      {/* Scrollable Tabs Container */}
      {isMultiSessionMode && (
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
      )}

      {/* New Tab Button */}
      {!isModelerReadOnly && canCreateSession && (
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

      {/* Actions (Import & Export) */}
      <div className="flex items-center gap-1 px-2">
        {onImportClick && !isModelerReadOnly && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onImportClick}
            className="h-8 px-2 text-xs font-normal text-gray-700 hover:text-gray-900"
            title={t('import.file')}
          >
            <Upload className="h-3.5 w-3.5 mr-1 text-gray-600" />
            <span>{t('import.title')}</span>
          </Button>
        )}
        {onExportClick && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onExportClick}
            className="h-8 px-2 text-xs font-normal text-gray-700 hover:text-gray-900"
            title={t('export.file')}
          >
            <Download className="h-3.5 w-3.5 mr-1 text-gray-600" />
            <span>{t('export.title')}</span>
          </Button>
        )}
      </div>
    </div>
  )
}

import type { UMLDiagram } from './diagram'

export interface DiagramSession {
  id: string
  diagram: UMLDiagram
  isActive: boolean
  isDirty: boolean
  lastSaved?: Date
  filePath?: string // For local file operations
  remoteId?: string // For API operations
}

export interface SessionManager {
  sessions: DiagramSession[]
  activeSessionId?: string
  maxSessions: number
}

export type SessionAction =
  | { type: 'CREATE_SESSION'; payload: { diagram: UMLDiagram } }
  | { type: 'OPEN_SESSION'; payload: { sessionId: string } }
  | { type: 'CLOSE_SESSION'; payload: { sessionId: string; force?: boolean } }
  | { type: 'CLOSE_ALL_SESSIONS'; payload?: { force?: boolean } }
  | { type: 'SET_ACTIVE_SESSION'; payload: { sessionId: string } }
  | { type: 'UPDATE_SESSION_DIAGRAM'; payload: { sessionId: string; diagram: UMLDiagram } }
  | { type: 'MARK_SESSION_DIRTY'; payload: { sessionId: string } }
  | { type: 'MARK_SESSION_CLEAN'; payload: { sessionId: string } }
  | { type: 'SET_SESSION_FILE_PATH'; payload: { sessionId: string; filePath: string } }
  | { type: 'SET_SESSION_REMOTE_ID'; payload: { sessionId: string; remoteId: string } }
  | { type: 'RENAME_SESSION'; payload: { sessionId: string; name: string } }

export interface SessionSettings {
  autoSave: boolean
  autoSaveInterval: number // in milliseconds
  maxSessions: number
  confirmBeforeClose: boolean
  rememberOpenSessions: boolean
}

export interface UnsavedChangesDialogState {
  isOpen: boolean
  sessions: DiagramSession[]
  action: 'close' | 'new' | 'open' | 'exit'
  onConfirm?: () => void
  onCancel?: () => void
}

// File operations
export interface FileOperationResult {
  success: boolean
  error?: string
  filePath?: string
}

export interface SaveOptions {
  format?: 'json' | 'xmi'
  prettify?: boolean
  includeMetadata?: boolean
}

export interface OpenOptions {
  merge?: boolean // Merge with existing diagram vs replace
  validateOnOpen?: boolean
}

// Recent files
export interface RecentFile {
  id: string
  name: string
  path: string
  lastOpened: Date
  thumbnail?: string // Base64 encoded thumbnail image
}

export interface RecentFilesManager {
  files: RecentFile[]
  maxFiles: number
}

export type RecentFilesAction =
  | { type: 'ADD_RECENT_FILE'; payload: RecentFile }
  | { type: 'REMOVE_RECENT_FILE'; payload: { id: string } }
  | { type: 'CLEAR_RECENT_FILES' }
  | { type: 'UPDATE_RECENT_FILE'; payload: { id: string; updates: Partial<RecentFile> } }

// Session persistence
export interface SessionPersistence {
  version: string
  sessions: Array<{
    id: string
    diagramId: string
    diagramName: string
    isActive: boolean
    filePath?: string
    remoteId?: string
    lastModified: string
  }>
  settings: SessionSettings
  recentFiles: RecentFile[]
}

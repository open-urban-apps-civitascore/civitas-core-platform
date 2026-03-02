import type { Pipeline } from './pipeline'

// ============================================================================
// Pipeline Session
// ============================================================================

/**
 * Represents a single pipeline editing session (one tab).
 *
 */
export interface PipelineSession {
  /** Unique session identifier */
  id: string
  /** Display name (shown in tab) */
  name: string
  /** The pipeline being edited in this session */
  pipeline: Pipeline
  /** Whether the session has unsaved changes */
  isDirty: boolean
  /** Timestamp when session was created */
  created: Date
  /** Timestamp of last modification */
  lastModified: Date
}

// ============================================================================
// Multi-Session State
// ============================================================================

/**
 * State for managing multiple pipeline sessions (tabs).
 *
 */
export interface PipelineSessionState {
  /** All active sessions */
  sessions: PipelineSession[]
  /** ID of the currently active session (visible tab) */
  activeSessionId: string | null
}

// ============================================================================
// Session Actions (Redux-style)
// ============================================================================

/**
 * Actions for session state management.
 */
export type PipelineSessionAction =
  | { type: 'CREATE_SESSION'; payload: { name?: string } }
  | { type: 'CLOSE_SESSION'; payload: { sessionId: string } }
  | { type: 'SWITCH_SESSION'; payload: { sessionId: string } }
  | { type: 'UPDATE_SESSION_NAME'; payload: { sessionId: string; name: string } }
  | { type: 'UPDATE_SESSION_PIPELINE'; payload: { sessionId: string; pipeline: Pipeline } }
  | { type: 'MARK_SESSION_DIRTY'; payload: { sessionId: string } }
  | { type: 'MARK_SESSION_CLEAN'; payload: { sessionId: string } }
  | { type: 'LOAD_SESSIONS'; payload: { sessions: PipelineSession[]; activeSessionId: string | null } }

// ============================================================================
// Session Actions Interface
// ============================================================================

/**
 * Interface with action functions for ease of use in components.
 */
export interface PipelineSessionActions {
  /** Creates a new pipeline session/tab */
  createSession: (name?: string) => string
  /** Closes a session by ID */
  closeSession: (sessionId: string) => void
  /** Switches to a different session */
  switchToSession: (sessionId: string) => void
  /** Updates the name of a session (tab rename) */
  updateSessionName: (sessionId: string, name: string) => void
  /** Updates the pipeline in a session */
  updateSessionPipeline: (sessionId: string, pipeline: Pipeline) => void
  /** Marks a session as having unsaved changes */
  markSessionDirty: (sessionId: string) => void
  /** Marks a session as saved (no pending changes) */
  markSessionClean: (sessionId: string) => void
  /** Gets the currently active session */
  getActiveSession: () => PipelineSession | null
  /** Gets all sessions */
  getAllSessions: () => PipelineSession[]
}

// ============================================================================
// Hook Return Type
// ============================================================================

/**
 * Return type for usePipelineSession hook.
 * Combines state access with action functions.
 */
export interface UsePipelineSessionReturn extends PipelineSessionActions {
  /** All active sessions */
  sessions: PipelineSession[]
  /** ID of the active session */
  activeSessionId: string | null
  /** The active session object (convenience accessor) */
  activeSession: PipelineSession | null
}

// ============================================================================
// Session Serialization
// ============================================================================

/**
 * Serialized session state for persistence/export.
 *
 */
export interface SerializedSessionState {
  sessions: Array<{
    id: string
    name: string
    pipeline: {
      id: string
      name: string
      description: string
      nodes: unknown[]
      edges: unknown[]
      viewport?: {
        x: number
        y: number
        zoom: number
      }
      createdAt: string
      updatedAt: string
    }
    created: string
    lastModified: string
  }>
  activeSessionId: string | null
}

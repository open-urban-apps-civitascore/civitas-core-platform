import type { UMLDiagram } from './diagram'

export interface DiagramSession {
  id: string
  name: string
  diagram: UMLDiagram
  isDirty: boolean
  lastModified: Date
  created: Date
}

export interface MultiSessionState {
  sessions: DiagramSession[]
  activeSessionId: string | null
}

// Redux-style actions following diagram.ts pattern
export type SessionAction =
  | { type: 'CREATE_SESSION'; payload: { name?: string } }
  | { type: 'CLOSE_SESSION'; payload: { sessionId: string } }
  | { type: 'SWITCH_SESSION'; payload: { sessionId: string } }
  | { type: 'UPDATE_SESSION_NAME'; payload: { sessionId: string; name: string } }
  | { type: 'UPDATE_SESSION_DIAGRAM'; payload: { sessionId: string; diagram: UMLDiagram } }
  | { type: 'MARK_SESSION_DIRTY'; payload: { sessionId: string } }
  | { type: 'MARK_SESSION_CLEAN'; payload: { sessionId: string } }
  | { type: 'LOAD_SESSIONS'; payload: { sessions: DiagramSession[]; activeSessionId: string | null } }

// Hook interface with actions as functions (for ease of use)
export interface MultiSessionActions {
  createSession: (name?: string) => string
  closeSession: (sessionId: string) => void
  switchToSession: (sessionId: string) => void
  updateSessionName: (sessionId: string, name: string) => void
  updateSessionDiagram: (sessionId: string, diagram: UMLDiagram) => void
  markSessionDirty: (sessionId: string) => void
  markSessionClean: (sessionId: string) => void
  getActiveSession: () => DiagramSession | null
  getAllSessions: () => DiagramSession[]
}

export interface UseMultiSessionReturn extends MultiSessionActions {
  sessions: DiagramSession[]
  activeSessionId: string | null
  activeSession: DiagramSession | null
}

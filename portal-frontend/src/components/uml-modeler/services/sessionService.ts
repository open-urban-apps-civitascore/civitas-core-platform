import { DirtyField, type DiagramSession, type MultiSessionState, type SessionAction } from '../types/session'
import { createEmptyDiagram } from './diagramService'

// Initial session state factory
export const createEmptySession = (name?: string): DiagramSession => {
  const now = new Date()
  return {
    id: crypto.randomUUID(),
    name: name || 'Untitled Diagram',
    diagram: createEmptyDiagram(),
    isDirty: false,
    dirtyFields: new Set(),
    lastModified: now,
    created: now,
  }
}

// Initial multi-session state
export const createInitialSessionState = (initialSession?: DiagramSession): MultiSessionState => {
  const firstSession = initialSession || createEmptySession('Untitled Diagram')
  return {
    sessions: [firstSession],
    activeSessionId: firstSession.id,
  }
}

// Session reducer following the pattern from diagramService.ts
export const sessionReducer = (state: MultiSessionState, action: SessionAction): MultiSessionState => {
  switch (action.type) {
    case 'CREATE_SESSION': {
      const { name } = action.payload
      const newSession = createEmptySession(name)

      return {
        sessions: [...state.sessions, newSession],
        activeSessionId: newSession.id,
      }
    }

    case 'CLOSE_SESSION': {
      const { sessionId, sessionName } = action.payload
      const sessionIndex = state.sessions.findIndex(s => s.id === sessionId)

      if (sessionIndex === -1) return state

      const newSessions = state.sessions.filter(s => s.id !== sessionId)

      // If closing the active session, switch to another one
      let newActiveId = state.activeSessionId
      if (state.activeSessionId === sessionId) {
        if (newSessions.length > 0) {
          // Try to activate session after the closed one, or the previous one
          const nextIndex = Math.min(sessionIndex, newSessions.length - 1)
          newActiveId = newSessions[nextIndex].id
        } else {
          // No sessions left, create a new one
          const newSession = createEmptySession(sessionName)
          return {
            sessions: [newSession],
            activeSessionId: newSession.id,
          }
        }
      }

      return {
        sessions: newSessions,
        activeSessionId: newActiveId,
      }
    }

    case 'SET_SESSION': {
      const { sessionId, session } = action.payload
      const newSessions = state.sessions.map(s => (s.id === sessionId ? session : s))

      return { ...state, sessions: [...newSessions] }
    }

    case 'SWITCH_SESSION': {
      const { sessionId } = action.payload

      if (state.sessions.find(s => s.id === sessionId)) {
        return {
          ...state,
          activeSessionId: sessionId,
        }
      }
      return state
    }

    case 'UPDATE_SESSION_NAME': {
      const { sessionId, name } = action.payload

      return {
        ...state,
        sessions: state.sessions.map(session =>
          session.id === sessionId
            ? { ...session, name, lastModified: new Date(), diagram: { ...session.diagram, name } }
            : session,
        ),
      }
    }

    case 'UPDATE_SESSION_DIAGRAM': {
      const { sessionId, diagram } = action.payload

      return {
        ...state,
        sessions: state.sessions.map(session =>
          session.id === sessionId
            ? {
                ...session,
                diagram,
                isDirty: true,
                lastModified: new Date(),
              }
            : session,
        ),
      }
    }

    case 'MARK_SESSION_DIRTY': {
      const { sessionId, dirtyField } = action.payload

      return {
        ...state,
        sessions: state.sessions.map(session =>
          session.id === sessionId
            ? {
                ...session,
                isDirty: true,
                dirtyFields: new Set([...session.dirtyFields, dirtyField]),
                lastModified: new Date(),
              }
            : session,
        ),
      }
    }

    case 'MARK_SESSION_CLEAN': {
      const { sessionId } = action.payload

      return {
        ...state,
        sessions: state.sessions.map(session =>
          session.id === sessionId
            ? {
                ...session,
                isDirty: false,
                dirtyFields: new Set<DirtyField>(),
              }
            : session,
        ),
      }
    }

    case 'MARK_FIELD_CLEAN': {
      const { sessionId, dirtyField } = action.payload
      return {
        ...state,
        sessions: state.sessions.map(session => {
          const dirtyFields = new Set([...session.dirtyFields].filter(field => field !== dirtyField))
          return session.id === sessionId
            ? {
                ...session,
                isDirty: dirtyFields.size > 0,
                dirtyFields: new Set([...session.dirtyFields].filter(field => field !== dirtyField)),
              }
            : session
        }),
      }
    }

    case 'LOAD_SESSIONS': {
      const { sessions, activeSessionId } = action.payload

      return {
        sessions,
        activeSessionId,
      }
    }

    default:
      return state
  }
}

// Helper functions
export const findSessionById = (state: MultiSessionState, sessionId: string): DiagramSession | undefined => {
  return state.sessions.find(session => session.id === sessionId)
}

export const getActiveSession = (state: MultiSessionState): DiagramSession | null => {
  return findSessionById(state, state.activeSessionId!) || null
}

export const getAllSessions = (state: MultiSessionState): DiagramSession[] => {
  return [...state.sessions]
}

// Session serialization
export const serializeSessions = (state: MultiSessionState): string => {
  return JSON.stringify(state, null, 2)
}

export const deserializeSessions = (data: string): MultiSessionState => {
  const parsed = JSON.parse(data) as { sessions: DiagramSession[]; activeSessionId: string | null }
  return {
    ...parsed,
    sessions: parsed.sessions.map(session => ({
      ...session,
      lastModified: new Date(session.lastModified),
      created: new Date(session.created),
    })),
  }
}

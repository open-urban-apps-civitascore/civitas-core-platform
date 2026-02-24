/**
 * Session Service
 *
 * Service for managing multiple pipeline sessions (tabs).
 *
 */

import type { Pipeline, PipelineOutputDTO, PipelineStylesPayload } from '../_types/pipeline'
import type { PipelineSession, PipelineSessionAction, PipelineSessionState } from '../_types/session'
import { createEmptyPipeline } from './pipelineService'

// ============================================================================
// Factory Functions
// ============================================================================

/**
 * Creates a new empty pipeline session.
 *
 */
export const createEmptySession = (name?: string): PipelineSession => {
  const now = new Date()
  return {
    id: crypto.randomUUID(),
    name: name || `Pipeline ${Date.now()}`,
    pipeline: createEmptyPipeline(name),
    isDirty: false,
    lastModified: now,
    created: now,
  }
}

/**
 * Creates the initial session state with one default session.
 *
 */
export const createInitialSessionState = (initialSession?: PipelineSession): PipelineSessionState => {
  const firstSession = initialSession || createEmptySession('Untitled Pipeline')
  return {
    sessions: [firstSession],
    activeSessionId: firstSession.id,
  }
}

/**
 * Creates a PipelineSession from a backend PipelineOutputDTO.
 * Parses the `styles` JSON string to restore nodes, edges, and viewport.
 */
export const createSessionFromBackendDTO = (dto: PipelineOutputDTO): PipelineSession => {
  const now = new Date()
  let parsedStyles: PipelineStylesPayload | null = null

  try {
    if (dto.styles) {
      parsedStyles = dto.styles as PipelineStylesPayload
    }
  } catch {
    console.error(`Failed to parse styles for pipeline ${dto.id}:`, dto.styles)
  }

  const pipeline: Pipeline = {
    id: dto.id,
    name: dto.name,
    description: dto.description || '',
    nodes: parsedStyles?.nodes ?? [],
    edges: parsedStyles?.edges ?? [],
    viewport: parsedStyles?.viewport ?? { x: 0, y: 0, zoom: 1 },
    createdAt: dto.createdAt ? new Date(dto.createdAt) : now,
    updatedAt: dto.modifiedAt ? new Date(dto.modifiedAt) : now,
    isDirty: false,
  }

  return {
    id: crypto.randomUUID(),
    name: dto.name,
    pipeline,
    isDirty: false,
    created: pipeline.createdAt,
    lastModified: pipeline.updatedAt,
  }
}

// ============================================================================
// Session Reducer
// ============================================================================

/**
 * Reducer for multi-session state management.
 */
export const sessionReducer = (state: PipelineSessionState, action: PipelineSessionAction): PipelineSessionState => {
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
      const { sessionId } = action.payload
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
          const newSession = createEmptySession('Untitled Pipeline')
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
            ? {
                ...session,
                name,
                pipeline: { ...session.pipeline, name },
                lastModified: new Date(),
              }
            : session,
        ),
      }
    }

    case 'UPDATE_SESSION_PIPELINE': {
      const { sessionId, pipeline } = action.payload

      return {
        ...state,
        sessions: state.sessions.map(session =>
          session.id === sessionId
            ? {
                ...session,
                pipeline,
                isDirty: true,
                lastModified: new Date(),
              }
            : session,
        ),
      }
    }

    case 'MARK_SESSION_DIRTY': {
      const { sessionId } = action.payload

      return {
        ...state,
        sessions: state.sessions.map(session =>
          session.id === sessionId
            ? {
                ...session,
                isDirty: true,
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
              }
            : session,
        ),
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

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Finds a session by ID.
 *
 */
export const findSessionById = (state: PipelineSessionState, sessionId: string): PipelineSession | undefined => {
  return state.sessions.find(session => session.id === sessionId)
}

/**
 * Gets the active session.
 *
 */
export const getActiveSession = (state: PipelineSessionState): PipelineSession | null => {
  if (!state.activeSessionId) return null
  return findSessionById(state, state.activeSessionId) || null
}

/**
 * Gets all sessions.
 *
 */
export const getAllSessions = (state: PipelineSessionState): PipelineSession[] => {
  return [...state.sessions]
}

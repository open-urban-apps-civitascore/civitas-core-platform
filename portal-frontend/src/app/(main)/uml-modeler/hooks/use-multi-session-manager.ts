'use client'

import { useCallback, useReducer } from 'react'

import {
  createInitialSessionState,
  getActiveSession as getActiveSessionFromState,
  getAllSessions as getAllSessionsFromState,
  sessionReducer,
} from '../services/sessionService'
import type { UMLDiagram } from '../types/diagram'
import type { DiagramSession, UseMultiSessionReturn } from '../types/session'

export const useMultiSessionManager = (initialSession?: DiagramSession): UseMultiSessionReturn => {
  const [state, dispatch] = useReducer(sessionReducer, undefined, () => createInitialSessionState(initialSession))

  const createSession = useCallback(
    (name?: string): string => {
      const newSessionId = crypto.randomUUID()
      dispatch({
        type: 'CREATE_SESSION',
        payload: { name },
      })
      return newSessionId
    },
    [dispatch],
  )

  const closeSession = useCallback(
    (sessionId: string): void => {
      dispatch({
        type: 'CLOSE_SESSION',
        payload: { sessionId },
      })
    },
    [dispatch],
  )

  const switchToSession = useCallback(
    (sessionId: string): void => {
      dispatch({
        type: 'SWITCH_SESSION',
        payload: { sessionId },
      })
    },
    [dispatch],
  )

  const updateSessionName = useCallback(
    (sessionId: string, name: string): void => {
      dispatch({
        type: 'UPDATE_SESSION_NAME',
        payload: { sessionId, name },
      })
    },
    [dispatch],
  )

  const updateSessionDiagram = useCallback(
    (sessionId: string, diagram: UMLDiagram): void => {
      dispatch({
        type: 'UPDATE_SESSION_DIAGRAM',
        payload: { sessionId, diagram },
      })
    },
    [dispatch],
  )

  const markSessionDirty = useCallback(
    (sessionId: string): void => {
      dispatch({
        type: 'MARK_SESSION_DIRTY',
        payload: { sessionId },
      })
    },
    [dispatch],
  )

  const markSessionClean = useCallback(
    (sessionId: string): void => {
      dispatch({
        type: 'MARK_SESSION_CLEAN',
        payload: { sessionId },
      })
    },
    [dispatch],
  )

  const getActiveSession = useCallback((): DiagramSession | null => {
    return getActiveSessionFromState(state)
  }, [state])

  const getAllSessions = useCallback((): DiagramSession[] => {
    return getAllSessionsFromState(state)
  }, [state])

  const activeSession = getActiveSession()

  return {
    sessions: state.sessions,
    activeSessionId: state.activeSessionId,
    activeSession,
    createSession,
    closeSession,
    switchToSession,
    updateSessionName,
    updateSessionDiagram,
    markSessionDirty,
    markSessionClean,
    getActiveSession,
    getAllSessions,
  }
}

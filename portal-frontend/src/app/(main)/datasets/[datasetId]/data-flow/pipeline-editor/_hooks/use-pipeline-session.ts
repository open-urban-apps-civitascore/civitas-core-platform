'use client'

/**
 * usePipelineSession Hook
 *
 * Multi-session management hook for pipeline editor.
 * Adapted from UML modeler's use-multi-session-manager.ts
 *
 */

import { useCallback, useReducer } from 'react'

import {
  createInitialSessionState,
  getActiveSession as getActiveSessionFromState,
  getAllSessions as getAllSessionsFromState,
  sessionReducer,
} from '../_services/sessionService'
import type { Pipeline } from '../_types/pipeline'
import type { PipelineSession, UsePipelineSessionReturn } from '../_types/session'

/**
 * Hook for managing multiple pipeline sessions (tabs).
 *
 * @param initialSession - Optional initial session to start with
 * @returns Session management functions and state
 *
 */
export const usePipelineSession = (initialSession?: PipelineSession): UsePipelineSessionReturn => {
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

  const updateSessionPipeline = useCallback(
    (sessionId: string, pipeline: Pipeline): void => {
      dispatch({
        type: 'UPDATE_SESSION_PIPELINE',
        payload: { sessionId, pipeline },
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

  const getActiveSession = useCallback((): PipelineSession | null => {
    return getActiveSessionFromState(state)
  }, [state])

  const getAllSessions = useCallback((): PipelineSession[] => {
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
    updateSessionPipeline,
    markSessionDirty,
    markSessionClean,
    getActiveSession,
    getAllSessions,
  }
}

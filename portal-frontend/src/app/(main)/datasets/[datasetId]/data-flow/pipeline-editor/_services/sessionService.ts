/**
 * Session Service
 *
 * Service for managing multiple pipeline sessions (tabs).
 *
 */

import { DATASINK_TYPES, type DataSink } from '@/types/datasinks'

import { ENTITY_TYPES, type PipelineNodeData } from '../_types/nodes'
import type { Pipeline, PipelineNode, PipelineOutputDTO, PipelineStylesPayload } from '../_types/pipeline'
import type { PipelineSession, PipelineSessionAction, PipelineSessionState } from '../_types/session'
import { createEmptyPipeline } from './pipelineService'
import { isValidNifiCron } from './validationService'

// ============================================================================
// CORE-model fallback hydration
// ============================================================================

/** One node of the stored CORE Pipeline document (`model`), as far as hydration needs it. */
interface CoreModelNode {
  id: string
  kind: string
  label?: string
  sourceRef?: string
  mappingRef?: string
  sinkRef?: string
  cronExpression?: string
  'x-ui-position'?: { x: number; y: number }
}

interface CoreModel {
  nodes?: CoreModelNode[]
  edges?: { id: string; source: string; target: string }[]
}

/** Logical (8-segment) form of a possibly versioned CORE URN. */
const logicalUrn = (urn: string): string => {
  const segments = urn.split(':')
  return segments.length === 9 ? segments.slice(0, 8).join(':') : urn
}

/**
 * Builds a renderable React-Flow graph from the CORE `model` when a pipeline carries no `styles`.
 *
 * The editor normally hydrates from `styles` only — its own round-trip artifact. A pipeline that
 * entered the platform through the bundle import has a model but no styles, and would render as an
 * empty canvas although it exists and deploys. The model is the source of truth, so it is the
 * honest fallback.
 *
 * Hydrated nodes carry the fields the SAVE path emits refs from (`configurationUrn`,
 * `mappingRef`), and `configured` is derived from ref presence — a node whose reference resolves
 * against the installed artifacts IS configured; instance-authored editor state must never be the
 * bar for content that arrived through the bundle import.
 *
 * A CORE `sink` node does not say which sink type it is, so the caller passes the dataset's sinks
 * (`dataSinks`): the node whose `sinkRef` matches a sink's `configurationUrn` hydrates with that
 * sink's real type (frost vs geoPersistence), its `entityId` (so a later editor save updates the
 * existing sink instead of minting a twin) and its table/structure details. An unmatched sink
 * falls back to an unconfigured geoPersistence node, the pre-existing behaviour.
 */
export const hydrateStylesFromCoreModel = (model: object, dataSinks: DataSink[] = []): PipelineStylesPayload | null => {
  const coreModel = model as CoreModel
  if (!Array.isArray(coreModel.nodes) || coreModel.nodes.length === 0) return null

  const nodes: PipelineNode[] = []
  for (const [index, coreNode] of coreModel.nodes.entries()) {
    const position = coreNode['x-ui-position'] ?? { x: index * 260, y: 120 }
    const base = { label: coreNode.label ?? coreNode.kind }

    let node: PipelineNode | null = null
    switch (coreNode.kind) {
      case 'source':
        node = {
          id: coreNode.id,
          type: 'dataSource',
          position,
          data: {
            ...base,
            entityType: ENTITY_TYPES.Datasource,
            entityName: coreNode.label,
            configurationUrn: coreNode.sourceRef,
            configured: Boolean(coreNode.sourceRef),
          } as PipelineNodeData,
        }
        break
      case 'sink': {
        const sink = dataSinks.find(candidate => candidate.configurationUrn === coreNode.sinkRef)
        if (sink?.dataSinkType === DATASINK_TYPES.FROST) {
          node = {
            id: coreNode.id,
            type: 'frost',
            position,
            data: {
              ...base,
              entityType: ENTITY_TYPES.Frost,
              entityId: sink.id,
              configurationUrn: coreNode.sinkRef,
              // Registry defaults: the FROST panel is read-only, the server is platform-owned.
              serverName: 'Sensor Data Storage',
              serverUrl: '',
              version: '1.1',
              configured: true,
            } as PipelineNodeData,
          }
        } else {
          const version = sink?.configuration?.dataStructureVersion
          const dataStructureVersionId = version ? `${version.dataStructureId}/${version.id}` : undefined
          const tableName = sink?.configuration?.tableName ?? ''
          node = {
            id: coreNode.id,
            type: 'geoPersistence',
            position,
            data: {
              ...base,
              entityType: ENTITY_TYPES.Persistence,
              entityId: sink?.id,
              configurationUrn: coreNode.sinkRef,
              tableName,
              dataStructureVersionId,
              // Same bar the GeoPersistence panel applies: table name plus target structure.
              configured: tableName.trim().length > 0 && dataStructureVersionId !== undefined,
            } as PipelineNodeData,
          }
        }
        break
      }
      case 'mapping':
        node = {
          id: coreNode.id,
          type: 'mapping',
          position,
          data: {
            ...base,
            mappingRef: coreNode.mappingRef,
            mappingLogicalUrn: coreNode.mappingRef ? logicalUrn(coreNode.mappingRef) : undefined,
            // Minimal valid config: the mapping's real content lives behind mappingRef; the
            // mapping sub-editor starts empty until the artifact is loaded/re-authored.
            mappingConfig: { fields: {}, positions: {} },
            configured: Boolean(coreNode.mappingRef),
          } as PipelineNodeData,
        }
        break
      case 'start':
      case 'end':
        node = {
          id: coreNode.id,
          type: coreNode.kind,
          position,
          data: {
            ...base,
            // Mirror the registry defaults so the inspector's isData guards recognise the node.
            nodeType: coreNode.kind,
            configured: true,
            description:
              coreNode.kind === 'start'
                ? 'Entry point of the pipeline. Execution begins here.'
                : 'Exit point of the pipeline. Execution completes here.',
          } as PipelineNodeData,
        }
        break
      case 'cron': {
        const cronExpression = coreNode.cronExpression ?? ''
        node = {
          id: coreNode.id,
          type: 'cron',
          position,
          data: {
            ...base,
            cronExpression,
            // Same bar the cron panel applies on edit.
            configured: cronExpression.trim() !== '' && isValidNifiCron(cronExpression.trim()),
          } as PipelineNodeData,
        }
        break
      }
      default:
        // CORE knows kinds the editor has no visual for yet (filter, enrich, split) — skip
        // rather than crash; the model stays intact, only the canvas omits them.
        console.warn(`hydrateStylesFromCoreModel: no editor node for kind '${coreNode.kind}'`)
    }
    if (node) nodes.push(node)
  }

  return {
    nodes,
    edges: (coreModel.edges ?? []).map(edge => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      data: {},
    })),
    nodePositions: Object.fromEntries(nodes.map(node => [node.id, node.position])),
    viewport: { x: 0, y: 0, zoom: 1 },
  }
}

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
 * Parses the `styles` JSON string to restore nodes, edges, and viewport. A pipeline without
 * styles (e.g. installed by the marketplace bundle import, which authors only the CORE model)
 * hydrates from the model instead of rendering an empty canvas; `dataSinks` (the dataset's sinks)
 * lets that hydration resolve each sink node's real type and entity.
 */
export const createSessionFromBackendDTO = (dto: PipelineOutputDTO, dataSinks: DataSink[] = []): PipelineSession => {
  const now = new Date()
  let parsedStyles: PipelineStylesPayload | null = null

  try {
    if (dto.styles) {
      parsedStyles = dto.styles as PipelineStylesPayload
    }
  } catch {
    console.error(`Failed to parse styles for pipeline ${dto.id}:`, dto.styles)
  }

  if (!parsedStyles?.nodes?.length && dto.model) {
    parsedStyles = hydrateStylesFromCoreModel(dto.model, dataSinks) ?? parsedStyles
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
                isDirty: true,
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
        sessions: state.sessions.map(session => {
          if (session.id !== sessionId) return session
          const nextPipeline = typeof pipeline === 'function' ? pipeline(session.pipeline) : pipeline
          return {
            ...session,
            pipeline: nextPipeline,
            isDirty: true,
            lastModified: new Date(),
          }
        }),
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

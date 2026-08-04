import { describe, expect, it } from 'vitest'

import type { PipelineNode } from '../_types/pipeline'
import type { PipelineSession } from '../_types/session'
import {
  isValidTableName,
  normalizeTableName,
  tableNameOwnerOutsideNode,
  tableNameOwnersOutsideSession,
} from './dataSinkNameService'
import { createEmptyPipeline } from './pipelineService'

const geoNode = (nodeId: string, tableName: string): PipelineNode =>
  ({
    id: nodeId,
    type: 'geoPersistence',
    position: { x: 0, y: 0 },
    data: { label: nodeId, configured: true, entityType: 'persistence', tableName },
  }) as unknown as PipelineNode

const session = (sessionId: string, pipelineName: string, nodes: PipelineNode[]): PipelineSession => ({
  id: sessionId,
  name: pipelineName,
  pipeline: { ...createEmptyPipeline(pipelineName), nodes },
  isDirty: false,
  created: new Date(),
  lastModified: new Date(),
})

describe('normalizeTableName', () => {
  it('trims and lowercases', () => {
    expect(normalizeTableName('  Roads  ')).toBe('roads')
  })
})

describe('isValidTableName', () => {
  it.each(['roads', '_roads', 'Roads_2024', 'r'])('accepts %s', tableName => {
    expect(isValidTableName(tableName)).toBe(true)
  })

  it.each(['', '2roads', 'roads-2024', 'roads 2024', 'roads;drop', 'straßen', ' roads'])('rejects %s', tableName => {
    expect(isValidTableName(tableName)).toBe(false)
  })
})

describe('tableNameOwnersOutsideSession', () => {
  it('maps the normalized names of the other sessions to their pipeline name', () => {
    const sessions = [
      session('session-1', 'Traffic', [geoNode('geo-1', 'roads')]),
      session('session-2', 'Water', [geoNode('geo-2', 'Rivers')]),
    ]

    expect(tableNameOwnersOutsideSession(sessions, 'session-1')).toEqual({ rivers: 'Water' })
  })

  it('keeps the first pipeline using a name', () => {
    const sessions = [
      session('session-1', 'Traffic', [geoNode('geo-1', 'roads')]),
      session('session-2', 'Water', [geoNode('geo-2', 'rivers')]),
      session('session-3', 'Backup', [geoNode('geo-3', 'Rivers')]),
    ]

    expect(tableNameOwnersOutsideSession(sessions, 'session-1')).toEqual({ rivers: 'Water' })
  })

  it('skips nodes without a name', () => {
    const sessions = [session('session-1', 'Traffic', []), session('session-2', 'Water', [geoNode('geo-1', '   ')])]

    expect(tableNameOwnersOutsideSession(sessions, 'session-1')).toEqual({})
  })

  it('ignores only the given session', () => {
    const sessions = [session('session-1', 'Traffic', [geoNode('geo-1', 'streets')])]

    expect(tableNameOwnersOutsideSession(sessions, 'session-2')).toEqual({ streets: 'Traffic' })
  })
})

describe('tableNameOwnerOutsideNode', () => {
  it('names the pipeline of a sibling node in the same pipeline', () => {
    const sessions = [session('session-1', 'Traffic', [geoNode('geo-1', 'roads'), geoNode('geo-2', 'rivers')])]

    expect(tableNameOwnerOutsideNode(sessions, 'geo-1', 'RIVERS')).toBe('Traffic')
  })

  it('names the pipeline of another session', () => {
    const sessions = [
      session('session-1', 'Traffic', [geoNode('geo-1', 'roads')]),
      session('session-2', 'Water', [geoNode('geo-2', 'rivers')]),
    ]

    expect(tableNameOwnerOutsideNode(sessions, 'geo-1', 'rivers')).toBe('Water')
  })

  it('is null for the name the given node uses itself', () => {
    const sessions = [session('session-1', 'Traffic', [geoNode('geo-1', 'roads')])]

    expect(tableNameOwnerOutsideNode(sessions, 'geo-1', 'roads')).toBeNull()
  })

  it('is null for a blank name', () => {
    const sessions = [session('session-1', 'Traffic', [geoNode('geo-1', 'roads')])]

    expect(tableNameOwnerOutsideNode(sessions, 'geo-2', '   ')).toBeNull()
  })
})

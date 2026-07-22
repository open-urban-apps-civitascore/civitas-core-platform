import { describe, expect, it } from 'vitest'

import { DATASINK_TYPES, type DataSinkPayload } from '@/types/datasinks'

import type { Pipeline, PipelineEdge, PipelineNode } from '../_types/pipeline'
import { buildDataSinkPayloads, type DataSinkSnapshot, isDestructiveDataSinkChange } from './payloadBuilderService'

/**
 * Pins the FROST sink payload derivation — the seam that lets the deploy engine resolve the
 * match keys at all: a wrong result here publishes a sink without (or with the wrong) target
 * structure and every mapped deploy fails.
 */

type TestNode = { id: string; type: string; data: Record<string, unknown> }

const pipeline = (nodes: TestNode[], edges: { source: string; target: string }[]): Pipeline => ({
  id: 'p-1',
  name: 'P',
  description: '',
  nodes: nodes as unknown as PipelineNode[],
  edges: edges.map((edge, index) => ({ ...edge, id: `e-${index}` })) as unknown as PipelineEdge[],
  viewport: { x: 0, y: 0, zoom: 1 },
  createdAt: new Date(0),
  updatedAt: new Date(0),
  isDirty: false,
})

const frostNode: TestNode = {
  id: 'frost-1',
  type: 'frost',
  data: { label: 'FROST', configured: true, entityType: 'frost', entityId: 'sink-1' },
}

const mappingNode = (id: string, targetVersionId?: string): TestNode => ({
  id,
  type: 'mapping',
  data: {
    label: 'Mapping',
    configured: true,
    mappingConfig: { fields: {}, positions: {} },
    ...(targetVersionId === undefined ? {} : { targetVersionId }),
  },
})

const frostPayload = (p: Pipeline) =>
  buildDataSinkPayloads(p).find(entry => entry.payload.dataSinkType === 'FROST')?.payload

describe('buildDataSinkPayloads — FROST target structure reference', () => {
  it('references the final mapping target of a mapped pipeline', () => {
    const p = pipeline([mappingNode('map-1', 'dsv-1'), frostNode], [{ source: 'map-1', target: 'frost-1' }])
    expect(frostPayload(p)?.configuration).toEqual({ dataStructureVersionId: 'dsv-1' })
  })

  it('sends an empty configuration for a passthrough pipeline (no mapping)', () => {
    const source: TestNode = {
      id: 'src-1',
      type: 'dataSource',
      data: { label: 'MQTT', configured: true, entityType: 'datasource', entityId: 'ds-1' },
    }
    const p = pipeline([source, frostNode], [{ source: 'src-1', target: 'frost-1' }])
    expect(frostPayload(p)?.configuration).toEqual({})
  })

  it('picks the nearest mapping of a chain (the one whose target the sink consumes)', () => {
    const p = pipeline(
      [mappingNode('map-1', 'dsv-upstream'), mappingNode('map-2', 'dsv-final'), frostNode],
      [
        { source: 'map-1', target: 'map-2' },
        { source: 'map-2', target: 'frost-1' },
      ],
    )
    expect(frostPayload(p)?.configuration).toEqual({ dataStructureVersionId: 'dsv-final' })
  })

  it('terminates on a cyclic graph', () => {
    const p = pipeline(
      [mappingNode('map-1', 'dsv-1'), frostNode],
      [
        { source: 'map-1', target: 'frost-1' },
        { source: 'frost-1', target: 'map-1' },
      ],
    )
    expect(frostPayload(p)?.configuration).toEqual({ dataStructureVersionId: 'dsv-1' })
  })

  it('sends an empty configuration when the mapping has no target version yet', () => {
    // The deploy then fails loudly on the engine side; the editor validation forces a re-save
    // before this state can be released.
    const p = pipeline([mappingNode('map-1'), frostNode], [{ source: 'map-1', target: 'frost-1' }])
    expect(frostPayload(p)?.configuration).toEqual({})
  })
})

describe('isDestructiveDataSinkChange', () => {
  const postgisPayload = (tableName: string, dataStructureVersionId: string): DataSinkPayload => ({
    id: 'sink-1',
    dataSinkType: DATASINK_TYPES.POSTGIS,
    configuration: { tableName, dataStructureVersionId },
  })

  const snapshotFor = (payload: DataSinkPayload, entityId: string | null): DataSinkSnapshot => {
    const { id: _id, ...comparable } = payload
    return { 'node-1': { entityId, configJson: JSON.stringify(comparable) } }
  }

  it('flags a tableName change on an existing sink', () => {
    const snapshot = snapshotFor(postgisPayload('old', 'v1'), 'sink-1')
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('new', 'v1'), snapshot)).toBe(true)
  })

  it('flags a dataStructureVersionId change on an existing sink', () => {
    const snapshot = snapshotFor(postgisPayload('t', 'v1'), 'sink-1')
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('t', 'v2'), snapshot)).toBe(true)
  })

  it('does not flag an unchanged sink', () => {
    const snapshot = snapshotFor(postgisPayload('t', 'v1'), 'sink-1')
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('t', 'v1'), snapshot)).toBe(false)
  })

  it('does not flag a brand-new sink (no entity, no table to lose)', () => {
    // Not in the snapshot at all → new node.
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('t', 'v1'), {})).toBe(false)
  })

  it('does not flag a sink whose snapshot entry has no entityId', () => {
    const snapshot = snapshotFor(postgisPayload('old', 'v1'), null)
    expect(isDestructiveDataSinkChange('node-1', postgisPayload('new', 'v1'), snapshot)).toBe(false)
  })
})

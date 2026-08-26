import { describe, expect, it } from 'vitest'

import type { DataSink } from '@/types/datasinks'

import type { DataSourceNodeData, FrostNodeData, GeoPersistenceNodeData, MappingNodeData } from '../_types/nodes'
import { hydrateStylesFromCoreModel } from './sessionService'

const SINK_URN = 'urn:core:platform:civitas:datasink:common:sink:a1b2c3d4e5:1.0.0'
const SOURCE_URN = 'urn:core:platform:civitas:datasource:common:feed:x1y2z3a4b5:1.0.0'
const MAPPING_URN = 'urn:core:standard:openurbanapps:mapping:environment:luftmessungzuobservation:uktwf8tdur:1.0.0'

/** A dataset sink as useGetDataSinks serves it, reduced to the fields hydration reads. */
const dataSink = (overrides: Partial<DataSink> & Pick<DataSink, 'dataSinkType'>): DataSink =>
  ({
    id: 'sink-entity-1',
    configurationUrn: SINK_URN,
    configuration: {},
    ...overrides,
  }) as unknown as DataSink

const coreNode = (node: Record<string, unknown>) => ({ nodes: [node], edges: [] })

describe('hydrateStylesFromCoreModel', () => {
  it('hydrates a sink whose ref matches a FROST sink as a frost node with its entity', () => {
    const styles = hydrateStylesFromCoreModel(
      coreNode({ id: 'n-sink', kind: 'sink', label: 'Sensordaten-Speicher', sinkRef: SINK_URN }),
      [dataSink({ dataSinkType: 'FROST' })],
    )

    const node = styles?.nodes[0]
    expect(node?.type).toBe('frost')
    const data = node?.data as FrostNodeData
    expect(data.entityId).toBe('sink-entity-1')
    expect(data.configurationUrn).toBe(SINK_URN)
    expect(data.configured).toBe(true)
  })

  it('hydrates a sink whose ref matches a POSTGIS sink with table and structure details', () => {
    const styles = hydrateStylesFromCoreModel(
      coreNode({ id: 'n-sink', kind: 'sink', label: 'Tabelle', sinkRef: SINK_URN }),
      [
        dataSink({
          dataSinkType: 'POSTGIS',
          configuration: {
            tableName: 'verkehrsmessung',
            dataStructureVersion: { id: 'version-1', dataStructureId: 'structure-1' },
          } as unknown as DataSink['configuration'],
        }),
      ],
    )

    const node = styles?.nodes[0]
    expect(node?.type).toBe('geoPersistence')
    const data = node?.data as GeoPersistenceNodeData
    expect(data.entityId).toBe('sink-entity-1')
    expect(data.tableName).toBe('verkehrsmessung')
    expect(data.dataStructureVersionId).toBe('structure-1/version-1')
    expect(data.configured).toBe(true)
  })

  it('falls back to an unconfigured geoPersistence node when no sink matches the ref', () => {
    const styles = hydrateStylesFromCoreModel(
      coreNode({ id: 'n-sink', kind: 'sink', label: 'Tabelle', sinkRef: SINK_URN }),
      [],
    )

    const node = styles?.nodes[0]
    expect(node?.type).toBe('geoPersistence')
    const data = node?.data as GeoPersistenceNodeData
    expect(data.entityId).toBeUndefined()
    expect(data.tableName).toBe('')
    expect(data.configured).toBe(false)
  })

  it('marks a source configured exactly when it carries a sourceRef', () => {
    const withRef = hydrateStylesFromCoreModel(
      coreNode({ id: 'n-source', kind: 'source', label: 'Feed', sourceRef: SOURCE_URN }),
    )
    expect((withRef?.nodes[0]?.data as DataSourceNodeData).configured).toBe(true)

    const withoutRef = hydrateStylesFromCoreModel(coreNode({ id: 'n-source', kind: 'source', label: 'Feed' }))
    expect((withoutRef?.nodes[0]?.data as DataSourceNodeData).configured).toBe(false)
  })

  it('marks a mapping configured through its ref and keeps the editor config empty', () => {
    const styles = hydrateStylesFromCoreModel(
      coreNode({ id: 'n-mapping', kind: 'mapping', label: 'Mapping', mappingRef: MAPPING_URN }),
    )

    const data = styles?.nodes[0]?.data as MappingNodeData
    expect(data.configured).toBe(true)
    expect(data.mappingRef).toBe(MAPPING_URN)
    expect(data.mappingLogicalUrn).toBe(MAPPING_URN.split(':').slice(0, 8).join(':'))
    expect(data.mappingConfig).toEqual({ fields: {}, positions: {} })
    // No snapshot: validateMappingCoversRequiredTargetFields skips exactly this installed shape.
    expect(data.targetRequiredFields).toBeUndefined()
  })

  it('hydrates start and end nodes registry-compatible (nodeType, configured)', () => {
    const styles = hydrateStylesFromCoreModel({
      nodes: [
        { id: 'n-start', kind: 'start', label: 'Start' },
        { id: 'n-end', kind: 'end', label: 'Ende' },
      ],
      edges: [],
    })

    expect(styles?.nodes[0]?.data).toMatchObject({ nodeType: 'start', configured: true })
    expect(styles?.nodes[1]?.data).toMatchObject({ nodeType: 'end', configured: true })
  })

  it('derives cron configured-ness from the expression validity', () => {
    const valid = hydrateStylesFromCoreModel(
      coreNode({ id: 'n-cron', kind: 'cron', cronExpression: '0 0 6 * * ?' }),
    )
    expect(valid?.nodes[0]?.data.configured).toBe(true)

    const invalid = hydrateStylesFromCoreModel(coreNode({ id: 'n-cron', kind: 'cron', cronExpression: 'täglich' }))
    expect(invalid?.nodes[0]?.data.configured).toBe(false)
  })
})

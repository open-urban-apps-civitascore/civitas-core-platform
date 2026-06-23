import { describe, expect, it } from 'vitest'

import { STATUS_TYPES } from '@/types/common'
import { DataSink, DATASINK_TYPES } from '@/types/datasinks'
import { DATASTRUCTURE_VERSION_SOURCE, DatastructureVersion } from '@/types/datastructures'
import { LayerFormData } from '@/types/namedApis'

import { getNativeCRSFromDataSink, mapFormLayerToPayload } from './namedApis'

const makeLayer = (overrides: Partial<LayerFormData> = {}): LayerFormData => ({
  id: '00000000-0000-0000-0000-000000000001',
  title: 'Layer 1',
  layerName: 'layer_1',
  description: '',
  keywords: [],
  dataSinkId: '00000000-0000-0000-0000-000000000002',
  attribute: ['geom'],
  cqlFilter: '',
  geometryColumnRef: 'geom',
  nativeCRS: 'EPSG:4326',
  crs: 'EPSG:4326',
  bboxAutoCalculate: false,
  nativeBoundingBox: { minX: '-180', minY: '-90', maxX: '180', maxY: '90', crs: 'EPSG:4326' },
  latLonBoundingBox: { minX: '-180', minY: '-90', maxX: '180', maxY: '90', crs: 'EPSG:4326' },
  defaultStyleId: null,
  alternativeStyleIds: [],
  ...overrides,
})

const makeDataSink = (): DataSink => ({
  id: '00000000-0000-0000-0000-000000000010',
  datasetId: '00000000-0000-0000-0000-000000000020',
  pipelineId: '00000000-0000-0000-0000-000000000030',
  dataSinkType: DATASINK_TYPES.POSTGIS,
  configuration: {
    tableName: 'my_table',
    dataStructureVersion: {
      id: '00000000-0000-0000-0000-000000000040',
      version: '1.0.0',
      description: null,
      dataStructureVersionStatus: STATUS_TYPES.AVAILABLE,
      dataStructureVersionSource: DATASTRUCTURE_VERSION_SOURCE.OWN,
      dataStructureId: '00000000-0000-0000-0000-000000000050',
      createdAt: '2026-01-01T00:00:00',
      modifiedAt: '2026-01-01T00:00:00',
    },
  },
  createdAt: '2026-01-01T00:00:00',
  modifiedAt: '2026-01-01T00:00:00',
})

const makeDatastructureVersion = (styles: DatastructureVersion['styles'] = null): DatastructureVersion => ({
  id: '00000000-0000-0000-0000-000000000040',
  version: '1.0.0',
  description: null,
  dataStructureVersionStatus: STATUS_TYPES.AVAILABLE,
  dataStructureVersionSource: DATASTRUCTURE_VERSION_SOURCE.OWN,
  modelAtlasUri: null,
  modelName: null,
  model: null,
  styles,
  inUse: true,
  dataStructure: { id: '00000000-0000-0000-0000-000000000050', name: 'Structure 1' },
  createdAt: '2026-01-01T00:00:00',
  modifiedAt: '2026-01-01T00:00:00',
})

const stylesWithCRS = {
  nodes: [
    {
      data: {
        element: {
          type: 'class' as const,
          id: '00000000-0000-0000-0000-000000000060',
          name: 'MyClass',
          attributes: [
            {
              id: '00000000-0000-0000-0000-000000000070',
              name: 'geom',
              type: { name: 'Geometry' },
              visibility: 'public' as const,
              meta: { gisInfo: { crs: 'EPSG:25832' } },
            },
          ],
          operations: [],
        },
      },
    },
  ],
} as unknown as DatastructureVersion['styles']

describe('mapFormLayerToPayload', () => {
  it('computes latLonBoundingBox from nativeBoundingBox and CRS', () => {
    const [result] = mapFormLayerToPayload([makeLayer({ crs: 'EPSG:4326' })])
    expect(result.latLonBoundingBox).toEqual({ minX: -180, minY: -90, maxX: 180, maxY: 90, crs: 'EPSG:4326' })
  })

  it('sets latLonBoundingBox to null when CRS is not supported', () => {
    const [result] = mapFormLayerToPayload([makeLayer({ crs: 'EPSG:9999' })])
    expect(result.latLonBoundingBox).toBeNull()
  })

  it('sets latLonBoundingBox to null when nativeBoundingBox contains non-numeric coords', () => {
    const [result] = mapFormLayerToPayload([
      makeLayer({
        nativeBoundingBox: { minX: 'not-a-number', minY: '-90', maxX: '180', maxY: '90', crs: 'EPSG:4326' },
      }),
    ])
    expect(result.latLonBoundingBox).toBeNull()
  })

  it('sets nativeBoundingBox and latLonBoundingBox to null when bboxAutoCalculate is true', () => {
    const [result] = mapFormLayerToPayload([makeLayer({ bboxAutoCalculate: true })])
    expect(result.nativeBoundingBox).toBeNull()
    expect(result.latLonBoundingBox).toBeNull()
  })

  it('sets defaultStyleId to null when value is "none"', () => {
    const [result] = mapFormLayerToPayload([makeLayer({ defaultStyleId: 'none' })])
    expect(result.defaultStyleId).toBeNull()
  })

  it('sets defaultStyleId to null when value is empty', () => {
    const [result] = mapFormLayerToPayload([makeLayer({ defaultStyleId: '' })])
    expect(result.defaultStyleId).toBeNull()
  })

  it('preserves defaultStyleId when it has a real value', () => {
    const styleId = '00000000-0000-0000-0000-000000000099'
    const [result] = mapFormLayerToPayload([makeLayer({ defaultStyleId: styleId })])
    expect(result.defaultStyleId).toBe(styleId)
  })
})

describe('getNativeCRSFromDataSink', () => {
  it('returns the CRS from the matching data sink and datastructure', () => {
    const result = getNativeCRSFromDataSink(
      '00000000-0000-0000-0000-000000000010',
      [makeDataSink()],
      [makeDatastructureVersion(stylesWithCRS)],
    )
    expect(result).toBe('EPSG:25832')
  })

  it('returns empty string when dataSinkId has no match', () => {
    const result = getNativeCRSFromDataSink(
      '00000000-0000-0000-0000-000000000099',
      [makeDataSink()],
      [makeDatastructureVersion(stylesWithCRS)],
    )
    expect(result).toBe('')
  })

  it('returns empty string when no datastructure matches the data sink', () => {
    const result = getNativeCRSFromDataSink('00000000-0000-0000-0000-000000000010', [makeDataSink()], [])
    expect(result).toBe('')
  })

  it('returns empty string when no attribute has a CRS', () => {
    const stylesWithoutCRS = {
      nodes: [
        {
          data: {
            element: {
              type: 'class' as const,
              id: '00000000-0000-0000-0000-000000000060',
              name: 'MyClass',
              attributes: [
                {
                  id: '00000000-0000-0000-0000-000000000070',
                  name: 'name',
                  type: { name: 'String' },
                  visibility: 'public' as const,
                },
              ],
              operations: [],
            },
          },
        },
      ],
    } as unknown as DatastructureVersion['styles']

    const result = getNativeCRSFromDataSink(
      '00000000-0000-0000-0000-000000000010',
      [makeDataSink()],
      [makeDatastructureVersion(stylesWithoutCRS)],
    )
    expect(result).toBe('')
  })
})

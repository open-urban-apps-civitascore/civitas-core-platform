import { Datasink } from '@/types/datasinks'
import { DatastructureVersion, DatastructureVersionSummary } from '@/types/datastructures'
import { Layer } from '@/types/namedApis'

export const mockLayerList: Layer[] = [
  {
    id: '00000000-0000-0000-0000-000000000001',
    datasetId: '00000000-0000-0000-0000-000000000002',
    dataSinkId: '00000000-0000-0000-0000-000000000003',
    layerName: 'air_quality_points',
    title: 'Air Quality (Punkte)',
    description: 'Stündliche Messungen',
    keywords: ['luftqualität', 'feinstaub'],
    attribute: ['station_id', 'temperature', 'geom'],
    geometryColumnRef: 'geom',
    cqlFilter: null,
    alternativeStyleIds: [],
    crs: 'EPSG:4326',
    defaultStyleId: null,
    geometryType: 'POINT',
    nativeCRS: 'EPSG:25832',
    bboxAutoCalculate: false,
    nativeBoundingBox: { minx: 5.8, miny: 47.2, maxx: 15.0, maxy: 55.0, crs: 'EPSG:25832' },
    latLonBoundingBox: { minx: 5.8, miny: 47.2, maxx: 15.0, maxy: 55.0, crs: 'EPSG:4326' },
    createdAt: '2026-05-04T10:30:00',
    modifiedAt: '2026-05-04T10:30:00',
  },
]

export const mockDatastructureVersionSummary: DatastructureVersionSummary = {
  id: '00000000-0000-0000-0000-000000000010',
  version: '1.0.0',
  description: null,
  dataStructureVersionStatus: 'AVAILABLE',
  dataStructureVersionSource: 'OWN',
  dataStructureId: '00000000-0000-0000-0000-000000000011',
  createdAt: '2026-05-04T10:00:00',
  modifiedAt: '2026-05-04T10:00:00',
}

export const mockDatasink: Datasink = {
  id: '00000000-0000-0000-0000-000000000003',
  datasetId: '00000000-0000-0000-0000-000000000002',
  pipelineId: '00000000-0000-0000-0000-000000000004',
  dataSinkType: 'POSTGIS',
  configuration: {
    tableName: 'air_quality_points',
    dataStructureVersion: mockDatastructureVersionSummary,
  },
  createdAt: '2026-05-04T10:00:00',
  modifiedAt: '2026-05-04T10:00:00',
}

export const mockDatastructureVersion: DatastructureVersion = {
  id: '00000000-0000-0000-0000-000000000010',
  version: '1.0.0',
  description: null,
  dataStructureVersionStatus: 'AVAILABLE',
  dataStructureVersionSource: 'OWN',
  modelAtlasUri: null,
  modelName: null,
  model: null,
  styles: null,
  inUse: true,
  dataStructure: { id: '00000000-0000-0000-0000-000000000011', name: 'Air Quality' },
  createdAt: '2026-05-04T10:00:00',
  modifiedAt: '2026-05-04T10:00:00',
}

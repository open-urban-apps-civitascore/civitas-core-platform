import { UMLDiagram } from '@/components/uml-modeler/types/diagram'
import { Datasink } from '@/types/datasinks'
import { DatastructureVersion, DatastructureVersionSummary } from '@/types/datastructures'
import { Layer, Style } from '@/types/namedApis'

export const mockStyleList: Style[] = [
  {
    id: '00000000-0000-0000-0000-000000000020',
    datasetId: '00000000-0000-0000-0000-000000000002',
    name: 'heatmap',
    sldContent: '<?xml version="1.0"?><StyledLayerDescriptor></StyledLayerDescriptor>',
    inUse: true,
    createdAt: '2026-05-04T10:30:00',
    modifiedAt: '2026-05-04T10:30:00',
  },
]

export const mockLayerList: Layer[] = [
  {
    id: '00000000-0000-0000-0000-000000000001',
    datasetId: '00000000-0000-0000-0000-000000000002',
    dataSinkId: '00000000-0000-0000-0000-000000000003',
    layerName: 'air_quality_points',
    title: 'Air Quality',
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
    nativeBoundingBox: { minX: 5.8, minY: 47.2, maxX: 15.0, maxY: 55.0, crs: 'EPSG:25832' },
    latLonBoundingBox: { minX: 5.8, minY: 47.2, maxX: 15.0, maxY: 55.0, crs: 'EPSG:4326' },
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
    tableName: 'air_quality',
    dataStructureVersion: mockDatastructureVersionSummary,
  },
  createdAt: '2026-05-04T10:00:00',
  modifiedAt: '2026-05-04T10:00:00',
}

export const mockDatastructureVersionSummary2: DatastructureVersionSummary = {
  id: '00000000-0000-0000-0000-000000000012',
  version: '1.0.0',
  description: null,
  dataStructureVersionStatus: 'AVAILABLE',
  dataStructureVersionSource: 'OWN',
  dataStructureId: '00000000-0000-0000-0000-000000000013',
  createdAt: '2026-05-04T10:00:00',
  modifiedAt: '2026-05-04T10:00:00',
}

export const mockDatasink2: Datasink = {
  id: '00000000-0000-0000-0000-000000000005',
  datasetId: '00000000-0000-0000-0000-000000000002',
  pipelineId: '00000000-0000-0000-0000-000000000006',
  dataSinkType: 'POSTGIS',
  configuration: {
    tableName: 'green_spaces',
    dataStructureVersion: mockDatastructureVersionSummary2,
  },
  createdAt: '2026-05-04T10:00:00',
  modifiedAt: '2026-05-04T10:00:00',
}

export const mockDatastructureVersion2: DatastructureVersion = {
  id: '00000000-0000-0000-0000-000000000012',
  version: '1.0.0',
  description: null,
  dataStructureVersionStatus: 'AVAILABLE',
  dataStructureVersionSource: 'OWN',
  modelAtlasUri: null,
  modelName: null,
  model: null,
  styles: {
    id: '00000000-0000-0000-0000-000000000032',
    name: 'Green Spaces',
    nodes: [
      {
        id: '00000000-0000-0000-0000-000000000033',
        type: 'class',
        position: { x: 0, y: 0 },
        data: {
          label: 'GreenSpaces',
          element: {
            id: '00000000-0000-0000-0000-000000000033',
            type: 'class',
            name: 'GreenSpaces',
            attributes: [
              { id: 'attr-4', name: 'area_id', type: 'Uuid', visibility: 'public' },
              { id: 'attr-5', name: 'name', type: 'String', visibility: 'public' },
              { id: 'attr-6', name: 'area_sqm', type: 'Double', visibility: 'public' },
              {
                id: 'attr-7',
                name: 'boundary',
                type: 'Polygon',
                visibility: 'public',
                meta: { gisInfo: { crs: 'EPSG:4326' } },
              },
            ],
            operations: [],
          },
        },
      },
    ],
    edges: [],
    lastModified: new Date('2026-05-04T10:00:00'),
    isDirty: false,
  } satisfies UMLDiagram,
  inUse: true,
  dataStructure: { id: '00000000-0000-0000-0000-000000000013', name: 'Green Spaces' },
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
  styles: {
    id: '00000000-0000-0000-0000-000000000030',
    name: 'Air Quality',
    nodes: [
      {
        id: '00000000-0000-0000-0000-000000000031',
        type: 'class',
        position: { x: 0, y: 0 },
        data: {
          label: 'AirQualityPoints',
          element: {
            id: '00000000-0000-0000-0000-000000000031',
            type: 'class',
            name: 'AirQualityPoints',
            attributes: [
              { id: 'attr-1', name: 'station_id', type: 'Uuid', visibility: 'public' },
              { id: 'attr-2', name: 'temperature', type: 'Float', visibility: 'public' },
              {
                id: 'attr-3',
                name: 'geom',
                type: 'Point',
                visibility: 'public',
                meta: { gisInfo: { crs: 'EPSG:25832' } },
              },
            ],
            operations: [],
          },
        },
      },
    ],
    edges: [],
    lastModified: new Date('2026-05-04T10:00:00'),
    isDirty: false,
  } satisfies UMLDiagram,
  inUse: true,
  dataStructure: { id: '00000000-0000-0000-0000-000000000011', name: 'Air Quality' },
  createdAt: '2026-05-04T10:00:00',
  modifiedAt: '2026-05-04T10:00:00',
}

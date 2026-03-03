import { Datasource } from '@/types/datasources'

export const mockDatasources: Datasource[] = [
  {
    id: '00000000-0000-0000-0000-000000000001',
    createdAt: '2024-06-15T10:00:00Z',
    modifiedAt: '2024-09-10T08:00:00Z',
    name: 'MQTT Sensor Data',
    description: 'Temperature sensor data via MQTT',
    dataSourceStatus: 'DRAFT',
    connectorType: 'MQTT',
    configuration: null,
    dataStructureVersion: null,
  },
  {
    id: '00000000-0000-0000-0000-000000000002',
    createdAt: '2024-03-01T12:00:00Z',
    modifiedAt: '2024-08-20T14:30:00Z',
    name: 'SQL Database Import',
    description: 'Importing data from PostgreSQL',
    dataSourceStatus: 'AVAILABLE',
    connectorType: 'SQL',
    configuration: null,
    dataStructureVersion: null,
  },
  {
    id: '00000000-0000-0000-0000-000000000003',
    createdAt: '2024-01-10T09:00:00Z',
    modifiedAt: '2024-01-10T09:00:00Z',
    name: 'Empty Datasource',
    description: '',
    dataSourceStatus: 'DRAFT',
    connectorType: null,
    configuration: null,
    dataStructureVersion: null,
  },
]

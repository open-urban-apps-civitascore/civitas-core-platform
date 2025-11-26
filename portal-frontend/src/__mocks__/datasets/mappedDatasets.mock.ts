import { DatasetTableData } from '@/types/datasets'

export const mappedDatasets: DatasetTableData[] = [
  {
    id: '1',
    name: 'Test Dataset 1',
    dataspace: 'Dataspace 1',
    department: 'Stadtentwicklung',
    creator: ['Test User1'],
    lastUpdated: '2023-09-10T08:00:00Z',
    status: null,
    releaseProcess: null,
    distribution: null,
  },
  {
    id: '2',
    name: 'Test Dataset 2',
    dataspace: '',
    department: 'Verkehrsplanung',
    creator: ['Test User2'],
    lastUpdated: '2023-01-01T08:00:00Z',
    status: 'open',
    releaseProcess: null,
    distribution: {
      format: 'DOCX',
      title: 'Superset',
      url: 'https://superset.de',
    },
  },
]

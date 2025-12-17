import { DatasetTableData } from '@/types/datasets'

export const mappedDatasets: DatasetTableData[] = [
  {
    id: '1',
    name: 'Test Dataset 1',
    dataspace: { id: '1', name: 'Dataspace 1' },
    contact: { id: '1', firstName: 'Test', lastName: 'User1' },
    lastUpdated: '2023-09-10T08:00:00Z',
    status: 'draft',
    access: true,
  },
  {
    id: '2',
    name: 'Test Dataset 2',
    dataspace: null,
    contact: { id: '2', firstName: 'Test', lastName: 'User2' },
    lastUpdated: '2023-01-01T08:00:00Z',
    status: 'published',
    access: false,
  },
]

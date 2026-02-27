import { DATASET_STATUS_TYPES, DatasetTableData } from '@/types/datasets'

export const mappedDatasets: DatasetTableData[] = [
  {
    id: '1',
    name: 'Test Dataset 1',
    createdBy: {
      id: 'user1',
      name: 'Max Mustermann',
    },
    modifiedAt: '2023-09-10T08:00:00Z',
    dataSetStatus: DATASET_STATUS_TYPES.DRAFT,
  },
  {
    id: '2',
    name: 'Test Dataset 2',
    createdBy: {
      id: 'user2',
      name: 'Erika Musterfrau',
    },
    modifiedAt: '2023-01-01T08:00:00Z',
    dataSetStatus: DATASET_STATUS_TYPES.AVAILABLE,
  },
]

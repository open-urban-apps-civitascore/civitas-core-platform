import { getDataset } from '@/app/services/api/datasets/serverRequests'

import { DatasetOverview } from './overview/components/DatasetOverview'

interface DatasetPageProps {
  params: Promise<{ datasetId: string }>
}

const DatasetPage = async (props: DatasetPageProps) => {
  const { params } = props
  const { datasetId } = await params

  const { data: dataset } = await getDataset(datasetId)

  // needs to be changed to real assignments when access management is implemented, currently only for testing purposes
  const mockAssignements = [
    {
      id: '1',
      group: { id: '1', name: 'Testgruppe' },
      role: { id: '1', name: 'TestRolle' },
      createdAt: '2024-01-01T00:00:00Z',
      modifiedAt: '2024-01-01T00:00:00Z',
      scopeType: 'DATASET',
      scope: { id: datasetId, name: dataset.name },
    },
  ]

  return (
    <DatasetOverview
      testId="datasetPage"
      dataset={dataset}
      groupCount={mockAssignements.filter(a => a.group).length}
      roleCount={mockAssignements.filter(a => a.role).length}
    />
  )
}

export default DatasetPage

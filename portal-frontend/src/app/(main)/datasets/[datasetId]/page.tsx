import { getDataset, getDatasetAssignments } from '@/app/services/api/datasets/serverRequests'

import { DatasetOverview } from './overview/components/DatasetOverview'

interface DatasetPageProps {
  params: Promise<{ datasetId: string }>
}

const DatasetPage = async (props: DatasetPageProps) => {
  const { params } = props
  const { datasetId } = await params

  const { data: dataset } = await getDataset(datasetId)

  const { data: datasetAssignments } = await getDatasetAssignments(datasetId)

  const getUniqueValuesCount = (entity: 'group' | 'role') => {
    const uniqueGroups = new Set(datasetAssignments.map(a => a[entity]?.id))

    return uniqueGroups.size
  }

  const roleCount = getUniqueValuesCount('role')
  const groupCount = getUniqueValuesCount('group')

  return <DatasetOverview testId="datasetPage" dataset={dataset} groupCount={groupCount} roleCount={roleCount} />
}

export default DatasetPage

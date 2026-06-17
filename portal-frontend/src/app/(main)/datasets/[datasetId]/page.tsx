import { getDatapools } from '@/app/services/api/datapools/serverRequests'
import { getDataset, getDatasetAssignments } from '@/app/services/api/datasets/serverRequests'

import { DatasetOverview } from './overview/components/DatasetOverview'

interface DatasetPageProps {
  params: Promise<{ datasetId: string }>
}

const DatasetPage = async (props: DatasetPageProps) => {
  const { params } = props
  const { datasetId } = await params

  const [dataset, datasetAssignments, datapools] = await Promise.all([
    getDataset(datasetId),
    getDatasetAssignments(datasetId),
    getDatapools(),
  ])

  const getUniqueValuesCount = (entity: 'group' | 'role') => {
    const uniqueGroups = new Set(datasetAssignments.data.map(a => a[entity]?.id))

    return uniqueGroups.size
  }

  const roleCount = getUniqueValuesCount('role')
  const groupCount = getUniqueValuesCount('group')

  const datapoolOptions = datapools.data.map(datapool => ({
    value: datapool.id,
    label: datapool.name,
  }))

  return (
    <DatasetOverview
      testId="datasetPage"
      dataset={dataset.data}
      groupCount={groupCount}
      roleCount={roleCount}
      datapoolOptions={datapoolOptions}
    />
  )
}

export default DatasetPage

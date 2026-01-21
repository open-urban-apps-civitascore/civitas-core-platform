import { getDataset } from '@/app/services/api/datasets/serverRequests'
import { getDataspaces } from '@/app/services/api/dataspaces/serverRequests'

import { DatasetOverview } from '../components/overview/DatasetOverview'
import { mapDatasetToFormData } from '../utils/mappers'
interface DatasetPageProps {
  params: Promise<{ datasetId: string }>
}

const DatasetPage = async (props: DatasetPageProps) => {
  const { params } = props
  const { datasetId } = await params

  const getData = async () => {
    try {
      const [{ data: datasetData }, { data: dataspacesData }] = await Promise.all([
        getDataset(datasetId),
        getDataspaces(),
      ])
      return {
        dataset: mapDatasetToFormData(datasetData),
        dataspaces: dataspacesData.map(dataspace => ({ value: dataspace.id, label: dataspace.name })),
      }
    } catch (error) {
      throw new Error(`An error occurred while loading data: ${error}`)
    }
  }

  const { dataset, dataspaces } = await getData()

  return (
    <DatasetOverview
      testId="datasetPage"
      dataset={dataset}
      dataspaces={dataspaces}
      datasources={['Datasource 1', 'Datasource 2']}
      groups={[]}
      apis={['API 1', 'API 2']}
      hasMetadata
      persistence={[]}
      isEditMode={true}
    />
  )
}

export default DatasetPage

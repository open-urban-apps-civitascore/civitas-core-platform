import { DatasetFormData, DatasetResponse } from '@/types/datasets'
import { DataSpace } from '@/types/dataspaces'

import { DatasetOverview } from '../components/overview/DatasetOverview'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

export const transformDatasetToFormData = (dataset: DatasetResponse): DatasetFormData | null => {
  try {
    const data = {
      id: dataset.id,
      name: dataset.name,
      dataspace: dataset.dataspace?.id || '',
      description: dataset.description,
      tags: dataset.tags,
    }
    return data
  } catch (error) {
    console.error(dataset, error)
    return null
  }
}
interface DatasetPageProps {
  params: Promise<{ datasetId: string }>
}

const DatasetPage = async (props: DatasetPageProps) => {
  const { params } = props
  const { datasetId } = await params
  const getData = async () => {
    try {
      const [datasetResponse, dataspacesResponse] = await Promise.all([
        fetch(`${URL}/datasets/${datasetId}`, {
          cache: 'no-store',
        }),
        fetch(`${URL}/dataspaces`, {
          cache: 'no-store',
        }),
      ])
      if (!datasetResponse.ok || !dataspacesResponse.ok) {
        throw new Error('An error occurred while loading data')
      }
      const datasetData: DatasetResponse = await datasetResponse.json()
      const dataspacesData: DataSpace[] = await dataspacesResponse.json()
      return {
        dataset: {
          id: datasetData.id,
          name: datasetData.name,
          dataspace: datasetData.dataspace?.id || '',
          description: datasetData.description,
          tags: datasetData.tags,
        },
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

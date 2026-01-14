import { apiRequest } from '@/app/services/api/request/apiRequest'
import { getServerRequestHeaders } from '@/app/services/api/request/getServerRequestHeaders'
import { DatasetFormData } from '@/types/datasets'
import { DataSpace } from '@/types/dataspaces'

import { DatasetOverview } from '../components/overview/DatasetOverview'

export const defaultDataset: DatasetFormData = {
  id: '',
  dataspace: '',
  name: '',
  description: '',
  tags: [],
}

const CreateDatasetPage = async () => {
  const getDataspaces = async () => {
    try {
      const { data } = await apiRequest<DataSpace[]>({
        endpoint: `/dataspaces`,
        method: 'GET',
        headers: await getServerRequestHeaders(),
        errorMessage: 'An error occurred while fetching dataspaces.',
      })
      return data.map(dataspace => ({ value: dataspace.id, label: dataspace.name }))
    } catch (error) {
      throw new Error(`An error occurred while loading data: ${error}`)
    }
  }

  const dataspaces = await getDataspaces()

  return (
    <DatasetOverview
      testId="createDatasetPage"
      dataset={defaultDataset}
      dataspaces={dataspaces}
      datasources={[]}
      groups={[]}
      hasMetadata={false}
      apis={[]}
      persistence={[]}
      isEditMode={false}
    />
  )
}

export default CreateDatasetPage

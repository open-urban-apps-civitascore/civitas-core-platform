import { getDataspaces as getDataspacesRequest } from '@/app/services/api/dataspaces/serverRequests'
import { DatasetFormData } from '@/types/datasets'

import { DatasetOverview } from '../components/overview/DatasetOverview'

export const defaultDataset: DatasetFormData = {
  id: '',
  dataspace: '',
  name: '',
  description: '',
}

const CreateDatasetPage = async () => {
  const getDataspaces = async () => {
    try {
      const { data } = await getDataspacesRequest()
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

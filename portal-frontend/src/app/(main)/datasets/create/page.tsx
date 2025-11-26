import { DatasetFormData } from '@/types/datasets'
import { DataSpace } from '@/types/dataspaces'

import { DatasetOverview } from '../components/DatasetOverview'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

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
      const dataspacesResponse = await fetch(`${URL}/dataspaces`, {
        cache: 'no-store',
      })
      if (!dataspacesResponse) {
        throw new Error('An error occurred while loading data')
      }
      const dataspacesData: DataSpace[] = await dataspacesResponse.json()
      return dataspacesData.map(dataspace => ({ value: dataspace.id, label: dataspace.name }))
    } catch (error) {
      throw new Error(`An error occurred while loading data: ${error}`)
    }
  }

  const dataspaces = await getDataspaces()

  return <DatasetOverview dataset={defaultDataset} dataspaces={dataspaces} isEditMode={false} />
}

export default CreateDatasetPage

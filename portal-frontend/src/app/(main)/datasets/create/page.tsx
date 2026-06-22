import { getDatapools } from '@/app/services/api/datapools/serverRequests'

import { DatasetCreateForm } from './components/DatasetCreateForm'

const CreateDatasetPage = async () => {
  const { data: datapools } = await getDatapools()

  const datapoolOptions = datapools.map(datapool => ({
    value: datapool.id,
    label: datapool.name,
  }))

  return <DatasetCreateForm datapoolOptions={datapoolOptions} />
}

export default CreateDatasetPage

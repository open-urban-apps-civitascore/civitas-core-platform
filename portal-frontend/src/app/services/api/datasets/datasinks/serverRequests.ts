import { serverFetch } from '@/lib/serverFetch'
import { DataSink } from '@/types/datasinks'

export const getDataSinks = async (datasetId: string) => {
  try {
    return await serverFetch<DataSink[]>({
      endpoint: `/datasets/${datasetId}/datasinks`,
      method: 'GET',
      isApiBackend: true,
    })
  } catch (error) {
    console.error(`An error occurred while fetching data sinks of dataset ${datasetId}.`, error)
    throw new Error('An error occurred while fetching data sinks.')
  }
}

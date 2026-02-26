import { serverFetch } from '@/lib/serverFetch'
import { DatastructureVersion } from '@/types/datastructures'

export const getDatastructureVersion = async (datastructureId: string, versionId: string) => {
  try {
    return await serverFetch<DatastructureVersion[]>({
      endpoint: `/datastructures/${datastructureId}/versions/${versionId}`,
      method: 'GET',
      isApiBackend: true,
    })
  } catch (error) {
    console.error(`An error occurred while fetching datastructure version.`, error)
    throw new Error('An error occurred while fetching datastructure version.')
  }
}

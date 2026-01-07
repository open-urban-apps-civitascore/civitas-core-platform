import { AxiosRequestConfig } from 'axios'

import { axiosClient } from '../client/client'

export const fetchGroups = async (requestConfig: Promise<AxiosRequestConfig>, params?: URLSearchParams) => {
  try {
    const config = await requestConfig
    const { data } = await axiosClient.get(`/api/groups?${params?.toString() || ''}`, config)
    return data
  } catch (error) {
    console.error(error)
    throw new Error(`Failed to fetch groups: ${error}`)
  }
}

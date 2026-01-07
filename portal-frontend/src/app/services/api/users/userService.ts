import { AxiosRequestConfig } from 'axios'

import { axiosClient } from '../client/client'

export const fetchUsers = async (requestConfig: Promise<AxiosRequestConfig>, params?: URLSearchParams) => {
  try {
    const config = await requestConfig
    const { data } = await axiosClient.get(`/api/users?${params?.toString() || ''}`, config)
    return data
  } catch (error) {
    console.error(error)
    throw new Error(`Failed to fetch users: ${error}`)
  }
}

export const fetchUser = async (requestConfig: Promise<AxiosRequestConfig>, userId: string) => {
  try {
    const config = await requestConfig
    const { data } = await axiosClient.get(`/api/users/${userId}`, config)
    return data
  } catch (error) {
    console.error(error)
    throw new Error(`Failed to fetch user: ${error}`)
  }
}

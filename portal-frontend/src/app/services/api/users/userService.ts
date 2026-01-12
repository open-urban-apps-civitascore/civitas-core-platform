import { AxiosRequestConfig } from 'axios'

import { CreateUserData, UpdateUserData, UserResponse } from '@/types/users'

import { axiosClient } from '../client/client'

export const fetchUsers = async (requestConfig: Promise<AxiosRequestConfig>, params?: URLSearchParams) => {
  try {
    const config = await requestConfig
    const { data } = await axiosClient.get(`/api/users?${params?.toString() || ''}`, config)
    return data
  } catch (error) {
    console.error(`Failed to fetch users: ${error}`)
    throw new Error(`Failed to fetch users: ${error}`)
  }
}

export const fetchUser = async (requestConfig: Promise<AxiosRequestConfig>, userId: string) => {
  try {
    const config = await requestConfig
    const { data } = await axiosClient.get(`/api/users/${userId}`, config)
    return data
  } catch (error) {
    console.error(`Failed to fetch user: ${error}`)
    throw new Error(`Failed to fetch user: ${error}`)
  }
}

export const createUser = async (requestConfig: Promise<AxiosRequestConfig>, userData: CreateUserData) => {
  try {
    const config = await requestConfig
    const response = await axiosClient.post(`/api/users`, userData, {
      ...config,
    })
    const data = response.data
    console.log('successfully created user')
    return data as UserResponse
  } catch (error) {
    console.error(`An error occurred while creating new user: ${error}`)
    throw new Error(`An error occurred while creating new user: ${error}`)
  }
}

export const updateUser = async (requestConfig: Promise<AxiosRequestConfig>, updateUserData: UpdateUserData) => {
  try {
    const config = await requestConfig
    const response = await axiosClient.put(`/api/users/${updateUserData.id}`, updateUserData, {
      ...config,
    })
    const data = response.data
    console.log('successfully updated user')
    return data as UserResponse
  } catch (error) {
    console.error(`An error occurred while updating the user: ${error}`)
    throw new Error(`An error occurred while updating the user: ${error}`)
  }
}

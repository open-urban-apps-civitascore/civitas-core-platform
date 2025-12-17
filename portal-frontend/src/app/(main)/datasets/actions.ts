'use server'

import { headers } from 'next/headers'

import { DatasetResponse } from '@/types/datasets'

export const createDataset = async (datasetData: DatasetResponse) => {
  console.log('creating dataset')
  try {
    const response = await fetch(`${process.env.NEXTAUTH_URL}/api/datasets`, {
      method: 'POST',
      headers: {
        cookie: (await headers()).get('cookie') || '',
      },
      body: JSON.stringify(datasetData),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`)
    }
    const data: DatasetResponse = await response.json()
    console.log('successfully created dataset:', data)
    return data
  } catch (error) {
    console.error('An error occurred while creating new dataset:', error)
    throw new Error(`An error occurred while creating new dataset:, ${error}`)
  }
}

export const updateDataset = async (updateDatasetData: Partial<DatasetResponse>) => {
  try {
    const response = await fetch(`${process.env.NEXTAUTH_URL}/api/datasets/${updateDatasetData.id}`, {
      method: 'PATCH',
      headers: {
        cookie: (await headers()).get('cookie') || '',
      },
      body: JSON.stringify(updateDatasetData),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! ${JSON.stringify(response)}`)
    }

    const data = await response.json()
    console.log('successfully updated dataset:', data)
  } catch (error) {
    console.error('An error occurred while updating the dataset:', error)
    throw new Error(`An error occurred while updating dataset:, ${error}`)
  }
}

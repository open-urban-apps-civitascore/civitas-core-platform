'use server'

import { DatasetFormData, DatasetOverviewData } from '@/types/datasets'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

export const createDataset = async (datasetData: DatasetOverviewData) => {
  console.log('creating dataset')
  try {
    const response = await fetch(`${URL}/datasets`, {
      method: 'POST',
      headers: {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(datasetData),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`)
    }
    const data: DatasetOverviewData = await response.json()
    console.log('successfully created dataset:', data)
    return data
  } catch (error) {
    console.error('An error occurred while creating new dataset:', error)
    throw new Error(`An error occurred while creating new dataset:, ${error}`)
  }
}

export const updateDataset = async (updateDatasetData: DatasetFormData) => {
  try {
    const response = await fetch(`${URL}/datasets/${updateDatasetData.id}`, {
      method: 'PATCH',
      headers: {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
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
  }
}

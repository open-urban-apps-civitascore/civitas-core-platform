'use server'

import { CreateGroupBaseInfoData, UpdateGroupBaseInfoData } from '@/types/groups'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

export const createGroup = async (groupData: CreateGroupBaseInfoData) => {
  try {
    const response = await fetch(`${URL}/groups`, {
      method: 'POST',
      headers: {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(groupData),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`)
    }
    const data = await response.json()
    console.log('successfully created group:', data)
  } catch (error) {
    console.error('An error occurred while creating new group:', error)
  }
}

export const updateGroup = async (updateGroupData: UpdateGroupBaseInfoData) => {
  try {
    const response = await fetch(`${URL}/groups/${updateGroupData.id}`, {
      method: 'PUT',
      headers: {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(updateGroupData),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! ${JSON.stringify(response)}`)
    }

    const data = await response.json()
    console.log('successfully updated user:', data)
  } catch (error) {
    console.error('An error occurred while updateing the user:', error)
  }
}

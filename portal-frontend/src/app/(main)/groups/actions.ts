'use server'

import { CreateGroupData, UpdateGroupData } from '@/types/groups'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

export const createGroup = async (groupData: CreateGroupData) => {
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
    return data
  } catch (error) {
    console.error('An error occurred while creating new group:', error)
    throw new Error(JSON.stringify(error, null, 4))
  }
}

export const updateGroup = async (updateGroupData: UpdateGroupData) => {
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
    console.log('successfully updated group:', data)
    return data
  } catch (error) {
    console.error('An error occurred while updating the user:', error)
    throw new Error(JSON.stringify(error, null, 4))
  }
}

// this implementation has to be changed when the backend is impemented
// all users list shouldn't be fetched
// already assigned users have to keep their assignment date (handle in backend)
export const patchGroupUsers = async (groupId: string, updateUserData: { id: string; assignedAt: string }[]) => {
  try {
    const response = await fetch(`${URL}/groups/${groupId}`, {
      method: 'PATCH',
      headers: {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ users: updateUserData }),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! ${JSON.stringify(response)}`)
    }

    const data = await response.json()
    console.log('successfully updated group:', data)
    return data
  } catch (error) {
    console.error('An error occurred while updating the user:', error)
    throw new Error(JSON.stringify(error, null, 4))
  }
}

'use server'

import { CreateGroupData, UpdateGroupData } from '@/types/groups'
import { UserResponse } from '@/types/users'

import { UserSelection } from './components/users-tab/AssignUsersModal'

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
export const patchGroupUsers = async (groupId: string, userSelection: UserSelection, originalUserIds: string[]) => {
  try {
    let userIds = userSelection.selectedIds.concat(originalUserIds)
    if (userSelection.selectAll) {
      const usersResponse = await fetch(`${URL}/users`)
      const userData: UserResponse[] = await usersResponse.json()
      const allUserIds = userData.map(user => user.id)
      userIds = allUserIds.filter(userId => !userSelection.excludedIds.find(id => id === userId))
    }
    const updateUserData = {
      users: userIds.map(id => ({ id, assignedAt: new Date().toISOString() })),
    }

    const response = await fetch(`${URL}/groups/${groupId}`, {
      method: 'PATCH',
      headers: {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(updateUserData),
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

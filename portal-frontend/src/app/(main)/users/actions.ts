'use server'

import { CreateUserData, UpdateUserData, UserResponse } from '@/types/users'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

export const createUser = async (userData: CreateUserData) => {
  console.log('User saved:', userData)

  try {
    const response = await fetch(`${URL}/users`, {
      method: 'POST',
      headers: {
        // eslint-disable-next-line @typescript-eslint/naming-convention
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(userData),
    })

    if (!response.ok) {
      throw new Error(`HTTP error! Status: ${response.status}`)
    }
    const data: UserResponse = await response.json()
    console.log('successfully created user:', data)
    return data
  } catch (error) {
    console.error('An error occurred while creating new user:', error)
  }
}

export const updateUser = async (updateUserData: UpdateUserData) => {
  try {
    const response = await fetch(`${URL}/users/${updateUserData.id}`, {
      method: 'PUT',
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
    console.log('successfully updated user:', data)
  } catch (error) {
    console.error('An error occurred while updateing the user:', error)
  }
}

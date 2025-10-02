'use server'

import { CreateUserData, UpdateUserData, UserFormData, UserFormSchema } from '@/types/users'

const URL = `${process.env.JSON_SERVER_HOST}:${process.env.JSON_SERVER_PORT}`

export const createUser = async (userData: CreateUserData) => {
  console.log('User gespeichert:', userData)

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

    //   form.reset()

    //   setTabValueParam(tabValue || DEFAULT_TAB)
    //   router.push(`/roles?_tab=${tabValue || DEFAULT_TAB}`)
  } catch (error) {
    console.error('Fehler:', error)
  }
}

export const updateUser = async (updateUserData: UpdateUserData) => {
  console.log(updateUserData.id)
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
    console.log('Erfolgreich aktualisiert:', data)
  } catch (error) {
    console.error('Fehler:', error)
  }
}

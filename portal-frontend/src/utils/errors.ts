import { AxiosError } from 'axios'

type ApiError = {
  type: string
  title: 'Conflict' | string
  status: number
  detail: string
  instance: string
}

export const isNameConflictError = (error: AxiosError) => {
  const isConflictError = error.status === 409
  if (!error.response) return false
  const apiError = error.response.data as ApiError
  return isConflictError && apiError.detail.includes('with name') && apiError.detail.includes('already exists')
}

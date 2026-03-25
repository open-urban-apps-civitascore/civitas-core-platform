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
  if (!error?.response) return false
  const apiError = error.response.data as ApiError
  const errorDetail = apiError.detail || ''
  return isConflictError && errorDetail.includes('with name') && errorDetail.includes('already exists')
}

export const isEmailConflictError = (error: AxiosError) => {
  const isConflictError = error.status === 409
  if (!error?.response) return false
  const apiError = error.response.data as ApiError
  const errorDetail = apiError.detail || ''
  return isConflictError && errorDetail.includes('with email') && errorDetail.includes('already exists')
}

export const isPermissionsError = (error: AxiosError) => {
  return error.status === 403
}

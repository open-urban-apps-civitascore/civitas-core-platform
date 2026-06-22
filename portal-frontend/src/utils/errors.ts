import { isAxiosError } from 'axios'

type ApiError = {
  type: string
  title: 'Conflict' | string
  status: number
  detail: string
  instance: string
}

export const isNameConflictError = (error: unknown) => {
  if (!isAxiosError(error)) return false
  const isConflictError = error.status === 409
  if (!error?.response) return false
  const apiError = error.response.data as ApiError
  const errorDetail = apiError.detail || ''
  return isConflictError && errorDetail.includes('with name') && errorDetail.includes('already exists')
}

export const isEmailConflictError = (error: unknown) => {
  if (!isAxiosError(error)) return false
  const isConflictError = error.status === 409
  if (!error?.response) return false
  const apiError = error.response.data as ApiError
  const errorDetail = apiError.detail || ''
  return isConflictError && errorDetail.includes('with email') && errorDetail.includes('already exists')
}

export const isPermissionsError = (error: unknown) => {
  if (!isAxiosError(error)) return false
  return error.status === 403
}

export const isDatapoolScopeViolationError = (error: unknown) => {
  if (!isAxiosError(error)) return false
  return error.status === 422
}

export const isLayerNameError = (error: unknown) => {
  if (!isAxiosError(error)) return false
  const isConflictError = error.status === 409
  if (!error?.response) return false
  const apiError = error.response.data as ApiError
  const errorDetail = apiError.detail || ''
  return isConflictError && errorDetail.includes('with layerName') && errorDetail.includes('already exists')
}

export class LayerSaveError extends Error {
  constructor(
    public readonly originalError: unknown,
    public readonly layerIndex: number,
  ) {
    super('Layer save failed')
    this.name = 'LayerSaveError'
  }
}

import { isAxiosError } from 'axios'

type ApiError = {
  type: string
  title: 'Conflict' | string
  status: number
  detail: string
  instance: string
}

/** The problem body of an axios error with this status, with type and detail normalised. */
const problemWithStatus = (error: unknown, status: number): Pick<ApiError, 'type' | 'detail'> | null => {
  if (!isAxiosError(error) || error.status !== status || !error.response) return null
  const data = error.response.data as Partial<ApiError> | undefined
  return {
    type: typeof data?.type === 'string' ? data.type : '',
    detail: typeof data?.detail === 'string' ? data.detail : '',
  }
}

const hasErrorType = (problem: Pick<ApiError, 'type'>, urnSuffix: string) => problem.type.endsWith(`:${urnSuffix}`)

export const isNameConflictError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && problem.detail.includes('with name') && problem.detail.includes('already exists')
}

export const isEmailConflictError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && problem.detail.includes('with email') && problem.detail.includes('already exists')
}

export const isPermissionsError = (error: unknown) => problemWithStatus(error, 403) !== null

export const isDatapoolScopeViolationError = (error: unknown) => {
  const problem = problemWithStatus(error, 422)
  return !!problem && hasErrorType(problem, 'DATASOURCE_SCOPE_VIOLATION')
}

export const isTableNameConflictError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  if (!problem || !hasErrorType(problem, 'UNIQUE_CONSTRAINT_VIOLATION')) return false
  return problem.detail.includes('tableName') && /already use[sd]/.test(problem.detail)
}

export const isNotDraftError = (error: unknown) => {
  const problem = problemWithStatus(error, 400)
  return !!problem && hasErrorType(problem, 'DATASET_NOT_EDITABLE')
}

export const isSagaInFlightError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && hasErrorType(problem, 'SAGA_IN_FLIGHT')
}

export const isResourceInUseError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && hasErrorType(problem, 'RESOURCE_IN_USE') && !problem.detail.includes('confirmDataLoss')
}

export const isUnconfirmedDataLossError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && hasErrorType(problem, 'RESOURCE_IN_USE') && problem.detail.includes('confirmDataLoss')
}

export const isLayerNameError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && problem.detail.includes('with layerName') && problem.detail.includes('already exists')
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

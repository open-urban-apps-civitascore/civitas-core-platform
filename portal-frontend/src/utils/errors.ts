import { isAxiosError } from 'axios'

import { ConnectionTestProblem } from '@/types/connectionTest'
import type { DataSinkRemoval } from '@/utils/dataSinkRemoval'

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

export const isModelRootMissingError = (error: unknown) => {
  const problem = problemWithStatus(error, 400)
  return !!problem && hasErrorType(problem, 'MODEL_ROOT_MISSING')
}

/** The members a release refusal names as not reachable from the model's root, or null for any other error. */
export const unreachableModelMembers = (error: unknown): string[] | null => {
  const problem = problemWithStatus(error, 400)
  if (!problem || !hasErrorType(problem, 'MODEL_MEMBER_UNREACHABLE') || !isAxiosError(error)) return null
  const memberNames: unknown = (error.response?.data as { memberNames?: unknown } | undefined)?.memberNames
  return Array.isArray(memberNames) ? memberNames.filter((name): name is string => typeof name === 'string') : []
}

const ARRAY_INDEX = /\[(\d+)\]/g
const CONNECTOR_PATH_PREFIX = 'connector.'

/** The paths below the connector of the fields that the backend rejects, or null for any other error. */
export const getConnectorFieldErrorPaths = (error: unknown): string[] | null => {
  const problem = problemWithStatus(error, 400)
  if (!problem || !hasErrorType(problem, 'VALIDATION_FAILED') || !isAxiosError(error)) return null
  const fieldErrors: unknown = (error.response?.data as { fieldErrors?: unknown } | undefined)?.fieldErrors
  if (!Array.isArray(fieldErrors)) return []
  const fields: unknown[] = fieldErrors.map((fieldError: { field?: unknown } | null) => fieldError?.field)
  const connectorFields = fields.filter(
    (field): field is string => typeof field === 'string' && field.startsWith(CONNECTOR_PATH_PREFIX),
  )
  return connectorFields.map(field => field.slice(CONNECTOR_PATH_PREFIX.length).replace(ARRAY_INDEX, '.$1'))
}

export const isDatastructureNotAvailableError = (error: unknown) => {
  const problem = problemWithStatus(error, 400)
  return (
    !!problem &&
    hasErrorType(problem, 'INVALID_INPUT') &&
    problem.detail.includes('must be in AVAILABLE status for an AVAILABLE DataSource')
  )
}

export const isStoredSecretRefusedError = (error: unknown) => {
  const problem = problemWithStatus(error, 400)
  return (
    !!problem &&
    hasErrorType(problem, 'MALFORMED_REQUEST') &&
    problem.detail.includes('stored secret is kept only for the stored destination')
  )
}

export const isSagaInFlightError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && hasErrorType(problem, 'SAGA_IN_FLIGHT')
}

export const isResourceInUseError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && hasErrorType(problem, 'RESOURCE_IN_USE')
}

export const isPipelineClosureInvalidError = (error: unknown) => {
  const problem = problemWithStatus(error, 422)
  return !!problem && hasErrorType(problem, 'PIPELINE_CLOSURE_INVALID')
}

export const isUnconfirmedDataLossError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && hasErrorType(problem, 'REMOVAL_NOT_CONFIRMED')
}

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null

const listOf = <T>(value: unknown, isItem: (item: Record<string, unknown>) => boolean): T[] =>
  Array.isArray(value) ? (value.filter(item => isRecord(item) && isItem(item)) as T[]) : []

/** What the backend would remove with a request it refused as unconfirmed, or null for any other error. */
export const unconfirmedRemoval = (error: unknown): DataSinkRemoval | null => {
  if (!isUnconfirmedDataLossError(error) || !isAxiosError(error)) return null
  const removal: unknown = (error.response?.data as { removal?: unknown } | undefined)?.removal
  const fields = isRecord(removal) ? removal : {}
  return {
    dataSinks: listOf(
      fields.dataSinks,
      dataSink => typeof dataSink.id === 'string' && typeof dataSink.provisioned === 'boolean',
    ),
    layers: listOf(fields.layers, layer => typeof layer.id === 'string' && typeof layer.layerName === 'string'),
    apis: listOf(fields.apis, api => typeof api.id === 'string' && typeof api.name === 'string'),
  }
}

export const isLayerNameError = (error: unknown) => {
  const problem = problemWithStatus(error, 409)
  return !!problem && problem.detail.includes('with layerName') && problem.detail.includes('already exists')
}

const UNKNOWN_ERROR_CODE = 'UNKNOWN'

const isNonEmptyString = (value: unknown): value is string => typeof value === 'string' && value !== ''

const fieldErrorMessage = (fieldError: unknown) =>
  typeof fieldError === 'object' && fieldError !== null && 'message' in fieldError ? fieldError.message : undefined

export const toConnectionTestProblem = (error: unknown): ConnectionTestProblem => {
  if (!isAxiosError(error)) return { code: UNKNOWN_ERROR_CODE, details: [] }
  const data = error.response?.data as (Partial<ApiError> & { fieldErrors?: unknown }) | undefined
  const type = typeof data?.type === 'string' ? data.type : ''
  const code = type.includes(':') ? type.slice(type.lastIndexOf(':') + 1) : ''
  const fieldErrors = Array.isArray(data?.fieldErrors) ? data.fieldErrors : []
  const fieldMessages = fieldErrors.map(fieldErrorMessage)
  const details = [data?.detail, ...fieldMessages]
  return { code: code || UNKNOWN_ERROR_CODE, details: details.filter(isNonEmptyString) }
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

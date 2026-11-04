import { get } from 'react-hook-form'

import { CONNECTOR_TYPES, HTTP_AUTHENTICATION_TYPES } from '@/const/connectors'
import {
  ConnectionTestInput,
  ConnectionTestResult,
  MASKED_SECRET,
  PREVIEW_TRUNCATED_MARKER,
} from '@/types/connectionTest'
import { ConnectorFormToApiSchema, ConnectorType, HttpApiCallsStrictSchema } from '@/types/connectors'
import { DatasourceFormDraft } from '@/types/datasources'

type ConnectorValues = Pick<DatasourceFormDraft, 'connectorType' | 'configuration'>

const JSON_INDENT = 2

const SECRET_PATHS: Record<ConnectorType, string> = {
  MQTT: 'password',
  SQL: 'password',
  HTTP: 'authentication.password',
}

const toFirstApiCallName = (configuration: ConnectorValues['configuration']) => {
  const name: unknown = get(configuration, 'apiCalls.0.name')
  return typeof name === 'string' ? name.trim() : ''
}

const REQUIRED_MESSAGE = 'common.errors.required'

// The backend requires an API call for HTTP. Invalid values of the API calls are left to the invalid hint.
export const isApiCallIncomplete = (values: ConnectorValues) => {
  if (values.connectorType !== CONNECTOR_TYPES.HTTP) return false
  try {
    const parsed = HttpApiCallsStrictSchema.safeParse(get(values.configuration, 'apiCalls') ?? [])
    return !parsed.success && parsed.error.issues.some(issue => issue.message === REQUIRED_MESSAGE)
  } catch {
    // Zod v4: safeParse throws on stale keys
    return true
  }
}

export const toConnectionTestInput = (values: ConnectorValues): ConnectionTestInput | null => {
  if (isApiCallIncomplete(values)) return null
  try {
    const parsed = ConnectorFormToApiSchema.safeParse(values)
    if (!parsed.success) return null
    if (values.connectorType !== CONNECTOR_TYPES.HTTP) return { connector: parsed.data }
    return { connector: parsed.data, apiCall: toFirstApiCallName(values.configuration) }
  } catch {
    // Zod v4: safeParse throws on stale keys
    return null
  }
}

export const hasMaskedSecret = (values: ConnectorValues) => {
  const { connectorType, configuration } = values
  if (!connectorType) return false
  const isHttpWithoutBasic =
    connectorType === CONNECTOR_TYPES.HTTP &&
    get(configuration, 'authentication.type') !== HTTP_AUTHENTICATION_TYPES.BASIC
  if (isHttpWithoutBasic) return false
  return get(configuration, SECRET_PATHS[connectorType]) === MASKED_SECRET
}

export const toPreviewText = (result: ConnectionTestResult, connectorType: ConnectorType): string | null => {
  const { preview } = result
  if (!preview) return null
  const hasTruncatedMarker = result.previewTruncated && preview.endsWith(PREVIEW_TRUNCATED_MARKER)
  const text = hasTruncatedMarker ? preview.slice(0, -PREVIEW_TRUNCATED_MARKER.length) : preview
  if (connectorType !== CONNECTOR_TYPES.HTTP) return text
  try {
    return JSON.stringify(JSON.parse(text), null, JSON_INDENT)
  } catch {
    return text
  }
}

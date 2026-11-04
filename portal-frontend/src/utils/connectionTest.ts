import { get } from 'react-hook-form'

import { CONNECTOR_TYPES, SECRET_PLACEHOLDER } from '@/const/connectors'
import { ConnectionTestInput, ConnectionTestResult, PREVIEW_TRUNCATED_MARKER } from '@/types/connectionTest'
import { ConnectorFormToApiSchema, ConnectorType } from '@/types/connectors'
import { DatasourceFormDraft } from '@/types/datasources'
import { HttpApiCallsStrictSchema } from '@/types/httpApiCalls'
import { getSecretPaths } from '@/utils/connectors'

type ConnectorValues = Pick<DatasourceFormDraft, 'connectorType' | 'configuration'>

const JSON_INDENT = 2

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
  if (!connectorType || !configuration) return false
  const secretPaths = getSecretPaths(connectorType, configuration)
  return secretPaths.some(path => get(configuration, path) === SECRET_PLACEHOLDER)
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

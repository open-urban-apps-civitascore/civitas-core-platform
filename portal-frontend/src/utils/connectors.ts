import { set } from 'react-hook-form'

import { CONNECTOR_INPUTS } from '@/app/(main)/datasources/[datasourceId]/components/connector-tab/connectorSources'
import { HTTP_AUTHENTICATION_TYPES } from '@/const/connectors'
import {
  ConnectorDraft,
  ConnectorType,
  HttpAuthenticationType,
  HttpConnectionForm,
  HttpParameterForm,
  normalizeHttpBaseUrl,
  SECRET_PLACEHOLDER,
} from '@/types/connectors'

type ConnectorConfig = NonNullable<ConnectorDraft['configuration']>

export const getConnectorDefaults = (type: ConnectorType): ConnectorConfig => {
  const defaults = {}
  CONNECTOR_INPUTS[type].forEach(field => set(defaults, field.key, field.defaultValue))
  return defaults as ConnectorConfig
}

export const getConnectorFormData = (
  connectorType: ConnectorType,
  currentConfig: ConnectorDraft['configuration'] | undefined,
): ConnectorDraft['configuration'] => {
  return {
    ...getConnectorDefaults(connectorType),
    ...(currentConfig ?? {}),
  }
}

export const formatPathWithQuery = (path: string, queryParameters: HttpParameterForm[]): string => {
  const namedParameters = queryParameters.filter(parameter => parameter.name)
  const pairs = namedParameters.map(({ name, value }) => `${name}=${value}`)
  const query = pairs.join('&')
  return query ? `${path}?${query}` : path
}

export const buildApiCallUrl = (baseUrl: string, path: string, queryParameters: HttpParameterForm[]): string => {
  const encodedParameters = queryParameters.map(({ name, value }) => ({
    name: encodeURIComponent(name),
    value: encodeURIComponent(value),
  }))
  return `${normalizeHttpBaseUrl(baseUrl)}${formatPathWithQuery(path, encodedParameters)}`
}

const SECRET_FIELDS: Record<HttpAuthenticationType, readonly string[]> = {
  [HTTP_AUTHENTICATION_TYPES.NONE]: [],
  [HTTP_AUTHENTICATION_TYPES.BASIC]: ['password'],
  [HTTP_AUTHENTICATION_TYPES.BEARER_TOKEN]: ['token'],
  [HTTP_AUTHENTICATION_TYPES.API_TOKEN]: ['value'],
  [HTTP_AUTHENTICATION_TYPES.OAUTH2]: ['clientSecret'],
  [HTTP_AUTHENTICATION_TYPES.CLIENT_CERTIFICATE]: ['certificate', 'privateKey'],
}

const SECRET_FIELD_NAMES = Object.values(SECRET_FIELDS).flat()

export const HTTP_SECRET_PATHS = SECRET_FIELD_NAMES.map(field => `authentication.${field}`)

// Scheme, host and port as written
const HTTP_URL_DESTINATION = /^([^:/?#]+):\/\/(?:[^@/?#]*@)?(\[[^\]]*\]|[^:/?#]*)(?::(\d*))?(?:[/?#]|$)/

const toDestination = (url: string) => {
  const [, scheme, host, port] = HTTP_URL_DESTINATION.exec(url) ?? []
  return scheme && host ? `${scheme}://${host}:${port ?? ''}` : url
}

const getDestinations = (configuration: HttpConnectionForm) => {
  const { baseUrl, authentication } = configuration
  const tokenUrl = authentication?.type === HTTP_AUTHENTICATION_TYPES.OAUTH2 ? authentication.tokenUrl : undefined
  const urls = [normalizeHttpBaseUrl(baseUrl ?? ''), (tokenUrl ?? '').trim()]
  const presentUrls = urls.filter(Boolean)
  return presentUrls.map(toDestination)
}

export const findSecretsToReenter = (current: HttpConnectionForm, loaded: HttpConnectionForm | undefined) => {
  const { authentication } = current
  if (!loaded || !authentication) return []
  const secretFields = SECRET_FIELDS[authentication.type]
  const maskedSecrets = Object.entries(authentication).filter(
    ([field, value]) => secretFields.includes(field) && value === SECRET_PLACEHOLDER,
  )
  if (!maskedSecrets.length) return []
  const loadedDestinations = new Set(getDestinations(loaded))
  const isAtLoadedDestinations = getDestinations(current).every(destination => loadedDestinations.has(destination))
  return isAtLoadedDestinations ? [] : maskedSecrets.map(([field]) => `authentication.${field}`)
}

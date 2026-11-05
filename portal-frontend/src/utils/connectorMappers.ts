import { HTTP_AUTHENTICATION_TYPES, HTTP_PAGINATION_TYPES, HTTP_PARAMETER_LOCATIONS } from '@/const/connectors'
import type { HttpAuthenticationApiResponse, HttpAuthenticationForm, HttpAuthenticationType } from '@/types/connectors'
import type { HttpPaginationForm, HttpParameterForm } from '@/types/httpApiCalls'

export const emptyHttpAuthentication = (type: HttpAuthenticationType): HttpAuthenticationForm => {
  switch (type) {
    case HTTP_AUTHENTICATION_TYPES.NONE:
      return { type }
    case HTTP_AUTHENTICATION_TYPES.BASIC:
      return { type, username: '', password: '' }
    case HTTP_AUTHENTICATION_TYPES.BEARER_TOKEN:
      return { type, token: '' }
    case HTTP_AUTHENTICATION_TYPES.API_TOKEN:
      return { type, name: '', value: '', in: HTTP_PARAMETER_LOCATIONS.HEADER }
    case HTTP_AUTHENTICATION_TYPES.OAUTH2:
      return { type, tokenUrl: '', clientId: '', clientSecret: '', scope: '' }
    case HTTP_AUTHENTICATION_TYPES.CLIENT_CERTIFICATE:
      return { type, certificate: '', privateKey: '' }
  }
}

export const toAuthenticationForm = (
  authentication: HttpAuthenticationApiResponse | null | undefined,
): HttpAuthenticationForm => {
  if (!authentication) return emptyHttpAuthentication(HTTP_AUTHENTICATION_TYPES.NONE)
  const presentValues = Object.fromEntries(Object.entries(authentication).filter(([, value]) => value != null))
  return { ...emptyHttpAuthentication(authentication.type), ...presentValues } as HttpAuthenticationForm
}

export const toAuthenticationApi = (authentication: HttpAuthenticationForm | undefined) => {
  if (!authentication) return { type: HTTP_AUTHENTICATION_TYPES.NONE }
  if (authentication.type === HTTP_AUTHENTICATION_TYPES.OAUTH2 && !authentication.scope) {
    return { ...authentication, scope: null }
  }
  return authentication
}

export const isEmptyParameter = (parameter: HttpParameterForm) => !parameter.name.trim() && !parameter.value

const withoutEmptyParameters = (parameters: HttpParameterForm[]) =>
  parameters.filter(parameter => !isEmptyParameter(parameter))

export const removeEmptyParameters = <T extends { queryParameters: HttpParameterForm[]; headers: HttpParameterForm[] }>(
  apiCall: T,
) => ({
  ...apiCall,
  queryParameters: withoutEmptyParameters(apiCall.queryParameters),
  headers: withoutEmptyParameters(apiCall.headers),
})

export const toPaginationApi = (pagination: HttpPaginationForm) => {
  if (!pagination.isEnabled) return null
  const toNumber = (value: string) => (value ? Number(value) : null)
  switch (pagination.type) {
    case HTTP_PAGINATION_TYPES.PAGE_NUMBER:
      return {
        type: pagination.type,
        pageParameter: pagination.pageParameter || null,
        pageSizeParameter: pagination.pageSizeParameter || null,
        pageSize: toNumber(pagination.pageSize),
        startPage: toNumber(pagination.startPage),
        endPage: toNumber(pagination.endPage),
      }
    case HTTP_PAGINATION_TYPES.LIMIT_OFFSET:
      return {
        type: pagination.type,
        offsetParameter: pagination.offsetParameter || null,
        offset: toNumber(pagination.offset),
        limitParameter: pagination.limitParameter || null,
        limit: toNumber(pagination.limit),
        countPath: pagination.countPath || null,
      }
    case HTTP_PAGINATION_TYPES.CURSOR:
      return {
        type: pagination.type,
        cursorPath: pagination.cursorPath || null,
        cursorParameter: pagination.cursorParameter || null,
        cursorIn: pagination.cursorIn,
        limitParameter: pagination.limitParameter || null,
        limit: toNumber(pagination.limit),
      }
  }
}

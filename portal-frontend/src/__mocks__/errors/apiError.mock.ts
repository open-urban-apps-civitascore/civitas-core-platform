import { AxiosError, AxiosHeaders, InternalAxiosRequestConfig } from 'axios'

const config = { headers: new AxiosHeaders(), method: 'GET', url: '/test' } as InternalAxiosRequestConfig

/** An axios error carrying a backend problem body, as the predicates in utils/errors.ts expect it. */
export const mockApiError = (status: number, detail?: string, type?: string) =>
  new AxiosError('request failed', undefined, config, undefined, {
    status,
    statusText: '',
    headers: new AxiosHeaders(),
    config,
    data: { detail, type },
  })

import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'

type AdapterResponseData = unknown

const buildResponse = (
  config: InternalAxiosRequestConfig,
  data: AdapterResponseData,
  headers: Record<string, string> = {},
): AxiosResponse => ({
  config,
  data,
  headers,
  status: 200,
  statusText: 'OK',
})

describe('axiosClient response interceptor', () => {
  beforeEach(() => {
    vi.resetModules()
    process.env.NEXT_SERVER_URL = 'https://api.test.com'
  })

  it('normalizes array responses without content and reads x-total-count header', async () => {
    const { axiosClient } = await import('./client')

    axiosClient.defaults.adapter = async config => buildResponse(config, [{ id: 1 }], { 'x-total-count': '12' })

    const response = await axiosClient.get('/users')

    expect(response.data).toEqual({
      content: [{ id: 1 }],
      totalElements: 12,
    })
  })

  it('falls back to 0 when x-total-count is missing for array responses', async () => {
    const { axiosClient } = await import('./client')

    axiosClient.defaults.adapter = async config => buildResponse(config, [{ id: 1 }])

    const response = await axiosClient.get('/users')

    expect(response.data).toEqual({
      content: [{ id: 1 }],
      totalElements: 0,
    })
  })

  it('falls back to 0 when x-total-count is invalid for array responses', async () => {
    const { axiosClient } = await import('./client')

    axiosClient.defaults.adapter = async config => buildResponse(config, [{ id: 1 }], { 'x-total-count': 'abc' })

    const response = await axiosClient.get('/users')

    expect(response.data).toEqual({
      content: [{ id: 1 }],
      totalElements: 0,
    })
  })

  it('normalizes object responses without content and leaves totalElements undefined', async () => {
    const { axiosClient } = await import('./client')

    axiosClient.defaults.adapter = async config => buildResponse(config, { id: 1 })

    const response = await axiosClient.get('/users/1')

    expect(response.data).toEqual({
      content: { id: 1 },
      totalElements: undefined,
    })
  })

  it('returns existing content responses unchanged', async () => {
    const { axiosClient } = await import('./client')

    const payload = {
      content: [{ id: 1 }],
      totalElements: 4,
    }

    axiosClient.defaults.adapter = async config => buildResponse(config, payload, { 'x-total-count': '12' })

    const response = await axiosClient.get('/users')

    expect(response.data).toBe(payload)
  })
})

// apiRequest.test.ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { apiRequest } from './apiRequest'
import { axiosClient } from '../client/client'

vi.mock('../client/client', () => ({
  axiosClient: {
    request: vi.fn(),
  },
}))
const mockedRequest = vi.mocked(axiosClient.request)

describe('apiRequest', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  it('sends GET request with correct request params and returns data', async () => {
    mockedRequest.mockResolvedValue({
      data: {
        content: { data: 'test' },
        totalElements: 10,
        totalPages: 2,
      },
    })

    const result = await apiRequest<{ foo: string }>({
      endpoint: '/test',
      method: 'GET',
      params: new URLSearchParams('type=test'),
    })

    expect(mockedRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        url: '/api/test?type=test',
        method: 'GET',
      }),
    )
    expect(result).toEqual({
      data: { data: 'test' },
      totalElements: 10,
      totalPages: 2,
    })
  })

  it('sends POST request with body and returns data', async () => {
    const mockedRequest = vi.mocked(axiosClient.request)

    mockedRequest.mockResolvedValue({
      data: {
        content: { id: 1 },
      },
    })

    const body = { name: 'Max' }

    const result = await apiRequest<{ id: number }, typeof body>({
      endpoint: '/users/123',
      method: 'POST',
      data: body,
    })

    expect(mockedRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'POST',
        url: '/api/users/123?',
        data: body,
      }),
    )

    expect(result).toEqual({
      data: { id: 1 },
      totalElements: undefined,
      totalPages: undefined,
    })
  })

  it('sends DELETE request and returns data', async () => {
    const mockedRequest = vi.mocked(axiosClient.request)

    mockedRequest.mockResolvedValue({
      data: {
        content: null,
      },
    })

    const result = await apiRequest<null>({
      endpoint: '/users/123',
      method: 'DELETE',
    })

    expect(mockedRequest).toHaveBeenCalledWith(
      expect.objectContaining({
        method: 'DELETE',
        url: '/api/users/123?',
      }),
    )

    expect(result).toEqual({
      data: null,
    })
  })

  it('throws error with correct error message when request fails', async () => {
    mockedRequest.mockRejectedValueOnce(new Error('Unauthorized'))

    await expect(
      apiRequest({
        endpoint: '/test',
        method: 'GET',
        errorMessage: 'Failed to load data',
      }),
    ).rejects.toThrow('Failed to load data')

    await expect(
      apiRequest({
        endpoint: '/test',
        method: 'POST',
        errorMessage: 'Failed to create data',
      }),
    ).rejects.toThrow('Failed to create data')

    await expect(
      apiRequest({
        endpoint: '/test',
        method: 'DELETE',
        errorMessage: 'Failed to delete data',
      }),
    ).rejects.toThrow('Failed to delete data')
  })
})

import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import mockedDatasetResponse from '@/__mocks__/datasets/datasetsResponse.json'
import { mappedDatasets } from '@/__mocks__/datasets/mappedDatasets.mock'
import messages from '@/messages/de.json'

import Page, { DatasetResponse, getSearchParam, getSortParam, mapDatasets } from './page'

describe('Page', () => {
  beforeEach(() => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockedDatasetResponse,
    }) as unknown as typeof fetch

    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <Page />
      </NextIntlClientProvider>,
    )
  })

  it('calls fetch with the correct URL and query parameters', () => {
    expect(fetch).toHaveBeenCalledWith('http://localhost:3001/datasets?_page=1&_limit=10')
  })

  it('renders the datasets table', async () => {
    const table = screen.getByRole('table')
    expect(table).toBeDefined()
  })

  it('calls fetch with search param when typing in search field', async () => {
    const input = screen.getByPlaceholderText('Durchsuchen...')
    fireEvent.change(input, { target: { value: 'climate' } })

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledWith(expect.stringContaining('&q=climate'))
    })
  })
})

describe('mapDatasets', () => {
  it('maps the datasets response to the correct structure', async () => {
    expect(mapDatasets(mockedDatasetResponse as DatasetResponse[])).toEqual(mappedDatasets)
  })
})

describe('getSortParam', () => {
  it('returns an empty string when no sorting is selected', () => {
    expect(getSortParam([])).toEqual('')
  })
  it('returns the correct search params when sorting is selected', () => {
    expect(getSortParam([{ id: 'name', desc: false }])).toEqual('&_sort=title&_order=asc')
    expect(getSortParam([{ id: 'name', desc: true }])).toEqual('&_sort=title&_order=desc')
    expect(getSortParam([{ id: 'lastUpdated', desc: false }])).toEqual('&_sort=modified&_order=asc')
    expect(getSortParam([{ id: 'lastUpdated', desc: true }])).toEqual('&_sort=modified&_order=desc')
  })

  describe('getSearchParam', () => {
    it('returns an empty string when searchstring is empty', () => {
      expect(getSearchParam('')).toEqual('')
    })
    it('returns the correct query string when a search string is provided', () => {
      expect(getSearchParam('test')).toEqual('&q=test')
    })
  })
})

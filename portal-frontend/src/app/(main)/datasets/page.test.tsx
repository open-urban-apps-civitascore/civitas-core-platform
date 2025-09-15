import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import mockedDatasetResponse from '@/__mocks__/datasets/datasetsResponse.json'
import { mappedDatasets } from '@/__mocks__/datasets/mappedDatasets.mock'
import messages from '@/messages/de.json'

import Page, { DatasetResponse, getSortParam, mapDatasets } from './page'

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
    expect(fetch).toHaveBeenCalledWith('http://localhost:3001/datasets?_page=1&_per_page=10')
  })

  it('renders the datasets table', async () => {
    const table = screen.getByRole('table')
    expect(table).toBeDefined()
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
    expect(getSortParam([{ id: 'name', desc: false }])).toEqual('&_sort=title')
    expect(getSortParam([{ id: 'name', desc: true }])).toEqual('&_sort=-title')
    expect(getSortParam([{ id: 'lastUpdated', desc: false }])).toEqual('&_sort=modified')
    expect(getSortParam([{ id: 'lastUpdated', desc: true }])).toEqual('&_sort=-modified')
  })
})

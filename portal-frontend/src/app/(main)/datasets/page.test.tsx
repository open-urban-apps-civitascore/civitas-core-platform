import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import mockedDatasetResponse from '@/__mocks__/datasets/datasetsResponse.json'
import { mappedDatasets } from '@/__mocks__/datasets/mappedDatasets.mock'
import messages from '@/messages/de.json'

import Page, { DatasetResponse, mapDatasets } from './page'

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

  it('calls fetch with the correct URL', () => {
    expect(fetch).toHaveBeenCalledWith('http://localhost:3001/datasets')
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

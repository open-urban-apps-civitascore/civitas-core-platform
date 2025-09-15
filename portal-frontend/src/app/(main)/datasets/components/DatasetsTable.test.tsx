import { render, screen, within } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import { mappedDatasets } from '@/__mocks__/datasets/mappedDatasets.mock'
import messages from '@/messages/de.json'

import DatasetsTable from './DatasetsTable'

describe('DatasetsTable', () => {
  beforeEach(() => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <DatasetsTable datasets={mappedDatasets} />
      </NextIntlClientProvider>,
    )
  })

  it('renders the table', () => {
    expect(screen.getByRole('table')).toBeDefined()
  })

  it('renders the header and header content correctly', async () => {
    ;[
      'Name',
      'Datenraum',
      'Fachbereich',
      'Ansprechperson',
      'Zuletzt aktualisiert',
      'Zugriffsstatus',
      'Freigabeprozess',
      'Distribution',
    ].forEach(headerText => {
      expect(screen.getByRole('columnheader', { name: headerText })).toBeDefined()
    })
    expect(screen.queryByRole('columnheader', { name: 'id' })).toBeNull()
  })

  it('renders the rows and row content correctly', async () => {
    screen.debug()
    const rows = screen.getAllByRole('row')
    expect(rows).toHaveLength(3)

    const dataRow = rows[1]
    const cells = within(dataRow).getAllByRole('cell')

    expect(cells[0]).toHaveTextContent(mappedDatasets[0].name) // name
    expect(cells[1]).toHaveTextContent(mappedDatasets[0].dataSpace) // name
    expect(cells[2]).toHaveTextContent(mappedDatasets[0].department) // name
    expect(cells[3]).toHaveTextContent(mappedDatasets[0].creator[0]) // name
    expect(cells[4]).toHaveTextContent('10.09.2023') // name
  })
})

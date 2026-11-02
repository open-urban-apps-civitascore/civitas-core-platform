import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'

import messages from '@/messages/de.json'
import { DatastructureStatusType } from '@/types/datastructures'

import { VersionStatusCounts } from './VersionStatusCounts'

const renderCounts = (versionCountByStatus?: Record<DatastructureStatusType, number>) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <VersionStatusCounts versionCountByStatus={versionCountByStatus} />
    </NextIntlClientProvider>,
  )

describe('VersionStatusCounts', () => {
  it('shows the available count before the draft count', () => {
    renderCounts({ AVAILABLE: 2, DRAFT: 1 })
    expect(screen.getAllByTestId('statusBadge').map(badge => badge.textContent)).toEqual(['2 Verfügbar', '1 Entwurf'])
  })

  it('shows no badge for a status without versions', () => {
    renderCounts({ AVAILABLE: 0, DRAFT: 3 })
    expect(screen.getAllByTestId('statusBadge').map(badge => badge.textContent)).toEqual(['3 Entwürfe'])
  })

  it.each([
    ['both counts are 0', { AVAILABLE: 0, DRAFT: 0 }],
    ['there are no counts', undefined],
  ])('renders nothing when %s', (_, versionCountByStatus) => {
    const { container } = renderCounts(versionCountByStatus)
    expect(container).toBeEmptyDOMElement()
  })
})

import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'

import messages from '@/messages/de.json'
import { STATUS_TYPES, StatusTypes } from '@/types/common'

import { StatusBadge } from './StatusBadge'

interface RenderOptions {
  count?: number
  className?: string
  testId?: string
}

const renderBadge = (status: StatusTypes, options: RenderOptions = {}) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <StatusBadge status={status} {...options} />
    </NextIntlClientProvider>,
  )

describe('StatusBadge', () => {
  it.each([
    [STATUS_TYPES.DRAFT, 'Entwurf'],
    [STATUS_TYPES.READY, 'Fertig'],
    [STATUS_TYPES.AVAILABLE, 'Verfügbar'],
  ])('shows the label of status %s without a count', (status, label) => {
    renderBadge(status)
    expect(screen.getByTestId('statusBadge')).toHaveTextContent(new RegExp(`^${label}$`))
  })

  it.each([
    [STATUS_TYPES.DRAFT, '2 Entwürfe'],
    [STATUS_TYPES.READY, '2 Fertig'],
    [STATUS_TYPES.AVAILABLE, '2 Verfügbar'],
  ])('shows the count with the label of status %s', (status, label) => {
    renderBadge(status, { count: 2 })
    expect(screen.getByTestId('statusBadge')).toHaveTextContent(new RegExp(`^${label}$`))
  })

  it.each([
    [STATUS_TYPES.DRAFT, 'lucide-circle-dashed'],
    [STATUS_TYPES.READY, 'lucide-circle-check'],
    [STATUS_TYPES.AVAILABLE, 'lucide-circle-check-big'],
  ])('shows the icon of status %s', (status, iconClass) => {
    renderBadge(status)
    expect(screen.getByTestId('statusBadge').querySelector('svg')).toHaveClass(iconClass)
  })

  it('lets the class name override the default height', () => {
    renderBadge(STATUS_TYPES.DRAFT, { className: 'h-5' })
    const badge = screen.getByTestId('statusBadge')
    expect(badge).toHaveClass('h-5')
    expect(badge).not.toHaveClass('h-7')
  })

  it('uses the given test id', () => {
    renderBadge(STATUS_TYPES.DRAFT, { testId: 'customBadge' })
    expect(screen.getByTestId('customBadge')).toBeInTheDocument()
  })
})

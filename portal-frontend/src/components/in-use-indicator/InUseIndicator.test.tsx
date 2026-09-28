import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'

import { InUseIndicator } from './InUseIndicator'

const renderIndicator = (isInUseByReleased: boolean) =>
  render(
    <NextIntlClientProvider locale="de" messages={messages}>
      <InUseIndicator isInUseByReleased={isInUseByReleased} />
    </NextIntlClientProvider>,
  )

describe('InUseIndicator', () => {
  it('renders nothing when no released entity references the artifact', () => {
    renderIndicator(false)
    expect(screen.queryByTestId('inUseIndicator')).toBeNull()
  })

  it('renders the indicator when a released entity references the artifact', () => {
    renderIndicator(true)
    expect(screen.getByTestId('inUseIndicator')).toBeInTheDocument()
  })

  it('names the restriction for assistive technology', () => {
    renderIndicator(true)
    expect(screen.getByTestId('inUseIndicator')).toHaveAccessibleName(messages.common.inUse.tooltip)
  })
})

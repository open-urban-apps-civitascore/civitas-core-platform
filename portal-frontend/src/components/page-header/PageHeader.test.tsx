import { render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'
import { PageHeader } from './PageHeader'

const onTabClickMock = vi.fn()
const tabsMock = [
  { value: 'testTab1', label: 'Test Tab 1' },
  { value: 'testTab2', label: 'Test Tab 2' },
  { value: 'testTab3', label: 'Test Tab 3' },
]

describe('PageHeader', () => {
  it('renders only the title when only a title is provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader title="Test Title" />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
  })

  it('renders the title and a badge when a title and a badge title are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader title="Test Title" badgeTitle="Badge Title" />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
    expect(screen.getByTestId('pageHeaderBadge')).toHaveTextContent('Badge Title')
  })

  it('renders the title and the primary tabs when title and primary tab values are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          title="Test Title"
          tabs={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.getAllByRole('tablist')).toHaveLength(1)
    expect(screen.getByTestId('primaryTabs')).toBeInTheDocument()
    expect(screen.queryByTestId('subTabs')).not.toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(3)
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
  })

  it('renders the title and the sub tabs when title and sub tab values are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          title="Test Title"
          subTabs={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.getAllByRole('tablist')).toHaveLength(1)
    expect(screen.queryByTestId('primaryTabs')).not.toBeInTheDocument()
    expect(screen.getByTestId('subTabs')).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(3)
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
  })

  it('renders the title and both tab sections when title and both tab section values are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          title="Test Title"
          tabs={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
          subTabs={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.getAllByRole('tablist')).toHaveLength(2)
    expect(screen.getByTestId('primaryTabs')).toBeInTheDocument()
    expect(screen.getByTestId('subTabs')).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(6)
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
  })

  it('renders the title, the badge and both tab sections when title, badge title and both tab section values are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          title="Test Title"
          badgeTitle="Badge Title"
          tabs={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
          subTabs={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.getAllByRole('tablist')).toHaveLength(2)
    expect(screen.getByTestId('primaryTabs')).toBeInTheDocument()
    expect(screen.getByTestId('subTabs')).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(6)
    expect(screen.getByTestId('pageHeaderBadge')).toHaveTextContent('Badge Title')
  })
})

import { fireEvent, render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'

import { PageHeader } from './PageHeader'

const mockUseIsTruncated = vi.fn().mockReturnValue(false)

vi.mock('@/hooks/use-is-truncated', () => ({
  useIsTruncated: () => mockUseIsTruncated(),
}))

const onTabClickMock = vi.fn()
const tabsMock = [
  { value: 'testTab1', label: 'Test Tab 1' },
  { value: 'testTab2', label: 'Test Tab 2' },
  { value: 'testTab3', label: 'Test Tab 3' },
]

describe('PageHeader', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders nothing when no properties provided provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader />
      </NextIntlClientProvider>,
    )
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
    expect(screen.queryByTestId('primaryTabs')).not.toBeInTheDocument()
    expect(screen.queryByTestId('segmentedControlBar')).not.toBeInTheDocument()
  })
  it('renders only the title when only a title is provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader title="Test Title" />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument()
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
    expect(screen.queryByTestId('primaryTabs')).not.toBeInTheDocument()
    expect(screen.queryByTestId('segmentedControlBar')).not.toBeInTheDocument()
  })
  it('renders only the TabSection when only tabSectionProps are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          tabsSectionProps={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
    expect(screen.queryByRole('tablist')).toBeInTheDocument()
    expect(screen.queryByTestId('primaryTabs')).toBeInTheDocument()
    expect(screen.queryByTestId('segmentedControlBar')).not.toBeInTheDocument()
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

  it('renders the title and the TabsSection when title and tabSectionProps values are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          title="Test Title"
          tabsSectionProps={{
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
    expect(screen.queryByTestId('segmentedControlBar')).not.toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(3)
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
  })

  it('renders the title and the SegmentedControlBar when title and segmentedControlBarProps are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          title="Test Title"
          segmentedControlBarProps={{
            tabs: tabsMock,
            onTabChange: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.getAllByRole('tablist')).toHaveLength(1)
    expect(screen.queryByTestId('primaryTabs')).not.toBeInTheDocument()
    expect(screen.getByTestId('segmentedControlBar')).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(3)
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
  })

  it('renders the title, TabSection and SegmentedControlBar when title, tabSectionProps and segmentedControlBarProps are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          title="Test Title"
          tabsSectionProps={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
          segmentedControlBarProps={{
            tabs: tabsMock,
            onTabChange: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.getAllByRole('tablist')).toHaveLength(2)
    expect(screen.getByTestId('primaryTabs')).toBeInTheDocument()
    expect(screen.getByTestId('segmentedControlBar')).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(6)
    expect(screen.queryByTestId('pageHeaderBadge')).not.toBeInTheDocument()
  })

  it('renders the title, the badge, TabSection and SegmentedControlBar when title, badge title, tabSectionProps and segmentedControlBarProps are provided', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          title="Test Title"
          badgeTitle="Badge Title"
          tabsSectionProps={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
          segmentedControlBarProps={{
            tabs: tabsMock,
            onTabChange: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
        />
      </NextIntlClientProvider>,
    )
    expect(screen.getByRole('heading')).toHaveTextContent('Test Title')
    expect(screen.getAllByRole('tablist')).toHaveLength(2)
    expect(screen.getByTestId('primaryTabs')).toBeInTheDocument()
    expect(screen.getByTestId('segmentedControlBar')).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(6)
    expect(screen.getByTestId('pageHeaderBadge')).toHaveTextContent('Badge Title')
  })

  it('triggers the tab click handler when clicking on a tab', () => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader
          title="Test Title"
          badgeTitle="Badge Title"
          tabsSectionProps={{
            tabs: tabsMock,
            onClick: onTabClickMock,
            selectedTab: tabsMock[0].value,
          }}
        />
      </NextIntlClientProvider>,
    )
    const tabs = screen.getAllByRole('tab')
    fireEvent.click(tabs[1])
    expect(onTabClickMock).toHaveBeenCalledOnce()
    fireEvent.click(tabs[2])
    expect(onTabClickMock).toHaveBeenCalledTimes(2)
  })

  it('does not show tooltip when title is not truncated', async () => {
    vi.useFakeTimers()
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader title="Test Title" />
      </NextIntlClientProvider>,
    )
    fireEvent.focus(screen.getByRole('heading'))
    await vi.advanceTimersByTimeAsync(500)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    vi.useRealTimers()
  })

  it('shows tooltip when title is truncated', async () => {
    vi.useFakeTimers()
    mockUseIsTruncated.mockReturnValue(true)
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <PageHeader title="Test Title" />
      </NextIntlClientProvider>,
    )
    fireEvent.focus(screen.getByRole('heading'))
    await vi.advanceTimersByTimeAsync(500)
    expect(screen.getByRole('tooltip')).toHaveTextContent('Test Title')
    vi.useRealTimers()
    mockUseIsTruncated.mockReturnValue(false)
  })
})

import { fireEvent, render, screen } from '@testing-library/react'
import { NextIntlClientProvider } from 'next-intl'
import { describe, expect, it } from 'vitest'

import messages from '@/messages/de.json'

import { TabsSection } from './TabsSections'

const onTabClickMock = vi.fn()
const tabsMock = [
  { value: 'testTab1', label: 'Test Tab 1' },
  { value: 'testTab2', label: 'Test Tab 2' },
  { value: 'testTab3', label: 'Test Tab 3' },
]

describe('TabsSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <TabsSection tabs={tabsMock} onClick={onTabClickMock} selectedTab={tabsMock[0].value} />
      </NextIntlClientProvider>,
    )
  })

  it('renders the tabs', () => {
    expect(screen.getByRole('tablist')).toBeInTheDocument()
    const tabs = screen.getAllByRole('tab')
    expect(tabs).toHaveLength(3)
    expect(tabs[0]).toHaveTextContent(tabsMock[0].label)
    expect(tabs[1]).toHaveTextContent(tabsMock[1].label)
    expect(tabs[2]).toHaveTextContent(tabsMock[2].label)
  })

  it('executes the onClick function on not selected tabs', () => {
    const tabs = screen.getAllByRole('tab')
    fireEvent.click(tabs[0])
    expect(onTabClickMock).not.toHaveBeenCalled()
    fireEvent.click(tabs[1])
    expect(onTabClickMock).toHaveBeenCalledTimes(1)
  })
})

describe('TabsSection primary tabs', () => {
  beforeEach(() => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <TabsSection tabs={tabsMock} onClick={onTabClickMock} selectedTab={tabsMock[0].value} />
      </NextIntlClientProvider>,
    )
  })

  it('renders the tabs with primary tabs styles', () => {
    expect(screen.getAllByRole('tab')[1]).toHaveClass('border-transparent')
    expect(screen.getAllByRole('tab')[1]).toHaveClass('border-b-2')
    expect(screen.getAllByRole('tab')[1]).toHaveClass('rounded-none')
    expect(screen.getAllByRole('tab')[1]).toHaveClass('hover:border-primary')
    expect(screen.getAllByRole('tab')[1]).toHaveClass('hover:bg-white')
  })

  it('highlights the selected tab', () => {
    expect(screen.getAllByRole('tab')[0]).toHaveClass('border-primary')
    expect(screen.getAllByRole('tab')[0]).not.toHaveClass('text-slate-400')
    expect(screen.getAllByRole('tab')[1]).not.toHaveClass('border-primary')
    expect(screen.getAllByRole('tab')[1]).toHaveClass('text-slate-400')
  })
})

describe('TabsSection subtabs', () => {
  beforeEach(() => {
    render(
      <NextIntlClientProvider locale="de" messages={messages}>
        <TabsSection tabs={tabsMock} onClick={onTabClickMock} selectedTab={tabsMock[0].value} isSubTabsSection />
      </NextIntlClientProvider>,
    )
  })

  it('renders the tabs with sub tabs styles', () => {
    expect(screen.getAllByRole('tab')[1]).toHaveClass('rounded-sm')
    expect(screen.getAllByRole('tab')[1]).toHaveClass('no-underline')
    expect(screen.getAllByRole('tab')[1]).toHaveClass('hover:bg-white')
  })

  it('highlights the selected tab', () => {
    expect(screen.getAllByRole('tab')[0]).toHaveClass('bg-white')
    expect(screen.getAllByRole('tab')[0]).toHaveClass('shadow-sm')
    expect(screen.getAllByRole('tab')[1]).not.toHaveClass('bg-white')
    expect(screen.getAllByRole('tab')[1]).not.toHaveClass('shadow-sm')
  })
})

import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { SegmentedControlBar, Tab } from './SegmentedControlBar'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const tabs: Tab<TestTab>[] = [
  { value: 'tab1', label: 'tabs.tab1' },
  { value: 'tab2', label: 'tabs.tab2' },
  { value: 'tab3', label: 'tabs.tab3' },
]

const defaultProps = {
  tabs,
  selectedTab: 'tab1',
  onTabChange: vi.fn(),
}
type TestTab = 'tab1' | 'tab2' | 'tab3'

const setup = (props = {}) => {
  const mergedProps = { ...defaultProps, ...props }
  return render(<SegmentedControlBar {...mergedProps} />)
}

describe('SegmentedControlBar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders all tabs', () => {
    setup()
    expect(screen.getByTestId('segmentedControlBar')).toBeInTheDocument()
    expect(screen.getByTestId('tab-tab1')).toBeInTheDocument()
    expect(screen.getByTestId('tab-tab2')).toBeInTheDocument()
    expect(screen.getByTestId('tab-tab3')).toBeInTheDocument()
  })

  test('clicking a tab calls onTabChange with correct tab value', () => {
    const onTabChange = vi.fn()
    setup({ onTabChange })

    fireEvent.click(screen.getByTestId('tab-tab2'))
    expect(onTabChange).toHaveBeenCalledWith('tab2')

    fireEvent.click(screen.getByTestId('tab-tab3'))
    expect(onTabChange).toHaveBeenCalledWith('tab3')
  })

  test('disabled tabs are not clickable', () => {
    const onTabChange = vi.fn()
    setup({ onTabChange, disabledTabs: ['tab2'] })

    fireEvent.click(screen.getByTestId('tab-tab2'))
    expect(onTabChange).not.toHaveBeenCalled()

    // Non-disabled tabs should still work
    fireEvent.click(screen.getByTestId('tab-tab3'))
    expect(onTabChange).toHaveBeenCalledWith('tab3')
  })

  test('disabled tabs have disabled attribute', () => {
    setup({ disabledTabs: ['tab2'] })

    expect(screen.getByTestId('tab-tab2')).toBeDisabled()
    expect(screen.getByTestId('tab-tab3')).not.toBeDisabled()
  })

  test('completed tabs show check icon', () => {
    setup({ completedTabs: ['tab3'], hasCompletionStatus: true })

    const tab1 = screen.getByTestId('tab-tab1')
    const tab2 = screen.getByTestId('tab-tab2')
    const tab3 = screen.getByTestId('tab-tab3')

    // Completed tabs should have CircleCheckBig (green icon)
    expect(tab1.querySelector('.text-muted-foreground')).toBeInTheDocument()
    expect(tab2.querySelector('.text-muted-foreground')).toBeInTheDocument()

    // Incomplete tabs should have CircleDashed (muted icon)
    expect(tab3.querySelector('.text-green-600')).toBeInTheDocument()
  })
})

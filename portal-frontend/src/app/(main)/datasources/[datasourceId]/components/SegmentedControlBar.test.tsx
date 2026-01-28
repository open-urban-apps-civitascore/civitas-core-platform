import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { DatasourceTab, SegmentedControlBar } from './SegmentedControlBar'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const defaultProps = {
  selectedTab: 'basicInfo' as DatasourceTab,
  onTabChange: vi.fn(),
}

const setup = (props = {}) => {
  const mergedProps = { ...defaultProps, ...props }
  return render(<SegmentedControlBar {...mergedProps} />)
}

describe('SegmentedControlBar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders all 5 tabs', () => {
    setup()
    expect(screen.getByTestId('segmentedControlBar')).toBeInTheDocument()
    expect(screen.getByTestId('tab-basicInfo')).toBeInTheDocument()
    expect(screen.getByTestId('tab-connector')).toBeInTheDocument()
    expect(screen.getByTestId('tab-dataStructure')).toBeInTheDocument()
    expect(screen.getByTestId('tab-accessPermissions')).toBeInTheDocument()
    expect(screen.getByTestId('tab-dataspaces')).toBeInTheDocument()
  })

  test('clicking a tab calls onTabChange with correct tab value', () => {
    const onTabChange = vi.fn()
    setup({ onTabChange })

    fireEvent.click(screen.getByTestId('tab-connector'))
    expect(onTabChange).toHaveBeenCalledWith('connector')

    fireEvent.click(screen.getByTestId('tab-dataStructure'))
    expect(onTabChange).toHaveBeenCalledWith('dataStructure')
  })

  test('disabled tabs are not clickable', () => {
    const onTabChange = vi.fn()
    setup({ onTabChange, disabledTabs: ['connector', 'dataStructure'] })

    fireEvent.click(screen.getByTestId('tab-connector'))
    expect(onTabChange).not.toHaveBeenCalled()

    fireEvent.click(screen.getByTestId('tab-dataStructure'))
    expect(onTabChange).not.toHaveBeenCalled()

    // Non-disabled tabs should still work
    fireEvent.click(screen.getByTestId('tab-accessPermissions'))
    expect(onTabChange).toHaveBeenCalledWith('accessPermissions')
  })

  test('disabled tabs have disabled attribute', () => {
    setup({ disabledTabs: ['connector'] })

    expect(screen.getByTestId('tab-connector')).toBeDisabled()
    expect(screen.getByTestId('tab-basicInfo')).not.toBeDisabled()
  })

  test('completed tabs show check icon', () => {
    setup({ completedTabs: ['basicInfo', 'connector'] })

    const basicInfoTab = screen.getByTestId('tab-basicInfo')
    const connectorTab = screen.getByTestId('tab-connector')
    const dataStructureTab = screen.getByTestId('tab-dataStructure')

    // Completed tabs should have CircleCheckBig (green icon)
    expect(basicInfoTab.querySelector('.text-green-600')).toBeInTheDocument()
    expect(connectorTab.querySelector('.text-green-600')).toBeInTheDocument()

    // Incomplete tabs should have CircleDashed (muted icon)
    expect(dataStructureTab.querySelector('.text-muted-foreground')).toBeInTheDocument()
  })
})

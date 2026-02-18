import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'

import { TagsMultiSelect } from './TagsMultiSelect'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

const defaultProps = {
  selectedTags: [] as string[],
  onTagsChange: vi.fn(),
}

const setup = (props = {}) => {
  const mergedProps = { ...defaultProps, ...props }
  return render(<TagsMultiSelect {...mergedProps} />)
}

describe('TagsMultiSelect', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('renders with placeholder when no tags selected', () => {
    setup()
    expect(screen.getByTestId('tagsDropdownTrigger')).toBeInTheDocument()
    expect(screen.getByText('form.tagsPlaceholder')).toBeInTheDocument()
  })

  test('renders selected tags as badges', () => {
    setup({ selectedTags: ['Tag 1', 'Tag 2'] })
    expect(screen.getByText('Tag 1')).toBeInTheDocument()
    expect(screen.getByText('Tag 2')).toBeInTheDocument()
    expect(screen.queryByText('form.tagsPlaceholder')).not.toBeInTheDocument()
  })

  test('opens popover when trigger button is clicked', async () => {
    setup()
    const trigger = screen.getByTestId('tagsDropdownTrigger')
    fireEvent.click(trigger)

    await waitFor(() => {
      expect(screen.getByPlaceholderText('form.tagsSearch')).toBeInTheDocument()
    })
  })

  test('shows available tags in the command list', async () => {
    setup()
    fireEvent.click(screen.getByTestId('tagsDropdownTrigger'))

    await waitFor(() => {
      expect(screen.getByText('Tag 1')).toBeInTheDocument()
      expect(screen.getByText('Tag 2')).toBeInTheDocument()
      expect(screen.getByText('Tag 3')).toBeInTheDocument()
    })
  })

  test('selecting a tag calls onTagsChange with added tag', async () => {
    const onTagsChange = vi.fn()
    setup({ onTagsChange, selectedTags: [] })

    fireEvent.click(screen.getByTestId('tagsDropdownTrigger'))

    await waitFor(() => {
      expect(screen.getByText('Tag 1')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText('Tag 1'))
    expect(onTagsChange).toHaveBeenCalledWith(['Tag 1'])
  })

  test('deselecting a tag calls onTagsChange with removed tag', async () => {
    const onTagsChange = vi.fn()
    setup({ onTagsChange, selectedTags: ['Tag 1', 'Tag 2'] })

    fireEvent.click(screen.getByTestId('tagsDropdownTrigger'))

    await waitFor(() => {
      expect(screen.getAllByText('Tag 1').length).toBeGreaterThanOrEqual(1)
    })

    // Find and click the Tag 1 item in the dropdown list
    const commandItems = screen.getAllByRole('option')
    const tag1Item = commandItems.find(item => item.textContent?.includes('Tag 1'))
    if (tag1Item) {
      fireEvent.click(tag1Item)
    }

    expect(onTagsChange).toHaveBeenCalledWith(['Tag 2'])
  })

  test('disabled state prevents interaction', () => {
    setup({ isDisabled: true })
    const trigger = screen.getByTestId('tagsDropdownTrigger')
    expect(trigger).toBeDisabled()
  })

  test('disabled state hides chevron icon', () => {
    setup({ isDisabled: true })
    const trigger = screen.getByTestId('tagsDropdownTrigger')
    // When disabled, the ChevronDown should not be rendered
    expect(trigger.querySelector('svg.opacity-50')).not.toBeInTheDocument()
  })
})

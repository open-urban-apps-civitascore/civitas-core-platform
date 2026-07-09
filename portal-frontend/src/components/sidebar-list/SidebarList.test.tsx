import { fireEvent, render, screen } from '@testing-library/react'

import { SidebarList, SidebarListItem } from './SidebarList'

const makeItem = (overrides: Partial<SidebarListItem> = {}): SidebarListItem => ({
  label: 'Test Layer',
  value: '00000000-0000-0000-0000-000000000001',
  displayTitle: 'Test Layer',
  ...overrides,
})

interface RenderProps {
  items?: SidebarListItem[]
  selectedItemIndex?: number | null
  isReadOnly?: boolean
  onSelectItem?: (index: number) => void
  onAddItem?: () => void
}

const renderSidebar = ({
  items = [],
  selectedItemIndex = null,
  isReadOnly = false,
  onSelectItem = vi.fn(),
  onAddItem = vi.fn(),
}: RenderProps = {}) =>
  render(
    <SidebarList
      items={items}
      selectedItemIndex={selectedItemIndex}
      isReadOnly={isReadOnly}
      addButtonLabel="Add Layer"
      addButtonTestId="sidebarAddLayerButton"
      onSelectItem={onSelectItem}
      onAddItem={onAddItem}
    />,
  )

describe('SidebarList', () => {
  describe('Item display titles', () => {
    it('renders the displayTitle of each item', () => {
      renderSidebar({
        items: [makeItem({ displayTitle: 'My Layer' })],
      })
      expect(screen.getByRole('button', { name: 'My Layer' })).toBeInTheDocument()
    })

    it('renders multiple items with their display titles', () => {
      renderSidebar({
        items: [
          makeItem({ value: '1', displayTitle: 'Layer 1' }),
          makeItem({ value: '2', displayTitle: 'Layer 2' }),
          makeItem({ value: '3', displayTitle: 'Layer 3' }),
        ],
      })
      expect(screen.getByRole('button', { name: 'Layer 1' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Layer 2' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Layer 3' })).toBeInTheDocument()
    })
  })

  describe('Selection state', () => {
    it('applies active styles to the selected item button', () => {
      renderSidebar({
        items: [makeItem({ displayTitle: 'Layer 1' })],
        selectedItemIndex: 0,
      })
      expect(screen.getByRole('button', { name: 'Layer 1' })).toHaveClass('bg-accent', 'font-medium')
    })

    it('does not apply active styles to non-selected item buttons', () => {
      renderSidebar({
        items: [makeItem({ value: '1', displayTitle: 'Layer 1' }), makeItem({ value: '2', displayTitle: 'Layer 2' })],
        selectedItemIndex: 0,
      })
      expect(screen.getByRole('button', { name: 'Layer 2' })).not.toHaveClass('bg-accent')
      expect(screen.getByRole('button', { name: 'Layer 2' })).not.toHaveClass('font-medium')
    })

    it('does not apply active styles to any button when selectedItemIndex is null', () => {
      renderSidebar({
        items: [makeItem({ displayTitle: 'Layer 1' })],
        selectedItemIndex: null,
      })
      expect(screen.getByRole('button', { name: 'Layer 1' })).not.toHaveClass('bg-accent')
    })
  })

  describe('Error state', () => {
    it('applies destructive text style when hasError is true', () => {
      renderSidebar({
        items: [makeItem({ displayTitle: 'Error Layer', hasError: true })],
      })
      expect(screen.getByRole('button', { name: 'Error Layer' })).toHaveClass('text-destructive')
    })

    it('does not apply destructive text style when hasError is false', () => {
      renderSidebar({
        items: [makeItem({ displayTitle: 'Normal Layer', hasError: false })],
      })
      expect(screen.getByRole('button', { name: 'Normal Layer' })).not.toHaveClass('text-destructive')
    })

    it('does not apply destructive text style when hasError is undefined', () => {
      renderSidebar({
        items: [makeItem({ displayTitle: 'Normal Layer' })],
      })
      expect(screen.getByRole('button', { name: 'Normal Layer' })).not.toHaveClass('text-destructive')
    })
  })

  describe('Interactions', () => {
    it('calls onSelectItem with the correct index when an item is clicked', () => {
      const onSelectItem = vi.fn()
      renderSidebar({
        items: [makeItem({ value: '1', displayTitle: 'Layer 1' }), makeItem({ value: '2', displayTitle: 'Layer 2' })],
        onSelectItem,
      })
      fireEvent.click(screen.getByRole('button', { name: 'Layer 2' }))
      expect(onSelectItem).toHaveBeenCalledWith(1)
    })

    it('calls onAddItem when the add button is clicked', () => {
      const onAddItem = vi.fn()
      renderSidebar({ onAddItem })
      fireEvent.click(screen.getByTestId('sidebarAddLayerButton'))
      expect(onAddItem).toHaveBeenCalledOnce()
    })
  })

  describe('Read-only mode', () => {
    it('renders the add button when not read-only', () => {
      renderSidebar({ isReadOnly: false })
      expect(screen.getByTestId('sidebarAddLayerButton')).toBeInTheDocument()
    })

    it('does not render the add button when read-only', () => {
      renderSidebar({ isReadOnly: true })
      expect(screen.queryByTestId('sidebarAddLayerButton')).not.toBeInTheDocument()
    })
  })

  describe('Edge cases', () => {
    it('renders no item buttons when items is empty', () => {
      renderSidebar({ items: [] })
      expect(screen.queryAllByRole('listitem')).toHaveLength(0)
    })
  })
})

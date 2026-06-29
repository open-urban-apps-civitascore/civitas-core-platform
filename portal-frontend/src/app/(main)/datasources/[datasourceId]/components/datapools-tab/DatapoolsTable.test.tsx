import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi } from 'vitest'

import { Datapool } from '@/types/datapools'

import { DatapoolsTable } from './DatapoolsTable'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  usePathname: () => '/',
  useSearchParams: () => new URLSearchParams(),
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

const mockDatapools: Datapool[] = [
  {
    id: 'dp-1',
    name: 'Datapool Alpha',
    description: 'First datapool',
    contactPerson: null,
    createdAt: '2024-01-01T00:00:00Z',
    modifiedAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 'dp-2',
    name: 'Datapool Beta',
    description: 'Second datapool',
    contactPerson: { id: 'u-1', name: 'Alice' },
    createdAt: '2024-01-02T00:00:00Z',
    modifiedAt: '2024-01-02T00:00:00Z',
  },
]

describe('DatapoolsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Rendering', () => {
    it('renders the table', () => {
      render(<DatapoolsTable datapools={mockDatapools} isLoading={false} />)
      expect(screen.getByTestId('datapoolsTable')).toBeInTheDocument()
    })

    it('renders datapool names', () => {
      render(<DatapoolsTable datapools={mockDatapools} isLoading={false} />)
      expect(screen.getByText('Datapool Alpha')).toBeInTheDocument()
      expect(screen.getByText('Datapool Beta')).toBeInTheDocument()
    })

    it('renders datapool descriptions', () => {
      render(<DatapoolsTable datapools={mockDatapools} isLoading={false} />)
      expect(screen.getByText('First datapool')).toBeInTheDocument()
      expect(screen.getByText('Second datapool')).toBeInTheDocument()
    })

    it('renders contact person name when present', () => {
      render(<DatapoolsTable datapools={mockDatapools} isLoading={false} />)
      expect(screen.getByText('Alice')).toBeInTheDocument()
    })

    it('shows noContact label when contact person is absent', () => {
      render(<DatapoolsTable datapools={mockDatapools} isLoading={false} />)
      expect(screen.getByText('noContact')).toBeInTheDocument()
    })

    it('renders empty table without errors when no datapools', () => {
      render(<DatapoolsTable datapools={[]} isLoading={false} />)
      expect(screen.getByTestId('datapoolsTable')).toBeInTheDocument()
    })
  })

  describe('Delete action', () => {
    it('does not render action column without onDeleteClick', () => {
      render(<DatapoolsTable datapools={mockDatapools} isLoading={false} />)
      expect(screen.queryByText('tableHeaders.action')).not.toBeInTheDocument()
    })

    it('renders action column with onDeleteClick', () => {
      render(<DatapoolsTable datapools={mockDatapools} isLoading={false} onDeleteClick={vi.fn()} />)
      expect(screen.getByText('tableHeaders.action')).toBeInTheDocument()
    })

    it('calls onDeleteClick with the correct datapool id', async () => {
      const user = userEvent.setup()
      const onDeleteClick = vi.fn()
      render(<DatapoolsTable datapools={mockDatapools} isLoading={false} onDeleteClick={onDeleteClick} />)

      const menuButton = screen.getAllByRole('button', { name: 'Open menu' })[0]
      await user.click(menuButton)
      const deleteItem = await screen.findByText('actions.delete')
      await user.click(deleteItem)

      expect(onDeleteClick).toHaveBeenCalledWith('dp-1')
    })
  })

  describe('Pagination', () => {
    it('hides pagination when datapools fit on one page', () => {
      render(<DatapoolsTable datapools={mockDatapools} isLoading={false} />)
      expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    })

    it('shows pagination when datapools exceed page size', () => {
      const manyDatapools: Datapool[] = Array.from({ length: 11 }, (_, i) => ({
        id: `dp-${i}`,
        name: `Datapool ${i}`,
        description: `Description ${i}`,
        contactPerson: null,
        createdAt: '2024-01-01T00:00:00Z',
        modifiedAt: '2024-01-01T00:00:00Z',
      }))
      render(<DatapoolsTable datapools={manyDatapools} isLoading={false} />)
      expect(screen.getByRole('navigation')).toBeInTheDocument()
    })
  })
})

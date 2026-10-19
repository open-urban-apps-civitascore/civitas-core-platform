import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { toast } from 'sonner'
import { vi } from 'vitest'

import { usePermissions } from '@/hooks/use-permissions'

import { DatasetCreateForm } from './DatasetCreateForm'

const mockPush = vi.fn()
const mockMutateAsync = vi.fn()

let mockSearchParams = new URLSearchParams('page=1')

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
    refresh: vi.fn(),
  }),
  useSearchParams: () => mockSearchParams,
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useCreateDataset: () => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
  }),
}))

vi.mock('sonner', () => ({
  toast: { success: vi.fn(), error: vi.fn(), info: vi.fn() },
}))

vi.mock('@/contexts/unsaved-changes/UnsavedChangesContext', () => ({
  useUnsavedChanges: () => ({
    setHasUnsavedChanges: vi.fn(),
    setSaveHandler: vi.fn(),
  }),
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: vi.fn(),
}))

const mockCanReadDatapools = (canRead: boolean) => {
  vi.mocked(usePermissions).mockReturnValue({
    hasPermission: vi.fn().mockReturnValue(canRead),
    hasPermissionInScope: vi.fn(),
    hasAnyPermission: vi.fn(),
    hasScopedPermission: vi.fn(),
  })
}

const setup = () => {
  const client = new QueryClient()
  return render(
    <QueryClientProvider client={client}>
      <DatasetCreateForm />
    </QueryClientProvider>,
  )
}

describe('DatasetCreateForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSearchParams = new URLSearchParams('page=1')
    mockMutateAsync.mockResolvedValue({ data: { id: 'test-id-123' } })
    mockCanReadDatapools(true)
  })

  test('renders the form with correct elements', () => {
    setup()
    expect(screen.getByTestId('createDatasetPage')).toBeInTheDocument()
    expect(screen.getByTestId('datasetCreateForm')).toBeInTheDocument()
  })

  test('renders title and subtitle', () => {
    setup()
    expect(screen.getByText('create.title')).toBeInTheDocument()
    expect(screen.getByText('create.subtitle')).toBeInTheDocument()
    expect(screen.getByText('create.basicInfo.title')).toBeInTheDocument()
    expect(screen.getByText('info.creationSubtitle')).toBeInTheDocument()
  })

  test('submit button is disabled when form is not dirty', () => {
    setup()
    expect(screen.getByRole('button', { name: 'actions.saveAndContinue' })).toBeDisabled()
  })

  test('submit button is enabled when name is filled in', () => {
    setup()
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Test Dataset' } })
    expect(screen.getByRole('button', { name: 'actions.saveAndContinue' })).toBeEnabled()
  })

  test('submit button is disabled again when name is cleared', () => {
    setup()
    const nameInput = screen.getByTestId('nameTextField')
    fireEvent.change(nameInput, { target: { value: 'Test Dataset' } })
    fireEvent.change(nameInput, { target: { value: '' } })
    expect(screen.getByRole('button', { name: 'actions.saveAndContinue' })).toBeDisabled()
  })

  test('does not submit when description is left blank', async () => {
    setup()
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Test Dataset' } })
    fireEvent.click(screen.getByRole('button', { name: 'actions.saveAndContinue' }))

    await waitFor(() => {
      expect(mockMutateAsync).not.toHaveBeenCalled()
    })
  })

  test('renders the description field', () => {
    setup()
    expect(screen.getByTestId('descriptionTextArea')).toBeInTheDocument()
  })

  test('calls createDataset mutation with description when filled in', async () => {
    setup()
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Test Dataset' } })
    fireEvent.change(screen.getByTestId('descriptionTextArea'), { target: { value: 'A description' } })
    fireEvent.click(screen.getByRole('button', { name: 'actions.saveAndContinue' }))

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith({ name: 'Test Dataset', description: 'A description' })
    })
  })

  test('navigates to dataset overview on successful creation', async () => {
    setup()
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Test Dataset' } })
    fireEvent.change(screen.getByTestId('descriptionTextArea'), { target: { value: 'A description' } })
    fireEvent.click(screen.getByRole('button', { name: 'actions.saveAndContinue' }))

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith('/datasets/test-id-123?mode=edit')
    })
  })

  test('cancel navigates back to datasets list with current search params', () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'actions.cancel' }))
    expect(mockPush).toHaveBeenCalledWith('/datasets?page=1')
  })

  test('cancel navigates back to datapool dataset-tab when source=datapools is set', () => {
    mockSearchParams = new URLSearchParams('source=datapools&datapoolId=dp-42')
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'actions.cancel' }))
    expect(mockPush).toHaveBeenCalledWith('/datapools/dp-42?subtab=datasets')
  })

  describe('Datapool select', () => {
    test('renders the datapool select', () => {
      setup()
      expect(screen.getByRole('combobox')).toBeInTheDocument()
    })

    test('is enabled and shows the placeholder when the user can read datapools', () => {
      mockCanReadDatapools(true)
      setup()
      const trigger = screen.getByRole('combobox')
      expect(trigger).toBeEnabled()
      expect(within(trigger).getByText('form.datapoolSelectPlaceholder')).toBeInTheDocument()
    })

    test('is disabled and shows notAvailable when the user cannot read datapools', () => {
      mockCanReadDatapools(false)
      setup()
      const trigger = screen.getByRole('combobox')
      expect(trigger).toBeDisabled()
      expect(within(trigger).getByText('info.notAvailable')).toBeInTheDocument()
    })

    test('prefills the datapool select with the datapoolId from URL params', () => {
      mockSearchParams = new URLSearchParams('datapoolId=dp-42&source=datapools')
      const client = new QueryClient()
      render(
        <QueryClientProvider client={client}>
          <DatasetCreateForm datapoolOptions={[{ value: 'dp-42', label: 'Datapool 42' }]} />
        </QueryClientProvider>,
      )
      expect(screen.getAllByText('Datapool 42').length).toBeGreaterThan(0)
    })
  })

  test('shows error toast on unexpected error during creation', async () => {
    mockMutateAsync.mockRejectedValueOnce(new Error('Network Error'))

    setup()
    fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'Test Dataset' } })
    fireEvent.change(screen.getByTestId('descriptionTextArea'), { target: { value: 'A description' } })
    fireEvent.click(screen.getByRole('button', { name: 'actions.saveAndContinue' }))

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith('errors.unexpectedError')
    })
  })
})

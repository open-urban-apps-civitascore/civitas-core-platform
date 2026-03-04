import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'

import { DatasetCreateForm } from './DatasetCreateForm'

const mockPush = vi.fn()
const mockMutate = vi.fn()

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
    refresh: vi.fn(),
  }),
  useSearchParams: () => new URLSearchParams('page=1'),
}))

vi.mock('@/app/services/api/datasets/clientRequests', () => ({
  useCreateDataset: () => ({
    mutate: mockMutate,
    isPending: false,
  }),
}))

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
  })

  test('renders form header and subtitle', () => {
    setup()
    expect(screen.getByText('create.form.title')).toBeInTheDocument()
    expect(screen.getByText('create.form.subtitle')).toBeInTheDocument()
  })

  test('submit button is disabled when form is not dirty', () => {
    setup()
    const submitButton = screen.getByRole('button', { name: 'actions.saveAndContinue' })
    expect(submitButton).toBeDisabled()
  })

  test('submit button is enabled after changing form values', () => {
    setup()
    const nameInput = screen.getByTestId('nameTextField')
    fireEvent.change(nameInput, { target: { value: 'Test Dataset' } })
    const submitButton = screen.getByRole('button', { name: 'actions.saveAndContinue' })
    expect(submitButton).toBeEnabled()
  })

  test('cancel button navigates back to datasets list', () => {
    setup()
    const cancelButton = screen.getByRole('button', { name: 'actions.cancel' })
    fireEvent.click(cancelButton)
    expect(mockPush).toHaveBeenCalledWith('/datasets?page=1')
  })

  test('calls createDataset mutation on form submission', async () => {
    setup()
    const nameInput = screen.getByTestId('nameTextField')
    fireEvent.change(nameInput, { target: { value: 'Test Dataset' } })

    const submitButton = screen.getByRole('button', { name: 'actions.saveAndContinue' })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(mockMutate).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'Test Dataset',
        }),
        expect.any(Object),
      )
    })
  })

  test('navigates to dataset overview on successful creation', async () => {
    mockMutate.mockImplementation((data, options) => {
      options.onSuccess({ data: { id: 'test-id-123' } })
    })

    setup()
    const nameInput = screen.getByTestId('nameTextField')
    fireEvent.change(nameInput, { target: { value: 'Test Dataset' } })

    const submitButton = screen.getByRole('button', { name: 'actions.saveAndContinue' })
    fireEvent.click(submitButton)

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith('/datasets/test-id-123?mode=edit')
    })
  })

  test('handles empty name validation', () => {
    setup()
    const nameInput = screen.getByTestId('nameTextField')
    fireEvent.change(nameInput, { target: { value: '' } })
    fireEvent.blur(nameInput)

    const submitButton = screen.getByRole('button', { name: 'actions.saveAndContinue' })
    expect(submitButton).toBeDisabled()
  })
})

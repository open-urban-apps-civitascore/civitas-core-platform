import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { toast } from 'sonner'
import { beforeEach, describe, expect, test, vi } from 'vitest'

import { DatastructureCreateForm } from './DatastructureCreateForm'

vi.mock('sonner', () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

const mockPush = vi.fn()
const mockSaveDatastructure = vi.fn()

const mockForm = {
  formState: { isDirty: false, errors: {} as Record<string, unknown> },
  control: {},
  handleSubmit: vi.fn(
    (onValid: () => Promise<void> | void, onInvalid?: (errors: Record<string, unknown>) => void) =>
      async (event?: Event) => {
        event?.preventDefault?.()
        if (Object.keys(mockForm.formState.errors).length > 0) {
          onInvalid?.(mockForm.formState.errors)
          return
        }
        return onValid()
      },
  ),
}

const mockHookState = {
  form: mockForm,
  isLoading: false,
  saveDatastructure: mockSaveDatastructure,
}

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

vi.mock('../../[datastructureId]/hooks/useDatastructureCreation', () => ({
  useDatastructureCreation: () => mockHookState,
}))

vi.mock('@/components/ui/form', () => ({
  Form: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

vi.mock('@/components/form/fields/TextField', () => ({
  TextField: ({ placeholder }: { placeholder: string }) => <input aria-label="name" placeholder={placeholder} />,
}))

vi.mock('@/components/form/fields/FormTextArea', () => ({
  FormTextArea: ({ placeholder }: { placeholder: string }) => (
    <textarea aria-label="description" placeholder={placeholder} />
  ),
}))

vi.mock('@/components/loading-spinner/LoadingSpinner', () => ({
  LoadingSpinner: () => <div data-testid="loadingSpinner" />,
}))

const setup = () => render(<DatastructureCreateForm />)

describe('DatastructureCreateForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockForm.formState.isDirty = false
    mockForm.formState.errors = {}
    mockHookState.isLoading = false
    mockHookState.saveDatastructure = mockSaveDatastructure
  })

  test('renders the form with correct elements', () => {
    setup()
    expect(screen.getByTestId('createDatastructurePage')).toBeInTheDocument()
    expect(screen.getByTestId('datastructureCreateForm')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'actions.cancel' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'actions.saveAndContinue' })).toBeInTheDocument()
  })

  test('renders title and subtitle', () => {
    setup()
    expect(screen.getByText('create.title')).toBeInTheDocument()
    expect(screen.getByText('create.subtitle')).toBeInTheDocument()
  })

  test('submit button is disabled when form is not dirty', () => {
    setup()
    expect(screen.getByRole('button', { name: 'actions.saveAndContinue' })).toBeDisabled()
  })

  test('submit button is enabled after changing form values', () => {
    mockForm.formState.isDirty = true

    setup()

    expect(screen.getByRole('button', { name: 'actions.saveAndContinue' })).toBeEnabled()
  })

  test('cancel button navigates back to datastructures list', () => {
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'actions.cancel' }))
    expect(mockPush).toHaveBeenCalledWith('/datastructures?page=1')
  })

  test('calls createDatastructure mutation on form submission', async () => {
    mockForm.formState.isDirty = true
    mockSaveDatastructure.mockResolvedValue({ id: 'test-id' })

    setup()
    fireEvent.click(screen.getByRole('button', { name: 'actions.saveAndContinue' }))

    await waitFor(() => {
      expect(mockSaveDatastructure).toHaveBeenCalledTimes(1)
    })
    expect(mockPush).toHaveBeenCalledWith('/datastructures/test-id?mode=edit')
  })

  test('submits via saveDatastructure and navigates to edit mode when creation succeeds', async () => {
    mockForm.formState.isDirty = true
    mockSaveDatastructure.mockResolvedValue({ id: 'test-id' })

    setup()
    fireEvent.click(screen.getByRole('button', { name: 'actions.saveAndContinue' }))

    await waitFor(() => {
      expect(mockSaveDatastructure).toHaveBeenCalledTimes(1)
      expect(mockPush).toHaveBeenCalledWith('/datastructures/test-id?mode=edit')
    })
  })

  test('does not navigate when saveDatastructure returns no response', async () => {
    mockForm.formState.isDirty = true
    mockSaveDatastructure.mockResolvedValue(undefined)

    setup()
    fireEvent.click(screen.getByRole('button', { name: 'actions.saveAndContinue' }))

    await waitFor(() => {
      expect(mockSaveDatastructure).toHaveBeenCalledTimes(1)
    })

    expect(mockPush).not.toHaveBeenCalled()
  })

  test('shows a validation error toast and does not submit when description is missing', async () => {
    mockForm.formState.isDirty = true
    mockForm.formState.errors = { description: { type: 'too_small', message: 'Required' } }

    setup()
    fireEvent.click(screen.getByRole('button', { name: 'actions.saveAndContinue' }))

    await waitFor(() => {
      expect(toast.error).toHaveBeenCalledWith('errors.formInvalid')
    })
    expect(mockSaveDatastructure).not.toHaveBeenCalled()
  })
})

describe('DatastructureCreateForm loading state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockForm.formState.isDirty = true
    mockHookState.isLoading = true
  })

  test('shows loading spinner when form is submitting', () => {
    setup()

    expect(screen.getByTestId('loadingSpinner')).toBeInTheDocument()
    expect(screen.queryByPlaceholderText('form.namePlaceholder')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'actions.saveAndContinue' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'actions.cancel' })).toBeDisabled()
  })
})

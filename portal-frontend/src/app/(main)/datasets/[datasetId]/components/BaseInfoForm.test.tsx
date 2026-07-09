import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { FormProvider, useForm } from 'react-hook-form'
import { vi } from 'vitest'

import { useGetCurrentUser } from '@/app/services/api/users/clientRequests'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { DatasetFormDraft } from '@/types/datasets'

import { BaseInfoForm } from './BaseInfoForm'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetCurrentUser: vi.fn(),
}))

const mockCurrentUser = (
  permissions: PermissionName[],
  scopeType: 'TENANT' | 'DATAPOOL' = 'TENANT',
  scopeId: string | null = null,
) => {
  vi.mocked(useGetCurrentUser).mockReturnValue({
    data: {
      username: 'test',
      email: 'test@test.com',
      title: 'MR' as const,
      firstName: 'Test',
      lastName: 'User',
      assignments: [{ scopeType, scopeId, permissions }],
    },
  } as ReturnType<typeof useGetCurrentUser>)
}

const mockedDatapoolOptions = [
  { value: 'datapool-1', label: 'Datapool 1' },
  { value: 'datapool-2', label: 'Datapool 2' },
]

const TestWrapper = ({ isReadOnly = true, isLoading = false }: { isReadOnly?: boolean; isLoading?: boolean }) => {
  const form = useForm<DatasetFormDraft>({
    defaultValues: {
      id: '1',
      name: 'Dataset 1',
      description: 'Description',
      openDataAccess: false,
      datapoolId: 'datapool-1',
    },
  })
  return (
    <FormProvider {...form}>
      <BaseInfoForm form={form} isReadOnly={isReadOnly} isLoading={isLoading} datapoolOptions={mockedDatapoolOptions} />
    </FormProvider>
  )
}

describe('BaseInfoForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockCurrentUser([PERMISSION_NAMES.DATAPOOL_READ], 'DATAPOOL', 'datapool-1')
  })

  it('renders the section title', () => {
    render(<TestWrapper />)
    expect(screen.getByText('overview.info.title')).toBeInTheDocument()
  })

  it('shows a loading spinner instead of fields when isLoading is true', () => {
    render(<TestWrapper isLoading={true} />)
    expect(screen.getByText('loading')).toBeInTheDocument()
    expect(screen.queryByTestId('nameTextField')).not.toBeInTheDocument()
  })

  it('shows fields when isLoading is false', () => {
    render(<TestWrapper isLoading={false} />)
    expect(screen.queryByText('loading')).not.toBeInTheDocument()
    expect(screen.getByTestId('nameTextField')).toBeInTheDocument()
  })

  it('shows the completion icon when the dataset has a name', () => {
    render(<TestWrapper />)
    expect(document.querySelector('svg.lucide-circle-check-big')).toBeInTheDocument()
    expect(document.querySelector('svg.lucide-circle:not(.lucide-circle-check-big)')).not.toBeInTheDocument()
  })

  it('shows the incomplete icon when the dataset name is empty', () => {
    const TestWrapperEmpty = () => {
      const form = useForm<DatasetFormDraft>({ defaultValues: { id: '1', name: '' } })
      return (
        <FormProvider {...form}>
          <BaseInfoForm form={form} isReadOnly={true} isLoading={false} datapoolOptions={[]} />
        </FormProvider>
      )
    }
    render(<TestWrapperEmpty />)
    expect(document.querySelector('svg.lucide-circle-dashed')).toBeInTheDocument()
    expect(document.querySelector('svg.lucide-circle-check-big')).not.toBeInTheDocument()
  })

  it('shows the name field with its default value', () => {
    render(<TestWrapper />)
    expect(screen.getByDisplayValue('Dataset 1')).toBeInTheDocument()
  })

  it('shows the description field with its default value', () => {
    render(<TestWrapper />)
    expect(screen.getByDisplayValue('Description')).toBeInTheDocument()
  })

  it('renders the datapool select', () => {
    render(<TestWrapper />)
    expect(screen.getByTestId('datapoolIdSelectTrigger')).toBeInTheDocument()
  })

  it('disables all fields when isReadOnly is true', () => {
    render(<TestWrapper isReadOnly={true} />)
    expect(screen.getByTestId('nameTextField')).toBeDisabled()
    expect(screen.getByRole('combobox')).toBeDisabled()
    expect(screen.getByTestId('descriptionTextArea')).toBeDisabled()
  })

  it('enables all fields when isReadOnly is false', () => {
    render(<TestWrapper isReadOnly={false} />)
    expect(screen.getByTestId('nameTextField')).not.toBeDisabled()
    expect(screen.getByRole('combobox')).not.toBeDisabled()
    expect(screen.getByTestId('descriptionTextArea')).not.toBeDisabled()
  })

  it('renders without errors when datapoolOptions is empty', () => {
    const TestWrapperNoOptions = () => {
      const form = useForm<DatasetFormDraft>({ defaultValues: { id: '1', name: 'Dataset 1' } })
      return (
        <FormProvider {...form}>
          <BaseInfoForm form={form} isReadOnly={true} isLoading={false} datapoolOptions={[]} />
        </FormProvider>
      )
    }
    render(<TestWrapperNoOptions />)
    expect(screen.getByTestId('datapoolIdSelectTrigger')).toBeInTheDocument()
  })

  describe('Datapool permission guards', () => {
    it('disables the datapool select when user has no DATAPOOL_READ permission', () => {
      mockCurrentUser([])
      render(<TestWrapper isReadOnly={false} />)
      expect(screen.getByRole('combobox')).toBeDisabled()
    })

    it('shows "anonymousDatapool" when user has no DATAPOOL_READ and a datapool is assigned', () => {
      mockCurrentUser([])
      render(<TestWrapper isReadOnly={false} />)
      expect(screen.getByRole('combobox')).toHaveTextContent('anonymousDatapool')
    })

    it('does not show "anonymousDatapool" when user has no DATAPOOL_READ and no datapool is assigned', () => {
      mockCurrentUser([])
      const TestWrapperNoDatapool = () => {
        const form = useForm<DatasetFormDraft>({ defaultValues: { id: '1', name: 'Dataset 1', datapoolId: null } })
        return (
          <FormProvider {...form}>
            <BaseInfoForm form={form} isReadOnly={false} isLoading={false} datapoolOptions={[]} />
          </FormProvider>
        )
      }
      render(<TestWrapperNoDatapool />)
      expect(screen.queryByText('anonymousDatapool')).not.toBeInTheDocument()
    })

    it('enables the datapool select when user has DATAPOOL_READ but no scoped permission for the assigned datapool', () => {
      mockCurrentUser([PERMISSION_NAMES.DATAPOOL_READ], 'DATAPOOL', 'other-pool')
      render(<TestWrapper isReadOnly={false} />)
      expect(screen.getByRole('combobox')).not.toBeDisabled()
    })

    it('shows "anonymousDatapool" as option when user has DATAPOOL_READ but no scoped permission for the assigned datapool', () => {
      mockCurrentUser([PERMISSION_NAMES.DATAPOOL_READ], 'DATAPOOL', 'other-pool')
      render(<TestWrapper isReadOnly={false} />)
      fireEvent.click(screen.getByRole('combobox'))
      expect(screen.getAllByText('anonymousDatapool').length).toBeGreaterThan(0)
      expect(screen.queryByText('Datapool 1')).not.toBeInTheDocument()
      expect(screen.getByText('Datapool 2')).toBeInTheDocument()
    })
  })

  describe('ChangeDatapoolModal', () => {
    const openDatapoolDropdownAndSelectOption = (optionLabel: string) => {
      fireEvent.click(screen.getByRole('combobox'))
      fireEvent.click(screen.getByText(optionLabel))
    }

    it('is not shown initially', () => {
      render(<TestWrapper isReadOnly={false} />)

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('opens when a new datapool is selected', () => {
      render(<TestWrapper isReadOnly={false} />)

      openDatapoolDropdownAndSelectOption('Datapool 2')

      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    describe('with form value access', () => {
      let capturedForm: ReturnType<typeof useForm<DatasetFormDraft>>

      const TestWrapperWithRef = () => {
        const form = useForm<DatasetFormDraft>({
          defaultValues: {
            id: '1',
            name: 'Dataset 1',
            description: 'Description',
            openDataAccess: false,
            datapoolId: 'datapool-1',
          },
        })
        capturedForm = form
        return (
          <FormProvider {...form}>
            <BaseInfoForm form={form} isReadOnly={false} isLoading={false} datapoolOptions={mockedDatapoolOptions} />
          </FormProvider>
        )
      }

      beforeEach(() => {
        render(<TestWrapperWithRef />)
        openDatapoolDropdownAndSelectOption('Datapool 2')
      })

      it('sets the new datapool value and closes the modal when the user confirms', async () => {
        fireEvent.click(screen.getByText('confirmButton'))

        await waitFor(() => {
          expect(capturedForm.getValues('datapoolId')).toBe('datapool-2')
          expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
        })
      })

      it('keeps the previous datapool value and closes the modal when the user cancels', async () => {
        fireEvent.click(screen.getByText('cancel'))

        await waitFor(() => {
          expect(capturedForm.getValues('datapoolId')).toBe('datapool-1')
          expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
        })
      })
    })
  })
})

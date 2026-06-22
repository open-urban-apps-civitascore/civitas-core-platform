import { zodResolver } from '@hookform/resolvers/zod'
import { fireEvent, render, screen } from '@testing-library/react'
import { useForm } from 'react-hook-form'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useGetUsers } from '@/app/services/api/users/clientRequests'
import { Form } from '@/components/ui/form'
import { usePermissions } from '@/hooks/use-permissions'
import { AssignmentScope } from '@/types/assignments'
import { PERMISSION_NAMES, PermissionName } from '@/types/currentUser'
import { Datapool, DatapoolFormData, DatapoolFormSchema } from '@/types/datapools'

import { BasicInfoTab } from './BasicInfoTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/app/services/api/users/clientRequests', () => ({
  useGetUsers: vi.fn(),
}))

vi.mock('@/hooks/use-permissions', () => ({
  usePermissions: vi.fn(),
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

const mockPermissions = (permissions: PermissionName[]) => {
  vi.mocked(usePermissions).mockReturnValue({
    hasPermission: (p: PermissionName) => permissions.includes(p),
    hasPermissionInScope: (_p: PermissionName, _scopeType: AssignmentScope) => false,
    hasAnyPermission: (...ps: PermissionName[]) => ps.some(p => permissions.includes(p)),
    hasScopedPermission: (_p: PermissionName, _scopeType: AssignmentScope, _scopeId: string) => false,
  })
}

const mockDatapool: Datapool = {
  id: 'dp1',
  name: 'Test Datapool',
  description: 'A description',
  contactPerson: { id: 'u1', name: 'Anna Müller' },
  createdAt: '2024-01-01T00:00:00Z',
  modifiedAt: '2024-01-01T00:00:00Z',
}

const TestWrapper = ({
  datapool = mockDatapool,
  isReadOnly = false,
}: {
  datapool?: Datapool
  isReadOnly?: boolean
}) => {
  const form = useForm<DatapoolFormData>({
    resolver: zodResolver(DatapoolFormSchema),
    defaultValues: {
      id: datapool.id,
      name: datapool.name,
      description: datapool.description,
      contactPersonId: datapool.contactPerson?.id ?? null,
    },
  })

  return (
    <Form {...form}>
      <BasicInfoTab form={form} isReadOnly={isReadOnly} datapool={datapool} />
    </Form>
  )
}

const setup = (props: { datapool?: Datapool; isReadOnly?: boolean } = {}) => render(<TestWrapper {...props} />)

describe('BasicInfoTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPermissions([PERMISSION_NAMES.USER_READ])
    vi.mocked(useGetUsers).mockReturnValue({
      data: undefined,
      isLoading: false,
    } as ReturnType<typeof useGetUsers>)
  })

  describe('Rendering', () => {
    it('renders the tab container', () => {
      setup()
      expect(screen.getByTestId('basicInfoTab')).toBeInTheDocument()
    })

    it('renders name and description fields', () => {
      setup()
      expect(screen.getByTestId('nameTextField')).toBeInTheDocument()
      expect(screen.getByTestId('descriptionTextArea')).toBeInTheDocument()
    })

    it('renders the contact autocomplete field', () => {
      setup()
      expect(screen.getByRole('combobox')).toBeInTheDocument()
    })

    it('shows the initial datapool name', () => {
      setup()
      expect(screen.getByTestId('nameTextField')).toHaveValue('Test Datapool')
    })

    it('shows the initial description', () => {
      setup()
      expect(screen.getByTestId('descriptionTextArea')).toHaveValue('A description')
    })

    it('shows the initial contact name in the contact field', () => {
      setup()
      expect(screen.getByRole('combobox')).toHaveValue('Anna Müller')
    })

    it('shows an empty contact field when no contactPerson is set', () => {
      setup({ datapool: { ...mockDatapool, contactPerson: null } })
      expect(screen.getByRole('combobox')).toHaveValue('')
    })
  })

  describe('Read-Only Mode', () => {
    it('disables the name field', () => {
      setup({ isReadOnly: true })
      expect(screen.getByTestId('nameTextField')).toBeDisabled()
    })

    it('disables the description field', () => {
      setup({ isReadOnly: true })
      expect(screen.getByTestId('descriptionTextArea')).toBeDisabled()
    })

    it('disables the contact field', () => {
      setup({ isReadOnly: true })
      expect(screen.getByRole('combobox')).toBeDisabled()
    })
  })

  describe('Edit Mode', () => {
    it('enables the name field', () => {
      setup({ isReadOnly: false })
      expect(screen.getByTestId('nameTextField')).not.toBeDisabled()
    })

    it('enables the description field', () => {
      setup({ isReadOnly: false })
      expect(screen.getByTestId('descriptionTextArea')).not.toBeDisabled()
    })

    it('updates the name field value on change', () => {
      setup({ isReadOnly: false })
      fireEvent.change(screen.getByTestId('nameTextField'), { target: { value: 'New Name' } })
      expect(screen.getByTestId('nameTextField')).toHaveValue('New Name')
    })

    it('updates the description field value on change', () => {
      setup({ isReadOnly: false })
      fireEvent.change(screen.getByTestId('descriptionTextArea'), { target: { value: 'New Description' } })
      expect(screen.getByTestId('descriptionTextArea')).toHaveValue('New Description')
    })
  })

  describe('Contact field permission gating', () => {
    it('disables the contact field when USER_READ permission is missing', () => {
      mockPermissions([])
      setup({ isReadOnly: false })
      expect(screen.getByRole('combobox')).toBeDisabled()
    })

    it('enables the contact field when USER_READ permission is present and isReadOnly=false', () => {
      mockPermissions([PERMISSION_NAMES.USER_READ])
      setup({ isReadOnly: false })
      expect(screen.getByRole('combobox')).not.toBeDisabled()
    })
  })
})

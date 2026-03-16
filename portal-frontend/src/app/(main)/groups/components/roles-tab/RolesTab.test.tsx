import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { useForm, UseFormReturn } from 'react-hook-form'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { Form } from '@/components/ui/form'
import type { Assignment } from '@/types/assignments'
import type { AssignmentFormData, Group, GroupBaseFormData } from '@/types/groups'
import type { Role } from '@/types/roles'

import { RolesTab } from './RolesTab'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
  useSearchParams: () => new URLSearchParams('page=1'),
  usePathname: vi.fn(),
}))

vi.mock('sonner', () => ({
  toast: { error: vi.fn() },
}))

vi.mock('@/hooks/use-query-params', () => ({
  useQueryParams: () => ({
    getApiRequestParams: vi.fn(() => new URLSearchParams()),
  }),
}))

vi.mock('@/app/services/api/roles/clientRequests', () => ({
  useGetRoles: () => ({
    data: { data: [], totalElements: 0 },
    isFetching: false,
  }),
}))

const systemAssignment: Assignment = {
  id: 'a1',
  createdAt: '',
  modifiedAt: '',
  group: { id: 'g1', name: 'Test Group' },
  role: { id: 'r1', name: 'Admin Role', roleType: 'SYSTEM', description: 'System admin', readonly: true },
  scopeType: null,
  scope: null,
}

const dataAssignment: Assignment = {
  id: 'a2',
  createdAt: '',
  modifiedAt: '',
  group: { id: 'g1', name: 'Test Group' },
  role: { id: 'r2', name: 'Data Editor', roleType: 'DATA', description: 'Data role', readonly: false },
  scopeType: 'TENANT',
  scope: null,
}

const scopedAssignment: Assignment = {
  id: 'a3',
  createdAt: '',
  modifiedAt: '',
  group: { id: 'g1', name: 'Test Group' },
  role: { id: 'r3', name: 'Dataset Viewer', roleType: 'DATA', description: 'Scoped data', readonly: true },
  scopeType: 'DATASET',
  scope: { id: 'ds1', name: 'My Dataset' },
}

const mockGroup: Group = {
  id: 'g1',
  name: 'Test Group',
  description: 'A test group',
  assignments: [systemAssignment, dataAssignment, scopedAssignment],
  members: [],
  contactUser: null,
  createdAt: '',
  modifiedAt: '',
}

const assignmentsFromGroup = (group: Group): AssignmentFormData[] =>
  (group.assignments ?? []).map(a => ({
    groupId: a.group.id,
    roleId: a.role.id,
    scopeType: a.scopeType,
    scopeId: a.scope?.id ?? null,
  }))

const queryClient = new QueryClient()

interface TestWrapperProps {
  groupData?: Group
  isReadOnly?: boolean
  pendingRoles?: Role[]
  setPendingRoles?: React.Dispatch<React.SetStateAction<Role[]>>
  extraAssignments?: AssignmentFormData[]
  formRef?: React.RefObject<UseFormReturn<GroupBaseFormData> | null>
}

const TestWrapper = ({
  groupData = mockGroup,
  isReadOnly = false,
  pendingRoles = [],
  setPendingRoles = vi.fn(),
  extraAssignments = [],
  formRef,
}: TestWrapperProps) => {
  const form = useForm<GroupBaseFormData>({
    defaultValues: {
      id: groupData.id,
      name: groupData.name,
      description: groupData.description || '',
      contactUserId: groupData.contactUser?.id || '',
      members: groupData.members?.map(m => m.id) || [],
      assignments: [...assignmentsFromGroup(groupData), ...extraAssignments],
    },
  })

  if (formRef) formRef.current = form

  return (
    <QueryClientProvider client={queryClient}>
      <Form {...form}>
        <RolesTab
          form={form}
          groupData={groupData}
          isReadOnly={isReadOnly}
          pendingRoles={pendingRoles}
          setPendingRoles={setPendingRoles}
        />
      </Form>
    </QueryClientProvider>
  )
}

describe('RolesTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const renderTab = (props?: TestWrapperProps) => {
    return render(<TestWrapper {...props} />)
  }

  it('renders all four scope tabs', () => {
    renderTab()

    expect(screen.getByText('roles.scopeTabs.platform')).toBeInTheDocument()
    expect(screen.getByText('roles.scopeTabs.datasets')).toBeInTheDocument()
    expect(screen.getByText('roles.scopeTabs.datasources')).toBeInTheDocument()
    expect(screen.getByText('roles.scopeTabs.datastructures')).toBeInTheDocument()
  })

  it('shows platform assignments (null + TENANT scopeType) on platform tab', () => {
    renderTab()

    expect(screen.getByRole('cell', { name: 'Admin Role' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'Data Editor' })).toBeInTheDocument()
    expect(screen.queryByRole('cell', { name: 'Dataset Viewer' })).not.toBeInTheDocument()
  })

  it('shows only DATASET-scoped assignments on datasets tab', async () => {
    renderTab()

    const datasetsTab = screen.getByText('roles.scopeTabs.datasets')
    fireEvent.click(datasetsTab)

    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'Dataset Viewer' })).toBeInTheDocument()
      expect(screen.queryByRole('cell', { name: 'Admin Role' })).not.toBeInTheDocument()
      expect(screen.queryByRole('cell', { name: 'Data Editor' })).not.toBeInTheDocument()
    })
  })

  it('shows info banner on scoped tabs but not on platform tab', async () => {
    renderTab()

    expect(screen.queryByText('roles.scopedInfoBanner.DATASET')).not.toBeInTheDocument()

    const datasetsTab = screen.getByText('roles.scopeTabs.datasets')
    fireEvent.click(datasetsTab)

    await waitFor(() => {
      expect(screen.getByText('roles.scopedInfoBanner.DATASET')).toBeInTheDocument()
    })
  })

  it('filters assignments by search string', async () => {
    renderTab()

    const searchInput = screen.getByRole('searchbox')
    fireEvent.change(searchInput, { target: { value: 'Admin' } })

    await waitFor(() => {
      expect(screen.getByRole('cell', { name: 'Admin Role' })).toBeInTheDocument()
      expect(screen.queryByRole('cell', { name: 'Data Editor' })).not.toBeInTheDocument()
    })
  })

  it('shows empty state when no assignments match current tab', () => {
    const emptyGroup: Group = { ...mockGroup, assignments: [] }
    renderTab({ groupData: emptyGroup })

    expect(screen.getByText('roles.noRoles')).toBeInTheDocument()
  })

  it('shows add role dropdown on platform tab when not read-only', () => {
    renderTab({ isReadOnly: false })

    expect(screen.getByText('roles.addRole')).toBeInTheDocument()
  })

  it('hides add role dropdown on scoped tabs', async () => {
    renderTab({ isReadOnly: false })

    const datasetsTab = screen.getByText('roles.scopeTabs.datasets')
    fireEvent.click(datasetsTab)

    await waitFor(() => {
      expect(screen.queryByText('roles.addRole')).not.toBeInTheDocument()
    })
  })

  it('hides add role dropdown when isReadOnly', () => {
    renderTab({ isReadOnly: true })

    expect(screen.queryByText('roles.addRole')).not.toBeInTheDocument()
  })

  it('shows action menu for platform assignments when not read-only', () => {
    renderTab()

    const menuButtons = screen.getAllByRole('button').filter(btn => btn.dataset.state !== undefined)
    expect(menuButtons.length).toBeGreaterThan(0)
  })

  describe('pending roles display', () => {
    const pendingRole: Role = {
      id: 'r-new',
      name: 'New Pending Role',
      description: 'A newly assigned role',
      type: 'SYSTEM',
      tenant: 't1',
      permissions: [],
      users: [],
      createdAt: '',
      lastUpdated: null,
      updatedBy: null,
      groups: [],
      roleOrigin: 'custom',
    }

    const pendingFormAssignment: AssignmentFormData = {
      groupId: 'g1',
      roleId: 'r-new',
      scopeType: null,
      scopeId: null,
    }

    it('displays pending role with name and description in the table', () => {
      renderTab({
        pendingRoles: [pendingRole],
        extraAssignments: [pendingFormAssignment],
      })

      expect(screen.getByRole('cell', { name: 'New Pending Role' })).toBeInTheDocument()
      expect(screen.getByRole('cell', { name: 'A newly assigned role' })).toBeInTheDocument()
    })

    it('displays pending DATA role on platform tab when scopeType is TENANT', () => {
      const dataRole: Role = {
        ...pendingRole,
        id: 'r-data-new',
        name: 'New Data Role',
        type: 'DATA',
      }

      renderTab({
        pendingRoles: [dataRole],
        extraAssignments: [{ groupId: 'g1', roleId: 'r-data-new', scopeType: 'TENANT', scopeId: null }],
      })

      expect(screen.getByRole('cell', { name: 'New Data Role' })).toBeInTheDocument()
    })

    it('does not display pending platform role on scoped tabs', async () => {
      renderTab({
        pendingRoles: [pendingRole],
        extraAssignments: [pendingFormAssignment],
      })

      const datasetsTab = screen.getByText('roles.scopeTabs.datasets')
      fireEvent.click(datasetsTab)

      await waitFor(() => {
        expect(screen.queryByRole('cell', { name: 'New Pending Role' })).not.toBeInTheDocument()
      })
    })

    it('shows pending role alongside existing assignments', () => {
      renderTab({
        pendingRoles: [pendingRole],
        extraAssignments: [pendingFormAssignment],
      })

      // Existing assignments
      expect(screen.getByRole('cell', { name: 'Admin Role' })).toBeInTheDocument()
      expect(screen.getByRole('cell', { name: 'Data Editor' })).toBeInTheDocument()
      // Pending assignment
      expect(screen.getByRole('cell', { name: 'New Pending Role' })).toBeInTheDocument()
    })
  })

  describe('form state integration', () => {
    it('removes assignment from form when confirmed via warning modal', async () => {
      const formRef = { current: null } as React.RefObject<UseFormReturn<GroupBaseFormData> | null>

      renderTab({ formRef })

      // There should be 2 platform assignments (Admin Role + Data Editor)
      expect(screen.getByRole('cell', { name: 'Admin Role' })).toBeInTheDocument()

      // Open the action menu for the first row (Radix needs pointerDown)
      const menuButtons = screen.getAllByLabelText('Open menu')
      fireEvent.pointerDown(menuButtons[0])

      await waitFor(() => {
        expect(screen.getByText('actions.removeItem')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('actions.removeItem'))

      // Confirm the warning modal
      await waitFor(() => {
        expect(screen.getByText('roles.removeRoleDescription')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('actions.remove'))

      // Form should now have fewer assignments
      await waitFor(() => {
        const assignments = formRef.current?.getValues('assignments') ?? []
        expect(assignments.length).toBeLessThan(3)
      })
    })

    it('updates setPendingRoles when removing a pending role', async () => {
      const setPendingRoles = vi.fn()

      const pendingRole: Role = {
        id: 'r-pending-remove',
        name: 'Remove Me',
        description: 'Will be removed',
        type: 'SYSTEM',
        tenant: 't1',
        permissions: [],
        users: [],
        createdAt: '',
        lastUpdated: null,
        updatedBy: null,
        groups: [],
        roleOrigin: 'custom',
      }

      const emptyGroup: Group = {
        ...mockGroup,
        assignments: [],
      }

      renderTab({
        groupData: emptyGroup,
        pendingRoles: [pendingRole],
        setPendingRoles,
        extraAssignments: [{ groupId: 'g1', roleId: 'r-pending-remove', scopeType: null, scopeId: null }],
      })

      expect(screen.getByRole('cell', { name: 'Remove Me' })).toBeInTheDocument()

      // Open the action menu (Radix needs pointerDown)
      const menuButtons = screen.getAllByLabelText('Open menu')
      fireEvent.pointerDown(menuButtons[0])

      await waitFor(() => {
        expect(screen.getByText('actions.removeItem')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('actions.removeItem'))

      // Confirm the warning modal
      await waitFor(() => {
        expect(screen.getByText('roles.removeRoleDescription')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('actions.remove'))

      await waitFor(() => {
        expect(setPendingRoles).toHaveBeenCalled()
      })
    })
  })
})

import '@testing-library/jest-dom'

import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { Role } from '@/types/roles'

import { RoleTemplateSelect } from './RoleTemplateSelect'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => {
    if (key === 'roles.permissionsTab.roleTemplate.placeholder') return 'Select a role as template'
    return ''
  },
}))

describe('RoleTemplateSelect', () => {
  const setRoleTemplate = vi.fn()
  const allRoles: Role[] = [
    {
      id: '1',
      name: 'Admin',
      roleType: 'DATA',
      permissions: [],
      readonly: true,
      modifiedBy: null,
      modifiedAt: null,
      createdAt: '',
      groupCount: 0,
      userCount: 0,
    },
    {
      id: '2',
      name: 'User',
      roleType: 'DATA',
      permissions: [],
      readonly: true,
      modifiedBy: null,
      modifiedAt: null,
      createdAt: '',
      groupCount: 0,
      userCount: 0,
    },
  ]

  it('renders the component with correct options', () => {
    render(<RoleTemplateSelect setRoleTemplate={setRoleTemplate} templateRoles={allRoles} />)

    const selectTrigger = screen.getByRole('combobox')
    fireEvent.click(selectTrigger)

    expect(screen.getByText('Admin')).toBeInTheDocument()
    expect(screen.getByText('User')).toBeInTheDocument()
  })

  it('calls setRoleTemplate with the correct role ID when a role is selected', () => {
    render(<RoleTemplateSelect setRoleTemplate={setRoleTemplate} templateRoles={allRoles} />)

    const selectTrigger = screen.getByRole('combobox')
    fireEvent.click(selectTrigger)

    const adminOption = screen.getByText('Admin')
    fireEvent.click(adminOption)

    expect(setRoleTemplate).toHaveBeenCalledWith('1')
  })
})

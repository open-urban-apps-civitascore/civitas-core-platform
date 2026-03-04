import { zodResolver } from '@hookform/resolvers/zod'
import { act, fireEvent, render, renderHook, screen } from '@testing-library/react'
import { type JSX, ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { vi } from 'vitest'

import { roleSchema } from '@/types/roles'

import { BaseInfoTab } from './BaseInfoTab'

const mockPush = vi.fn()

vi.mock('next/navigation', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}))

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string) => key,
}))

vi.mock('@/hooks/use-mobile', () => ({
  useIsMobile: () => false,
}))

const wrapper = ({ children }: { children: ReactNode }): JSX.Element => <div>{children}</div>

const { result } = renderHook(
  () =>
    useForm({
      resolver: zodResolver(roleSchema),
      defaultValues: {
        name: '',
        description: '',
        readonly: false,
      },
    }),
  { wrapper },
)

describe('BaseInfoTab Component Custom Roles', () => {
  const mockOnSubmit = vi.fn()
  const mockDeleteRole = vi.fn()

  const defaultProps = {
    form: result.current,
    onSubmit: mockOnSubmit,
    isLoading: false,
    isDefaultRole: false,
    isEditMode: true,
    deleteRole: mockDeleteRole,
    roleType: 'custom',
  }

  it('renders the component initially in read only mode', () => {
    render(<BaseInfoTab {...defaultProps} />)

    expect(screen.getByText('heading')).toBeDefined()

    const nameInput = screen.getByRole('textbox', { name: 'form.inputs.name' })
    const descriptionInput = screen.getByRole('textbox', { name: 'form.inputs.description' })
    const roleOriginInput = screen.getByRole('textbox', { name: 'form.inputs.roleOrigin' })
    const editButton = screen.getByRole('button', { name: 'actions.edit' })

    expect(nameInput).toBeDefined()
    expect(descriptionInput).toBeDefined()
    expect(roleOriginInput).toBeDefined()

    expect(editButton).toBeDefined()

    expect(nameInput).toBeDisabled()
    expect(descriptionInput).toBeDisabled()
    expect(roleOriginInput).toBeDisabled()

    const cancelButton = screen.queryByRole('button', { name: 'actions.cancel' })
    expect(cancelButton).toBeNull()

    const saveButton = screen.queryByRole('button', { name: 'actions.submit' })
    expect(saveButton).toBeNull()
  })

  it('renders edit mode after clicking edit button', () => {
    render(<BaseInfoTab {...defaultProps} />)

    const editButton = screen.getByRole('button', { name: 'actions.edit' })
    act(() => {
      fireEvent.click(editButton)
    })

    const cancelButton = screen.getByRole('button', { name: 'actions.cancel' })
    expect(cancelButton).toBeDefined()

    const saveButton = screen.getByRole('button', { name: 'actions.submit' })
    expect(saveButton).toBeDefined()

    act(() => {
      fireEvent.change(screen.getByRole('textbox', { name: 'form.inputs.name *' }), {
        target: { value: 'New Role Name' },
      })
      fireEvent.change(screen.getByRole('textbox', { name: 'form.inputs.description' }), {
        target: { value: 'New Role Description' },
      })
    })

    expect((screen.getByRole('textbox', { name: 'form.inputs.name *' }) as HTMLInputElement).value).toBe(
      'New Role Name',
    )
    expect((screen.getByRole('textbox', { name: 'form.inputs.description' }) as HTMLInputElement).value).toBe(
      'New Role Description',
    )

    expect(screen.getByRole('textbox', { name: 'form.inputs.roleOrigin' })).toBeDefined()
    expect(screen.getByRole('textbox', { name: 'form.inputs.roleOrigin' })).toBeDisabled()
  })

  it('calls deleteRole when delete button is clicked', () => {
    render(<BaseInfoTab {...defaultProps} />)

    const deleteButton = screen.getByRole('button', { name: 'securityArea.deleteButton' })
    fireEvent.click(deleteButton)

    expect(mockDeleteRole).toHaveBeenCalled()
  })

  it('calls router push to roles table when clicking cancel', () => {
    render(<BaseInfoTab {...defaultProps} />)

    const editButton = screen.getByRole('button', { name: 'actions.edit' })
    act(() => {
      fireEvent.click(editButton)
    })

    const cancelButton = screen.getByRole('button', { name: 'actions.cancel' })
    expect(cancelButton).toBeDefined()
    act(() => {
      fireEvent.click(cancelButton)
    })

    expect(mockPush).toHaveBeenCalledWith('/roles?_tab=custom')
  })
})

describe('BaseInfoTab Component Default Roles', () => {
  const mockOnSubmit = vi.fn()
  const mockDeleteRole = vi.fn()

  const defaultProps = {
    form: result.current,
    onSubmit: mockOnSubmit,
    isLoading: false,
    isDefaultRole: true,
    isEditMode: true,
    deleteRole: mockDeleteRole,
    roleType: 'custom',
  }

  it('renders the component in read only mode', () => {
    render(<BaseInfoTab {...defaultProps} />)

    expect(screen.getByText('heading')).toBeDefined()

    const nameInput = screen.getByRole('textbox', { name: 'form.inputs.name' })
    const descriptionInput = screen.getByRole('textbox', { name: 'form.inputs.description' })
    const roleOriginInput = screen.getByRole('textbox', { name: 'form.inputs.roleOrigin' })
    const editButton = screen.queryByRole('button', { name: 'actions.edit' })

    expect(nameInput).toBeDefined()
    expect(descriptionInput).toBeDefined()
    expect(roleOriginInput).toBeDefined()

    expect(editButton).toBeNull()

    expect(nameInput).toBeDisabled()
    expect(descriptionInput).toBeDisabled()
    expect(roleOriginInput).toBeDisabled()

    const cancelButton = screen.queryByRole('button', { name: 'actions.cancel' })
    expect(cancelButton).toBeNull()

    const saveButton = screen.queryByRole('button', { name: 'actions.submit' })
    expect(saveButton).toBeNull()
  })
})

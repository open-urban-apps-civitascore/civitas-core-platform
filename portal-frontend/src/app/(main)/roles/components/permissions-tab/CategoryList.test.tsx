import '@testing-library/jest-dom'

import { fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { PermissionItem } from '@/types/permissions'

import { CategoryList } from './CategoryList'

vi.mock('next-intl', () => ({
  useTranslations: () => (key: string, params: { count: number }) => `${params.count} selected`,
}))

describe('CategoryList', () => {
  const permissionList: PermissionItem[] = [
    { value: 'read', name: 'Read', category: { id: '1', title: 'General' } },
    { value: 'write', name: 'Write', category: { id: '1', title: 'General' } },
  ]

  const checkedItems: PermissionItem[] = [{ value: 'read', name: 'Read', category: { id: '1', title: 'General' } }]

  const setCheckedItems = vi.fn()

  it('renders the component with correct permissions', () => {
    render(
      <CategoryList
        permissionList={permissionList}
        checkedItems={checkedItems}
        setCheckedItems={setCheckedItems}
        isDefaultRole={false}
      />,
    )

    expect(screen.getByText('General')).toBeInTheDocument()
    expect(screen.getByText('Read')).toBeInTheDocument()
    expect(screen.getByText('Write')).toBeInTheDocument()
    expect(screen.getByText('1 selected')).toBeInTheDocument()

    const checkboxes = screen.getAllByRole('checkbox')
    expect(checkboxes[0]).toBePartiallyChecked()
    expect(checkboxes[1]).toBeChecked()
    expect(checkboxes[2]).not.toBeChecked()
  })

  it('toggles the selection of a single item', () => {
    render(
      <CategoryList
        permissionList={permissionList}
        checkedItems={checkedItems}
        setCheckedItems={setCheckedItems}
        isDefaultRole={false}
      />,
    )

    const writeCheckbox = screen.getAllByRole('checkbox')[2]
    fireEvent.click(writeCheckbox)

    expect(setCheckedItems).toHaveBeenCalledWith([
      ...checkedItems,
      { value: 'write', name: 'Write', category: { id: '1', title: 'General' } },
    ])
  })

  it('toggles the selection of all items', () => {
    render(
      <CategoryList
        permissionList={permissionList}
        checkedItems={checkedItems}
        setCheckedItems={setCheckedItems}
        isDefaultRole={false}
      />,
    )

    const headerCheckbox = screen.getAllByRole('checkbox')[0]
    fireEvent.click(headerCheckbox)

    expect(setCheckedItems).toHaveBeenCalledWith(permissionList)
  })

  it('renders disabled checkboxes when isDefaultRole is true', () => {
    render(
      <CategoryList
        permissionList={permissionList}
        checkedItems={checkedItems}
        setCheckedItems={setCheckedItems}
        isDefaultRole={true}
      />,
    )

    const checkboxes = screen.getAllByRole('checkbox')
    checkboxes.forEach(checkbox => {
      expect(checkbox).toBeDisabled()
    })
  })
})

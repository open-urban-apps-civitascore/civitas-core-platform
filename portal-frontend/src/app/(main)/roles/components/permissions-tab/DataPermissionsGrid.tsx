'use client'

import { useTranslations } from 'next-intl'
import { type JSX } from 'react'

import { Checkbox } from '@/components/ui/checkbox'
import { PermissionItem } from '@/types/permissions'

// Maps row keys to permission name prefixes
const DATA_PERMISSION_ROWS = [
  { key: 'dataset', prefix: 'DATASET' },
  { key: 'datasetPayload', prefix: 'DATASET_PAYLOAD' },
  { key: 'datasource', prefix: 'DATASOURCE' },
  { key: 'datastructure', prefix: 'DATASTRUCTURE' },
] as const

const DATA_PERMISSION_COLUMNS = ['READ', 'CREATE', 'UPDATE', 'DELETE', 'RELEASE'] as const

interface DataPermissionsGridProps {
  permissions: PermissionItem[]
  checkedItems: PermissionItem[]
  setCheckedItems: (items: PermissionItem[]) => void
  isReadOnly: boolean
}

export const DataPermissionsGrid = (props: DataPermissionsGridProps): JSX.Element => {
  const { permissions, checkedItems, setCheckedItems, isReadOnly } = props
  const tRoles = useTranslations('roles.permissionsTab')

  // Find a permission by constructing the name from prefix + action
  const findPermission = (prefix: string, action: string): PermissionItem | undefined => {
    const permissionName = `${prefix}_${action}`
    return permissions.find(p => p.name === permissionName)
  }

  const isChecked = (permission: PermissionItem | undefined): boolean => {
    if (!permission) return false
    return checkedItems.some(item => item.value === permission.value)
  }

  const onToggle = (permission: PermissionItem | undefined): void => {
    if (!permission || isReadOnly) return
    if (isChecked(permission)) {
      setCheckedItems(checkedItems.filter(item => item.value !== permission.value))
    } else {
      setCheckedItems([...checkedItems, permission])
    }
  }

  return (
    <div className="border border-[#E5E5E5] rounded-sm overflow-hidden">
      {/* Header row */}
      <div className="grid grid-cols-6 bg-[#F5F5F5] border-b border-[#E5E5E5]">
        <div className="p-3 font-semibold" />
        {DATA_PERMISSION_COLUMNS.map(col => (
          <div key={col} className="p-3 text-center font-semibold text-sm">
            {tRoles(`dataPermissions.${col.toLowerCase()}`)}
          </div>
        ))}
      </div>

      {/* Data rows */}
      {DATA_PERMISSION_ROWS.map(row => (
        <div key={row.key} className="grid grid-cols-6 border-b border-[#E5E5E5] last:border-b-0 bg-white">
          <div className="p-3 font-medium text-sm flex items-center">
            {tRoles(`dataPermissions.${row.key}`)}
          </div>
          {DATA_PERMISSION_COLUMNS.map(col => {
            const permission = findPermission(row.prefix, col)
            // If no permission exists for this combination (e.g., DATASET_PAYLOAD_RELEASE), show disabled
            return (
              <div key={col} className="p-3 flex items-center justify-center">
                {permission ? (
                  <Checkbox
                    checked={isChecked(permission)}
                    onClick={() => onToggle(permission)}
                    disabled={isReadOnly}
                    className="w-[16px] h-[16px]"
                  />
                ) : (
                  <Checkbox disabled checked={false} className="w-[16px] h-[16px] opacity-30" />
                )}
              </div>
            )
          })}
        </div>
      ))}
    </div>
  )
}

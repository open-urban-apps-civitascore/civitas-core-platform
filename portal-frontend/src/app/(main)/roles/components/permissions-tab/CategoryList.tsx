import { useTranslations } from 'next-intl'
import { useMemo } from 'react'
import { type JSX } from 'react'

import { Checkbox } from '@/components/ui/checkbox'
import { PermissionItem } from '@/types/permissions'

type CategoryListProps = {
  permissionList: PermissionItem[]
  checkedItems: PermissionItem[]
  setCheckedItems: (items: PermissionItem[]) => void
  isDefaultRole: boolean
}

export const CategoryList = (props: CategoryListProps) => {
  const { permissionList, checkedItems, setCheckedItems, isDefaultRole } = props
  const tRoles = useTranslations('roles')

  const checkedItemsByCategory = useMemo(() => {
    return checkedItems.filter(item => item.category.id === permissionList[0].category.id)
  }, [checkedItems, permissionList])

  const areAllItemsSelected = useMemo(() => {
    return (
      checkedItemsByCategory.length === permissionList.length &&
      checkedItemsByCategory.every(item =>
        permissionList.find(permission => permission.category.id === item.category.id),
      )
    )
  }, [checkedItemsByCategory, permissionList])

  const onChangeCheckbox = (item: PermissionItem): void => {
    const isChecked = checkedItems.find(checkedItem => checkedItem.value === item.value)

    if (isChecked) {
      setCheckedItems(checkedItems.filter(checkedItem => checkedItem.value !== item.value))
    } else {
      setCheckedItems([...checkedItems, item])
    }
  }

  const onToggleAllItems = (): void => {
    if (areAllItemsSelected) {
      const newCheckedItems = checkedItems.filter(item => item.category.id !== permissionList[0].category.id)
      setCheckedItems(newCheckedItems)
    } else {
      const categoryItems = permissionList.filter(
        permission => !checkedItems.some(item => item.value === permission.value),
      )
      setCheckedItems([...checkedItems, ...categoryItems])
    }
  }

  const getHeaderCheckedState = (): boolean | 'indeterminate' => {
    if (checkedItemsByCategory.length === 0) {
      return false
    }
    if (areAllItemsSelected) {
      return true
    }

    return 'indeterminate'
  }

  const CategoryHeader = (): JSX.Element => {
    return (
      <div className="min-w-[85px] gap-[10px] self-stretch h-[56px] p-[8px_12px]  border-b-[#E5E5E5] border-b-[1px] bg-[#F5F5F5]">
        <div className="flex justify-between items-center">
          <div className="flex items-center">
            <div className="flex items-center justify-center w-[45px] h-[32px] p-[8px_12px]">
              <Checkbox
                className="w-[16px] h-[16px]"
                checked={getHeaderCheckedState()}
                onClick={() => {
                  onToggleAllItems()
                }}
                disabled={isDefaultRole}
              />
            </div>
            <h2 className="text-xl">{permissionList[0]?.category.title}</h2>
          </div>
          <span className="font-semibold text-xs text-primary">
            {tRoles('permissionsTab.selectedPersmissions', { count: checkedItemsByCategory.length })}
          </span>
        </div>
      </div>
    )
  }

  const PermissionItem = ({ inputItem }: { inputItem: PermissionItem }): JSX.Element => {
    return (
      <div className="flex items-center min-w-[85px] gap-[10px] self-stretch h-[48px] p-[8px_12px]  border-b-[#E5E5E5] border-b-[1px] bg-white">
        <div className="flex items-center justify-center w-[45px] h-[32px] p-[8px_12px]">
          <Checkbox
            className="w-[16px] h-[16px]"
            onClick={() => onChangeCheckbox(inputItem)}
            checked={checkedItems.some(item => item.value === inputItem.value)}
            disabled={isDefaultRole}
          />
        </div>
        <span>{inputItem.name}</span>
      </div>
    )
  }

  return (
    <div className="mb-6 border border-[#E5E5E5] rounded-sm overflow-hidden">
      <CategoryHeader />

      {permissionList.map(permission => (
        <PermissionItem key={permission.value} inputItem={permission} />
      ))}
    </div>
  )
}

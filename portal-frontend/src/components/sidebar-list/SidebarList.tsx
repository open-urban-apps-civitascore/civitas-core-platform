import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export type SidebarListItem = {
  label: string
  value: string
  displayTitle: string
}
interface SidebarListProps {
  items: SidebarListItem[]
  selectedItemIndex: number | null
  isReadOnly: boolean
  addButtonLabel: string
  addButtonTestId?: string
  onSelectItem: (index: number) => void
  onAddItem: () => void
}

export const SidebarList = ({
  items,
  selectedItemIndex,
  isReadOnly,
  addButtonLabel,
  addButtonTestId,
  onSelectItem,
  onAddItem,
}: SidebarListProps) => {
  return (
    <div className="flex w-52 shrink-0 flex-col gap-2 mt-6">
      <ul className="flex flex-col gap-1">
        {items.map((item, index) => (
          <li key={item.value}>
            <button
              type="button"
              onClick={() => onSelectItem(index)}
              className={cn(
                'w-full cursor-pointer rounded-lg px-3 py-2 text-left text-sm hover:bg-accent',
                selectedItemIndex === index && 'bg-muted font-medium',
              )}
            >
              {item.displayTitle}
            </button>
          </li>
        ))}
      </ul>
      {!isReadOnly && (
        <Button type="button" variant="outline" onClick={onAddItem} className="mt-2" data-testid={addButtonTestId}>
          + {addButtonLabel}
        </Button>
      )}
    </div>
  )
}

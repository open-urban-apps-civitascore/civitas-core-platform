import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

export type SidebarListItem = {
  label: string
  value: string
  displayTitle: string
  hasError?: boolean
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
      <ul className="flex flex-col gap-2">
        {items.map((item, index) => (
          <li key={item.value}>
            <Tooltip>
              <TooltipTrigger asChild>
                <button
                  type="button"
                  onClick={() => onSelectItem(index)}
                  className={cn(
                    'w-full cursor-pointer rounded-lg px-3 py-2 text-left text-sm hover:bg-accent line-clamp-2 max-h-14 overflow-hidden',
                    selectedItemIndex === index && 'bg-accent font-medium',
                    item.hasError && 'text-destructive',
                  )}
                >
                  {item.displayTitle}
                </button>
              </TooltipTrigger>
              <TooltipContent variant="secondary">{item.displayTitle}</TooltipContent>
            </Tooltip>
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

import { useRef } from 'react'

import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { useClampedText } from '@/hooks/use-clamped-text'
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

interface SidebarListItemButtonProps {
  item: SidebarListItem
  isSelected: boolean
  onSelect: () => void
}

// Guaranteed to fit within 2 lines regardless of whether the title contains whitespace to wrap
// on: CSS line-clamp alone can't be trusted here, since -webkit-line-clamp miscounts lines when
// combined with forced (whitespace-free) breaks, and without forced breaks unbreakable text simply
// overflows instead of wrapping. useClampedText measures real layout and truncates the string itself.
const SidebarListItemButton = ({ item, isSelected, onSelect }: SidebarListItemButtonProps) => {
  const labelRef = useRef<HTMLSpanElement>(null)
  const displayText = useClampedText(item.displayTitle, labelRef)

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          onClick={onSelect}
          className={cn(
            'w-full cursor-pointer rounded-lg px-3 py-2 text-left text-sm hover:bg-accent',
            isSelected && 'bg-accent font-medium',
            item.hasError && 'text-destructive',
          )}
        >
          <span ref={labelRef} className="block max-h-10 overflow-hidden break-words">
            {displayText}
          </span>
        </button>
      </TooltipTrigger>
      <TooltipContent variant="secondary">{item.displayTitle}</TooltipContent>
    </Tooltip>
  )
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
            <SidebarListItemButton
              item={item}
              isSelected={selectedItemIndex === index}
              onSelect={() => onSelectItem(index)}
            />
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

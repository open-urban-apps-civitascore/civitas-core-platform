import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { StyleFormData } from '@/types/namedApis'

interface StyleSidebarProps {
  existingStyles: StyleFormData[]
  selectedStyleIndex: number | null
  isReadOnly: boolean
  onSelectStyle: (index: number) => void
  onAddStyle: () => void
}

export const StyleSidebar = ({
  existingStyles,
  selectedStyleIndex,
  isReadOnly,
  onSelectStyle,
  onAddStyle,
}: StyleSidebarProps) => {
  const t = useTranslations('datasets.overview.completion.apis.config.styles')

  return (
    <div className="flex w-52 shrink-0 flex-col gap-2 mt-6">
      <ul className="flex flex-col gap-1">
        {existingStyles.map((style, index) => {
          const isNew = style.id?.startsWith('new-') ?? false
          const fallbackKey = isNew ? 'newStyle' : 'untitledStyle'
          const untitledIndex = !style.name ? existingStyles.slice(0, index + 1).filter(s => !s.name).length : 0
          const displayTitle = style.name || t(fallbackKey, { index: untitledIndex })
          return (
            <li key={style.id}>
              <button
                type="button"
                onClick={() => onSelectStyle(index)}
                className={cn(
                  'w-full cursor-pointer rounded-lg px-3 py-2 text-left text-sm hover:bg-accent',
                  selectedStyleIndex === index && 'bg-muted font-medium',
                )}
              >
                {displayTitle}
              </button>
            </li>
          )
        })}
      </ul>
      {!isReadOnly && (
        <Button
          type="button"
          variant="outline"
          onClick={onAddStyle}
          className="mt-2"
          data-testid="sidebarAddStyleButton"
        >
          + {t('addStyle')}
        </Button>
      )}
    </div>
  )
}

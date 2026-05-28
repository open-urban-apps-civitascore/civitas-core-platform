import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { LayerFormData } from '@/types/namedApis'

interface LayerSidebarProps {
  existingLayers: LayerFormData[]
  selectedLayerIndex: number | null
  isReadOnly: boolean
  onSelectLayer: (index: number) => void
  onAddLayer: () => void
}

export const LayerSidebar = ({
  existingLayers,
  selectedLayerIndex,
  isReadOnly,
  onSelectLayer,
  onAddLayer,
}: LayerSidebarProps) => {
  const t = useTranslations('datasets.overview.completion.apis.config.layer')

  return (
    <div className="flex w-52 shrink-0 flex-col gap-2 mt-6">
      <ul className="flex flex-col gap-1">
        {existingLayers.map((layer, index) => {
          const isNew = layer.id?.startsWith('new-') ?? false
          const fallbackKey = isNew ? 'newLayer' : 'untitledLayer'
          const untitledIndex = !layer.title ? existingLayers.slice(0, index + 1).filter(l => !l.title).length : 0
          const displayTitle = layer.title || t(fallbackKey, { index: untitledIndex })
          return (
            <li key={layer.id}>
              <button
                type="button"
                onClick={() => onSelectLayer(index)}
                className={cn(
                  'w-full cursor-pointer rounded-lg px-3 py-2 text-left text-sm hover:bg-accent',
                  selectedLayerIndex === index && 'bg-muted font-medium',
                )}
              >
                {displayTitle}
              </button>
            </li>
          )
        })}
      </ul>
      {!isReadOnly && (
        <Button type="button" variant="outline" onClick={onAddLayer} className="mt-2">
          + {t('addLayer')}
        </Button>
      )}
    </div>
  )
}

import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { LayerFormData } from '@/types/namedApis'

interface LayerSidebarProps {
  existingLayers: LayerFormData[]
  selectedLayerId: string | null
  onSelectLayer: (layerId: string) => void
  onAddLayer: () => void
}

export const LayerSidebar = ({ existingLayers, selectedLayerId, onSelectLayer, onAddLayer }: LayerSidebarProps) => {
  const t = useTranslations('datasets.overview.completion.apis.config.layer')

  return (
    <div className="flex w-52 shrink-0 flex-col gap-2 mt-6">
      <ul className="flex flex-col gap-1">
        {existingLayers.map(layer => (
          <li key={layer.id}>
            <button
              type="button"
              onClick={() => onSelectLayer(layer.id)}
              className={cn(
                'w-full cursor-pointer rounded-lg px-3 py-2 text-left text-sm hover:bg-accent',
                selectedLayerId === layer.id && 'bg-muted font-medium',
              )}
            >
              {layer.title}
            </button>
          </li>
        ))}
      </ul>
      <Button type="button" variant="outline" onClick={onAddLayer} className="mt-2">
        + {t('addLayer')}
      </Button>
    </div>
  )
}

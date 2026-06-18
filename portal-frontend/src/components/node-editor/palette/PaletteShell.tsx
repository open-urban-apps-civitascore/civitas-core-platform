'use client'

import type { NodeRegistry } from '../types'
import { PaletteCategory } from './PaletteCategory'
import { PaletteItem } from './PaletteItem'

interface PaletteShellProps {
  registry: NodeRegistry
  title?: string
  width?: number
}

/** Lists registry entries grouped by category. Domain-agnostic. */
export const PaletteShell = ({ registry, title = 'Transforms', width = 240 }: PaletteShellProps) => (
  <div className="flex h-full flex-col" style={{ width }}>
    <div className="border-b border-border px-3 py-2">
      <h3 className="text-sm font-medium text-foreground">{title}</h3>
    </div>
    <div className="flex-1 space-y-4 overflow-y-auto p-3">
      {registry.categories.map(category => (
        <PaletteCategory key={category} label={category}>
          {registry.list
            .filter(def => def.category === category)
            .map(def => (
              <PaletteItem
                key={def.type}
                type={def.type}
                label={def.label}
                description={def.description}
                icon={def.icon}
              />
            ))}
        </PaletteCategory>
      ))}
    </div>
  </div>
)

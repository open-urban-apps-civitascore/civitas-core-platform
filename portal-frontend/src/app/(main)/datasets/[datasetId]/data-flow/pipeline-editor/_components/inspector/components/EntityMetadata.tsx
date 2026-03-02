'use client'

/**
 * EntityMetadata Component
 *
 * Displays read-only metadata for a selected entity.
 *
 */

// ============================================================================
// Props
// ============================================================================

interface MetadataItem {
  label: string
  value: string | string[] | undefined
}

interface EntityMetadataProps {
  items: MetadataItem[]
  title?: string
}

// ============================================================================
// Component
// ============================================================================

export const EntityMetadata: React.FC<EntityMetadataProps> = ({ items, title = 'Details' }) => {
  const filteredItems = items.filter(item => item.value !== undefined && item.value !== '')

  if (filteredItems.length === 0) {
    return null
  }

  return (
    <div className="space-y-2">
      <h4 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{title}</h4>
      <div className="rounded-md border border-border bg-muted/30 p-3">
        <dl className="space-y-2">
          {filteredItems.map(item => (
            <div key={item.label} className="flex flex-col gap-0.5">
              <dt className="text-xs text-muted-foreground">{item.label}</dt>
              <dd className="text-sm text-foreground">
                {Array.isArray(item.value) ? (
                  item.value.length > 0 ? (
                    <div className="flex flex-wrap gap-1">
                      {item.value.map(tag => (
                        <span key={`${item.label}-${tag}`} className="rounded-sm bg-muted px-1.5 py-0.5 text-xs">
                          {tag}
                        </span>
                      ))}
                    </div>
                  ) : (
                    <span className="text-muted-foreground">—</span>
                  )
                ) : (
                  item.value || <span className="text-muted-foreground">—</span>
                )}
              </dd>
            </div>
          ))}
        </dl>
      </div>
    </div>
  )
}

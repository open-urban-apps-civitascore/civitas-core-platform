'use client'

/**
 * EntitySelector Component
 *
 * Reusable dropdown component for selecting entities (DataSource, API, FROST).
 *
 */

import { X } from 'lucide-react'

import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import type { SelectableEntity } from '../../../_services/entityService'

// ============================================================================
// Props
// ============================================================================

interface EntitySelectorProps {
  label: string
  placeholder?: string
  entities: SelectableEntity[]
  selectedId?: string | number
  isLoading: boolean
  isError: boolean
  onChange: (entity: SelectableEntity | undefined) => void
  isDisabled?: boolean
}

// ============================================================================
// Component
// ============================================================================

export const EntitySelector: React.FC<EntitySelectorProps> = ({
  label,
  placeholder = 'Select an entity...',
  entities,
  selectedId,
  isLoading,
  isError,
  onChange,
  isDisabled = false,
}) => {
  const handleValueChange = (value: string) => {
    if (value === '__clear__') {
      onChange(undefined)
      return
    }
    const entity = entities.find(e => String(e.id) === value)
    if (entity) {
      onChange(entity)
    }
  }

  const selectedValue = selectedId !== undefined ? String(selectedId) : undefined

  return (
    <div className="space-y-2">
      <label className="text-sm font-medium text-foreground">{label}</label>

      {isLoading ? (
        <div className="flex h-9 items-center rounded-md border border-border bg-muted px-3">
          <span className="text-sm text-muted-foreground">Loading...</span>
        </div>
      ) : isError ? (
        <div className="flex h-9 items-center rounded-md border border-destructive bg-destructive/10 px-3">
          <span className="text-sm text-destructive">Error loading entities</span>
        </div>
      ) : (
        <div className="relative">
          <Select value={selectedValue} onValueChange={handleValueChange} disabled={isDisabled}>
            <SelectTrigger className="w-full">
              <SelectValue placeholder={placeholder} />
            </SelectTrigger>
            <SelectContent>
              {selectedValue && (
                <SelectItem value="__clear__" className="text-muted-foreground">
                  <div className="flex items-center gap-2">
                    <X className="h-3 w-3" />
                    Clear selection
                  </div>
                </SelectItem>
              )}
              {entities.map(entity => (
                <SelectItem key={String(entity.id)} value={String(entity.id)}>
                  {entity.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      )}
    </div>
  )
}

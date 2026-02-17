'use client'

import { Check, ChevronDown } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useState } from 'react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

// TODO - Fetch real tags
const AVAILABLE_TAGS = ['Tag 1', 'Tag 2', 'Tag 3']

interface TagsMultiSelectProps {
  selectedTags: string[]
  onTagsChange: (tags: string[]) => void
  isDisabled?: boolean
}

export const TagsMultiSelect = (props: TagsMultiSelectProps) => {
  const { selectedTags, onTagsChange, isDisabled = false } = props
  const t = useTranslations('datasources')
  const [open, setOpen] = useState(false)

  const handleTagToggle = (tag: string) => {
    if (selectedTags.includes(tag)) {
      onTagsChange(selectedTags.filter(t => t !== tag))
    } else {
      onTagsChange([...selectedTags, tag])
    }
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={isDisabled}
          className={cn(
            'w-full justify-between font-normal min-h-9 h-auto',
            isDisabled && 'opacity-100 text-muted-foreground border-hidden shadow-none',
          )}
          data-testid="tagsDropdownTrigger"
        >
          <div className="flex flex-wrap gap-1">
            {selectedTags.length > 0 ? (
              selectedTags.map(tag => (
                <Badge key={tag} variant="secondary" className="mr-1">
                  {tag}
                </Badge>
              ))
            ) : (
              <span className="text-muted-foreground">{t('form.tagsPlaceholder')}</span>
            )}
          </div>
          {!isDisabled && <ChevronDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[384px] p-0" align="start">
        <Command>
          <CommandInput placeholder={t('form.tagsSearch')} />
          <CommandList>
            <CommandEmpty>{t('form.noTagsFound')}</CommandEmpty>
            <CommandGroup>
              {AVAILABLE_TAGS.map(tag => (
                <CommandItem key={tag} value={tag} onSelect={() => handleTagToggle(tag)}>
                  <div
                    className={cn(
                      'mr-2 flex h-4 w-4 items-center justify-center rounded border border-primary',
                      selectedTags.includes(tag) ? 'bg-primary' : 'opacity-50',
                    )}
                  >
                    {selectedTags.includes(tag) && <Check className="h-3 w-3 text-white" />}
                  </div>
                  {tag}
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}

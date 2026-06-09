'use client'

import { useTranslations } from 'next-intl'

import type { ConfigField } from '@/components/node-editor/types'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

import type { MappingTransformDef } from '../transforms'

interface FieldControlProps {
  field: ConfigField
  value: string
  onChange: (value: string) => void
}

const FieldControl = ({ field, value, onChange }: FieldControlProps) => {
  if (field.control === 'select') {
    return (
      <Select value={value} onValueChange={onChange}>
        <SelectTrigger>
          <SelectValue placeholder={field.placeholder} />
        </SelectTrigger>
        <SelectContent>
          {field.options?.map(option => (
            <SelectItem key={option.label} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    )
  }

  return (
    <Input
      type={field.control === 'number' ? 'number' : 'text'}
      value={value}
      placeholder={field.placeholder}
      className="w-full"
      onChange={e => onChange(e.target.value)}
    />
  )
}

interface TransformInspectorProps {
  def?: MappingTransformDef
  config: Record<string, unknown>
  onChange: (key: string, value: string) => void
}

/** Inspector body rendered entirely from the selected node's registry entry. */
export const TransformInspector = ({ def, config, onChange }: TransformInspectorProps) => {
  const t = useTranslations('pipelineEditor.mappingEditor.inspector')

  if (!def) return null

  return (
    <div className="space-y-4 p-4">
      <div className="space-y-1">
        <h4 className="text-sm font-medium text-foreground">{def.label}</h4>
        {def.description && <p className="text-xs text-muted-foreground">{def.description}</p>}
      </div>

      {def.config.map(field => (
        <div key={field.key} className="space-y-1.5">
          <Label>{field.label}</Label>
          <FieldControl
            field={field}
            value={String(config[field.key] ?? field.default ?? '')}
            onChange={value => onChange(field.key, value)}
          />
        </div>
      ))}

      {def.config.length === 0 && <p className="text-xs text-muted-foreground">{t('noConfig')}</p>}
    </div>
  )
}

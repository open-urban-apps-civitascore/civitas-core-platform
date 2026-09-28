'use client'

/**
 * PortStructureTree Component
 *
 * The structure a sink port publishes, as a field tree: the names, the data types, and which fields
 * a record must carry. It is what the modeller needs before they build the Mapping, and it comes
 * from the platform, so it says the same thing the sink checks when it writes.
 *
 * The tree is built with the adapter the mapping editor uses on the same document, so both show one
 * structure the same way.
 */

import { useTranslations } from 'next-intl'
import { useMemo } from 'react'

import type { FieldNode } from '../../mapping-editor/_types'
import { ModelResolutionError, modelToSchemaTree } from '../../mapping-editor/schema/modelAdapter'

interface PortStructureTreeProps {
  /** The JSON Schema model the port publishes. */
  model: Record<string, unknown>
  /** The name of the port, used when the document carries no title. */
  name: string
}

export const PortStructureTree: React.FC<PortStructureTreeProps> = ({ model, name }) => {
  const t = useTranslations('pipelineEditor.frostPanel')

  const fields = useMemo(() => {
    try {
      const tree = modelToSchemaTree(model, name)
      // The record class is the single '$' node; its children are the fields of a record.
      return tree.fields.length === 1 && tree.fields[0].path === '$' ? (tree.fields[0].children ?? []) : tree.fields
    } catch (error) {
      if (!(error instanceof ModelResolutionError)) throw error
      return null
    }
  }, [model, name])

  if (fields === null || fields.length === 0) {
    return null
  }

  return (
    <div className="space-y-1">
      <h4 className="text-muted-foreground text-xs font-medium tracking-wide uppercase">{t('structure')}</h4>
      <FieldList fields={fields} />
      <p className="text-muted-foreground text-xs">{t('structureHelp')}</p>
    </div>
  )
}

const FieldList: React.FC<{ fields: FieldNode[] }> = ({ fields }) => (
  <ul className="space-y-0.5">
    {fields.map(field => (
      <li key={field.path}>
        <FieldRow field={field} />
        {field.children && field.children.length > 0 && (
          <div className="border-border ml-1 border-l pl-3">
            <FieldList fields={field.children} />
          </div>
        )}
      </li>
    ))}
  </ul>
)

const FieldRow: React.FC<{ field: FieldNode }> = ({ field }) => {
  const t = useTranslations('pipelineEditor.frostPanel')

  return (
    <div className="flex items-baseline gap-2 text-xs">
      <span className="font-mono">{field.name}</span>
      <span className="text-muted-foreground">{field.type}</span>
      {field.required && (
        // The mandatory fields are the reason to read this at all: without them the port rejects
        // the record.
        <span className="text-destructive" title={t('required')}>
          *
        </span>
      )}
      {field.primaryKey && <span className="text-muted-foreground">{t('reference')}</span>}
    </div>
  )
}

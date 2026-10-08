'use client'

import { useTranslations } from 'next-intl'
import { ReactNode } from 'react'

import { BasicDropdownMenu } from '@/components/dropdown-menu/BasicDropdownMenu'
import { versionOfUrn } from '@/utils/coreUrn'

import { useActiveDiagram } from '../../hooks/use-active-diagram'

interface ToolbarProps {
  onImportFromDatastructure?: () => void
  content?: ReactNode
}

/**
 * The published structures this diagram was built from, each at the version it was loaded at. A
 * later version of a structure does not change a diagram that took an earlier one, so the version
 * is what has to be readable here.
 */
const ImportedStructures: React.FC = () => {
  const { diagram } = useActiveDiagram()
  const t = useTranslations('umlModeler.loadStandard')
  const imported = diagram.importedStructures ?? []
  if (imported.length === 0) return null

  return (
    <span>
      {t('pinned')}{' '}
      {imported.map(structure => (
        <span key={structure.urn} className="ml-1" title={structure.urn}>
          {structure.name} {versionOfUrn(structure.urn)}
        </span>
      ))}
    </span>
  )
}

export const Toolbar: React.FC<ToolbarProps> = ({ onImportFromDatastructure, content }) => {
  const t = useTranslations('umlModeler')

  return (
    <div className="flex items-center gap-1 px-3 py-2 bg-white border-b border-gray-200">
      {content && <div className="mr-3">{content}</div>}
      {/* File Operations */}
      <div className="flex items-center gap-1 mr-3">
        {onImportFromDatastructure && (
          <BasicDropdownMenu
            title={t('import.title')}
            menuItems={[
              {
                label: t('import.fromPlatform'),
                onClick: () => onImportFromDatastructure(),
              },
            ]}
          />
        )}
      </div>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Status/Info Area */}
      <div className="text-xs text-gray-500">
        <ImportedStructures />
      </div>
    </div>
  )
}

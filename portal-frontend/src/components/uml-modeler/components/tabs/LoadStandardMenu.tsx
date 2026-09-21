'use client'

/**
 * LoadStandardMenu Component
 *
 * Loads a structure the platform publishes into the diagram that is open.
 *
 * The menu has two levels: the sinks that publish structures, and the structures of the sink. Both
 * come from the platform, so a sink that begins to publish, and a structure a sink adds, appear
 * here without a change in this file.
 *
 * It is not offered on a released version: an import writes into the diagram, and a released
 * version does not change.
 */

import { Download, Loader2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useCallback, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'

import {
  type PublishedStructureSummary,
  useGetPublishedStructure,
  useGetPublishedStructures,
} from '@/app/services/api/published-structures/clientRequests'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import { useActiveDiagram } from '../../hooks/use-active-diagram'
import { useReadOnly } from '../../hooks/use-read-only'
import { SchemaImportError } from '../../services/jsonSchemaImportService'
import { mergeStructureIntoDiagram } from '../../services/structureMergeService'

export const LoadStandardMenu: React.FC = () => {
  const t = useTranslations('umlModeler.loadStandard')
  const { isReadOnly } = useReadOnly()
  const { diagram, dispatch, setRootNode } = useActiveDiagram()

  const [selected, setSelected] = useState<PublishedStructureSummary | null>(null)
  const selectedKey = selected?.key ?? null
  // The document arrives after the click. The guard keeps a re-render from merging it twice.
  const merging = useRef<string | null>(null)

  const sinks = useGetPublishedStructures({ isEnabled: !isReadOnly })
  const structure = useGetPublishedStructure({
    structureKey: selectedKey ?? undefined,
    isEnabled: Boolean(selectedKey),
  })

  const merge = useCallback(
    (document: Record<string, unknown>, source: PublishedStructureSummary) => {
      // The version is pinned, not the identity: a later version of the structure must leave a
      // Data structure that took an earlier one untouched.
      const result = mergeStructureIntoDiagram(diagram, document, {
        urn: source.urn ?? '',
        name: source.name,
      })
      dispatch({ type: 'SET_NODES', payload: [...diagram.nodes, ...result.nodes] })
      dispatch({ type: 'SET_EDGES', payload: [...diagram.edges, ...result.edges] })
      dispatch({ type: 'SET_IMPORTED_STRUCTURES', payload: result.importedStructures })
      if (result.rootElementId) setRootNode(result.rootElementId)
      // A renamed class is not a failure, but the modeller has to learn that the name moved.
      for (const renamed of result.renamed) {
        toast.warning(t('renamed', { from: renamed.from, to: renamed.to }))
      }
      toast.success(t('loaded'))
    },
    [diagram, dispatch, setRootNode, t],
  )

  useEffect(() => {
    const document = structure.data?.data
    if (!selected || !document || merging.current === selected.key) return
    merging.current = selected.key
    try {
      merge(document, selected)
    } catch (error) {
      if (error instanceof SchemaImportError) {
        toast.error(t('unreadable', { construct: error.construct }))
      } else {
        console.error('loading a published structure failed', error)
        toast.error(t('error'))
      }
    } finally {
      setSelected(null)
      merging.current = null
    }
  }, [selected, structure.data, merge, t])

  if (isReadOnly) return null

  const groups = sinks.data?.data ?? []
  const isLoading = sinks.isLoading || Boolean(selectedKey)

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="h-8 px-2" title={t('title')} disabled={isLoading}>
          {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
          <span className="ml-1 text-xs">{t('title')}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        {groups.length === 0 ? (
          <DropdownMenuItem disabled>{t('empty')}</DropdownMenuItem>
        ) : (
          groups.map(group => (
            <DropdownMenuSub key={group.sink}>
              <DropdownMenuSubTrigger>{group.sink}</DropdownMenuSubTrigger>
              <DropdownMenuSubContent>
                {group.structures.map(candidate => (
                  <DropdownMenuItem
                    key={candidate.key}
                    className="hover:cursor-pointer"
                    onSelect={() => setSelected(candidate)}
                  >
                    {candidate.name}
                  </DropdownMenuItem>
                ))}
              </DropdownMenuSubContent>
            </DropdownMenuSub>
          ))
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

'use client'

/**
 * ImportMenu Component
 *
 * The one way into the diagram from outside: a file, or a structure the platform publishes.
 *
 * The file is read by the caller, which owns the file input and the question whether the file
 * replaces the diagram or is added to it. A standard structure is always added, and this menu loads
 * and merges it itself. Its submenu has two levels: the sinks that publish structures, and the
 * structures of the sink. Both come from the platform, so a sink that begins to publish, and a
 * structure a sink adds, appear here without a change in this file.
 *
 * It is not offered on a released version: an import writes into the diagram, and a released
 * version does not change.
 */

import { useReactFlow } from '@xyflow/react'
import { Loader2, Upload } from 'lucide-react'
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
  DropdownMenuSeparator,
  DropdownMenuSub,
  DropdownMenuSubContent,
  DropdownMenuSubTrigger,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

import { useActiveDiagram } from '../../hooks/use-active-diagram'
import { useReadOnly } from '../../hooks/use-read-only'
import { SchemaImportError } from '../../services/jsonSchemaImportService'
import { mergeStructureIntoDiagram } from '../../services/structureMergeService'

interface ImportMenuProps {
  /** Opens the file dialog. Without it the menu offers the standard structures only. */
  onImportFile?: () => void
}

export const ImportMenu: React.FC<ImportMenuProps> = ({ onImportFile }) => {
  const t = useTranslations('umlModeler.loadStandard')
  const tModeler = useTranslations('umlModeler')
  const { isReadOnly } = useReadOnly()
  const { diagram, dispatch } = useActiveDiagram()
  const { fitView } = useReactFlow()

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
      // The menu offers only published structures. A pin without a version would name nothing.
      if (!source.urn) throw new Error(`the structure '${source.key}' has no published version`)
      const result = mergeStructureIntoDiagram(diagram, document, { urn: source.urn, name: source.name })
      if (result.nodes.length === 0) {
        // Nothing was loaded. Saying otherwise would send the modeller looking for classes that
        // are not there, and a pin for it would record a structure the diagram does not hold.
        toast.warning(t('emptyStructure'))
        return
      }
      // One action. The provider recomputes every dispatch from the diagram of the current
      // render, so a second one in this handler would drop what the first one added. The root
      // flag needs no call of its own either — it is already on the element the merge answers.
      dispatch({
        type: 'MERGE_STRUCTURE',
        payload: {
          nodes: result.nodes,
          edges: result.edges,
          importedStructures: result.importedStructures,
        },
      })
      // The loaded classes are placed clear of what is already drawn, which puts them outside the
      // view the modeller is looking at. Showing them is the point of the click, so the canvas
      // moves to them — after the frame in which React Flow learns about them.
      const loaded = result.nodes.map(node => ({ id: node.id }))
      requestAnimationFrame(() => {
        void fitView({ nodes: loaded, padding: 0.2, duration: 400 })
      })

      // A renamed class is not a failure, but the modeller has to learn that the name moved.
      for (const renamed of result.renamed) {
        toast.warning(t('renamed', { from: renamed.from, to: renamed.to }))
      }
      toast.success(t('loaded'))
    },
    [diagram, dispatch, fitView, t],
  )

  useEffect(() => {
    // A failed request ends the selection; otherwise the menu would wait for it forever.
    if (selected && structure.isError) {
      setSelected(null)
      return
    }
    const document = structure.data?.data
    // Placeholder data is the answer to the previous selection, kept while this one loads. Merging
    // it would put the wrong structure into the diagram.
    if (structure.isPlaceholderData) return
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
  }, [selected, structure.data, structure.isPlaceholderData, structure.isError, merge, t])

  if (isReadOnly) return null

  const groups = sinks.data?.data ?? []
  const isLoading = sinks.isLoading || Boolean(selectedKey)

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="h-8 px-2 text-xs font-normal text-gray-700 hover:text-gray-900"
          title={tModeler('import.title')}
          disabled={isLoading}
        >
          {isLoading ? (
            <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" />
          ) : (
            <Upload className="h-3.5 w-3.5 mr-1 text-gray-600" />
          )}
          <span>{tModeler('import.title')}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {onImportFile && (
          <>
            <DropdownMenuItem className="hover:cursor-pointer" onSelect={onImportFile}>
              {tModeler('import.fromFile')}
            </DropdownMenuItem>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuSub>
          <DropdownMenuSubTrigger>{tModeler('import.standard')}</DropdownMenuSubTrigger>
          <DropdownMenuSubContent>
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
                        // A structure that was not published yet has no version to pin.
                        disabled={!candidate.urn}
                        onSelect={() => setSelected(candidate)}
                      >
                        {candidate.name}
                      </DropdownMenuItem>
                    ))}
                  </DropdownMenuSubContent>
                </DropdownMenuSub>
              ))
            )}
          </DropdownMenuSubContent>
        </DropdownMenuSub>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

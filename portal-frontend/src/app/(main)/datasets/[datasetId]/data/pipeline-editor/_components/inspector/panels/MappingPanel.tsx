'use client'

/**
 * MappingPanel Component
 *
 * Inspector panel for Mapping nodes.
 * Includes Monaco editor for mapping code.
 * Monaco Editor is dynamically imported to avoid slow compilation.
 *
 */

import { Info } from 'lucide-react'
import dynamic from 'next/dynamic'
import { useCallback } from 'react'

import type { MappingNodeData } from '../../../_types/nodes'

// Dynamically import Monaco Editor to avoid slow compilation
// Monaco Editor is ~8MB and importing it synchronously causes massive build overhead
const MonacoEditor = dynamic(
  () =>
    import('@monaco-editor/react').then(async mod => {
      // Configure Monaco loader to use local package instead of CDN
      const monaco = await import('monaco-editor')
      mod.loader.config({ monaco })
      return mod.default
    }),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-[200px] items-center justify-center bg-muted/30">
        <span className="text-sm text-muted-foreground">Loading editor...</span>
      </div>
    ),
  },
)

interface MappingPanelProps {
  data: MappingNodeData
  onUpdate: (data: Partial<MappingNodeData>) => void
}

export const MappingPanel: React.FC<MappingPanelProps> = ({ data, onUpdate }) => {
  const handleCodeChange = useCallback(
    (value: string | undefined) => {
      const code = value || ''
      onUpdate({
        mappingCode: code,
        configured: code.trim() !== '',
      })
    },
    [onUpdate],
  )

  return (
    <div className="flex h-full flex-col space-y-4 p-4">
      <div className="flex items-start gap-2 rounded-md bg-muted/50 p-3">
        <Info className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        <p className="text-sm text-muted-foreground">
          Define your data transformation mapping using YAML or JSON format.
        </p>
      </div>

      <div className="space-y-2">
        <label className="text-sm font-medium text-foreground">Mapping Code</label>
        <div className="overflow-hidden rounded-md border border-border">
          <MonacoEditor
            height="200px"
            defaultLanguage="yaml"
            value={data.mappingCode || '# Define your mapping here\n'}
            onChange={handleCodeChange}
            options={{
              minimap: { enabled: false },
              fontSize: 13,
              lineNumbers: 'on',
              scrollBeyondLastLine: false,
              wordWrap: 'on',
              wrappingStrategy: 'advanced',
              folding: true,
              automaticLayout: true,
              tabSize: 2,
            }}
            theme="vs-light"
          />
        </div>
      </div>

      <div className="space-y-2">
        <h4 className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Status</h4>
        <div
          className={`rounded-md p-2 text-sm ${
            data.configured
              ? 'bg-green-50 text-green-800 dark:bg-green-950/30 dark:text-green-200'
              : 'bg-amber-50 text-amber-800 dark:bg-amber-950/30 dark:text-amber-200'
          }`}
        >
          {data.configured ? 'Mapping configured' : 'No mapping defined'}
        </div>
      </div>
    </div>
  )
}

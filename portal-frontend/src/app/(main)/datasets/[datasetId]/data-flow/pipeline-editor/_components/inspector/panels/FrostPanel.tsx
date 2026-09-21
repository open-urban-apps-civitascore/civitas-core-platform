'use client'

/**
 * FrostPanel Component
 *
 * Inspector panel for FROST storage nodes. The node carries one decision: the port, which declares
 * what the sink writes. There is no default and no pre-selection — an empty port is an error state,
 * not an empty field, because the platform must not decide what a Pipeline writes.
 */

import { useTranslations } from 'next-intl'
import { useCallback } from 'react'

import { useGetPublishedStructure } from '@/app/services/api/published-structures/clientRequests'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

import { FROST_PORT_LOGIC, type FrostSinkPort, isFrostSinkPort, portsOfLogic } from '../../../_constants/frostPorts'
import type { FrostNodeData } from '../../../_types/nodes'
import { EntityMetadata } from '../components/EntityMetadata'
import { PortStructureTree } from '../components/PortStructureTree'

interface FrostPanelProps {
  data: FrostNodeData
  onUpdate: (updates: Partial<FrostNodeData>) => void
}

export const FrostPanel: React.FC<FrostPanelProps> = ({ data, onUpdate }) => {
  const t = useTranslations('pipelineEditor')

  const handlePortChange = useCallback(
    (value: string) => {
      if (!isFrostSinkPort(value)) {
        return
      }
      // The node counts as configured exactly when it carries a port; the validation panel and the
      // publish gate read that one flag.
      onUpdate({ port: value, configured: true })
    },
    [onUpdate],
  )

  return (
    <div className="space-y-4 p-4">
      <div className="space-y-2">
        <Label htmlFor="frost-port">{t('frostPanel.port')}</Label>
        <Select value={data.port ?? ''} onValueChange={handlePortChange}>
          <SelectTrigger id="frost-port" aria-invalid={!data.port}>
            <SelectValue placeholder={t('frostPanel.portPlaceholder')} />
          </SelectTrigger>
          <SelectContent>
            {FROST_PORT_LOGIC.map(logic => {
              const ports = portsOfLogic(logic)
              return ports.length === 0 ? null : (
                <SelectGroup key={logic}>
                  <SelectLabel>{t(`frostPanel.logic.${logic}`)}</SelectLabel>
                  {ports.map(port => (
                    <SelectItem key={port} value={port}>
                      <span className="font-medium">{port}</span>
                      <span className="text-muted-foreground ml-2 text-xs">{t(`frostPanel.ports.${port}.cost`)}</span>
                    </SelectItem>
                  ))}
                </SelectGroup>
              )
            })}
          </SelectContent>
        </Select>
        <p className="text-muted-foreground text-xs">{t('frostPanel.portHelp')}</p>
      </div>

      {data.port ? <PortSummary port={data.port} /> : null}

      <EntityMetadata
        title={t('frostPanel.details')}
        items={[
          { label: t('frostPanel.serverName'), value: data.serverName },
          { label: t('frostPanel.serverUrl'), value: t('frostPanel.internalServer') },
          { label: t('frostPanel.version'), value: data.version },
        ]}
      />
    </div>
  )
}

/**
 * What the selected port writes, and the structure it publishes: the field names, the data types
 * and the fields a record must carry.
 *
 * The structure comes from the platform. While it is not there — it is still loading, or the
 * request failed — the panel names the references the port resolves instead of showing nothing:
 * that much is known here, and a modeller who sees it can start.
 */
const PortSummary: React.FC<{ port: FrostSinkPort }> = ({ port }) => {
  const t = useTranslations('pipelineEditor')
  const structure = useGetPublishedStructure({ structureKey: port, isEnabled: true })
  const model = structure.data?.data

  return (
    <div className="space-y-2">
      <p className="text-sm">{t(`frostPanel.ports.${port}.description`)}</p>
      {model ? (
        <PortStructureTree model={model} name={port} />
      ) : (
        <p className="text-muted-foreground text-xs">
          <span className="font-medium">{t('frostPanel.expects')}</span> {t(`frostPanel.ports.${port}.expects`)}
        </p>
      )}
    </div>
  )
}

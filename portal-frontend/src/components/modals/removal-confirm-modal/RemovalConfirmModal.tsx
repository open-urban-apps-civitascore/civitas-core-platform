'use client'

import { useTranslations } from 'next-intl'

import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import type { RemovalConfirmationDialog } from '@/hooks/use-removal-confirmation'

interface RemovalConfirmModalProps {
  dialog: RemovalConfirmationDialog
  title: string
  description: string
  confirmButtonTitle: string
}

interface RemovedItemListProps {
  heading: string
  names: { id: string; name: string }[]
  testId: string
}

const RemovedItemList = (props: RemovedItemListProps) => {
  const { heading, names, testId } = props
  if (names.length === 0) return null
  return (
    <div data-testid={testId}>
      <p className="font-medium text-foreground">{heading}</p>
      <ul className="list-disc pl-5">
        {names.map(item => (
          <li key={item.id}>{item.name}</li>
        ))}
      </ul>
    </div>
  )
}

export const RemovalConfirmModal = (props: RemovalConfirmModalProps) => {
  const { dialog, title, description, confirmButtonTitle } = props
  const { open, impact, isLoading, onConfirm, onCancel } = dialog
  const t = useTranslations('common.removalConfirmModal')

  const layers = impact?.layers.map(layer => ({ id: layer.id, name: layer.title || layer.layerName })) ?? []
  const apis = impact?.apis.map(api => ({ id: api.id, name: api.name })) ?? []
  const hasStoredData = impact?.dataSinks.some(dataSink => dataSink.provisioned) ?? false

  return (
    <WarningModal
      open={open}
      onOpenChange={isOpen => {
        if (!isOpen) onCancel()
      }}
      title={title}
      description={
        <div className="space-y-3">
          <p>{description}</p>
          <RemovedItemList heading={t('layers')} names={layers} testId="removalConfirmLayers" />
          <RemovedItemList heading={t('apis')} names={apis} testId="removalConfirmApis" />
          {hasStoredData && <p data-testid="removalConfirmStoredData">{t('storedData')}</p>}
        </div>
      }
      confirmButtonTitle={confirmButtonTitle}
      onConfirm={onConfirm}
      onDiscard={onCancel}
      isLoading={isLoading}
    />
  )
}

'use client'

import { useTranslations } from 'next-intl'

import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import type { RemovalConfirmationDialog } from '@/hooks/use-removal-confirmation'
import { deletesStoredData } from '@/utils/dataSinkRemoval'

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
  const { open, removal, onConfirm, onCancel } = dialog
  const t = useTranslations('common.removalConfirmModal')

  const layers = removal?.layers.map(layer => ({ id: layer.id, name: layer.title || layer.layerName })) ?? []
  const apis = removal?.apis.map(api => ({ id: api.id, name: api.name })) ?? []
  const hasStoredData = !!removal && deletesStoredData(removal)

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
          <div className="max-h-[50vh] space-y-3 overflow-y-auto">
            <RemovedItemList heading={t('layers')} names={layers} testId="removalConfirmLayers" />
            <RemovedItemList heading={t('apis')} names={apis} testId="removalConfirmApis" />
          </div>
          {hasStoredData && <p data-testid="removalConfirmStoredData">{t('storedData')}</p>}
        </div>
      }
      confirmButtonTitle={confirmButtonTitle}
      onConfirm={onConfirm}
      onDiscard={onCancel}
    />
  )
}

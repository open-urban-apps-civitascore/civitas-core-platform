import { AlertTriangle, Info } from 'lucide-react'
import { useTranslations } from 'next-intl'

import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'

interface ChangeDatapoolModalProps {
  isOpen: boolean
  onDiscard: () => void
  onConfirm: () => void
}

export const ChangeDatapoolModal = ({ isOpen, onDiscard, onConfirm }: ChangeDatapoolModalProps) => {
  const t = useTranslations('datasets.changeDatapoolModal')
  const tCommon = useTranslations('common.actions')

  return (
    <Dialog open={isOpen}>
      <DialogContent className="sm:max-w-lg" showCloseButton={false} aria-describedby={undefined}>
        <DialogHeader>
          <DialogTitle>{t('title')}</DialogTitle>
        </DialogHeader>

        <div className="flex flex-col gap-4 text-sm">
          <p>{t('description')}</p>

          <ul className="list-disc pl-5 flex flex-col gap-1">
            <li>{t('consequences.permissions')}</li>
            <li>{t('consequences.access')}</li>
            <li>{t('consequences.otherUsers')}</li>
          </ul>

          <div className="flex gap-2 items-start">
            <AlertTriangle className="mt-0.5 shrink-0 size-4" />
            <p>{t('warnings.inheritance')}</p>
          </div>

          <div className="flex gap-2 items-start">
            <Info className="mt-0.5 shrink-0 size-4" />
            <p>{t('warnings.checkPermissions')}</p>
          </div>
        </div>

        <DialogFooter className="flex gap-2 sm:justify-end">
          <Button type="button" variant="outline" onClick={onDiscard}>
            {tCommon('cancel')}
          </Button>
          <Button type="button" onClick={onConfirm}>
            {t('confirmButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

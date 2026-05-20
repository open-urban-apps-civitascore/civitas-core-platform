import { useTranslations } from 'next-intl'
import React from 'react'
import { UseFormReturn } from 'react-hook-form'

import { TextField } from '@/components/form/fields/TextField'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { WfsWmsApiFormData } from '@/types/namedApis'

interface BoundingBoxConfigProps {
  form: UseFormReturn<WfsWmsApiFormData>
}

const BBOX_FIELDS = ['minX', 'minY', 'maxX', 'maxY'] as const

export const BoundingBoxConfig = (props: BoundingBoxConfigProps) => {
  const { form } = props
  const tError = useTranslations()
  const t = useTranslations('datasets.overview.completion.apis.config.layer')

  const isMobile = useIsMobile()
  const bbox = form.formState.errors.layer?.nativeBoundingBox
  const bboxError = BBOX_FIELDS.map(f => bbox?.[f]?.message).find(Boolean)

  return (
    <div className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]')}>
      <label className="text-sm font-medium leading-none">
        {t('geometry.boundingBox')}
        <span className="text-red-500 ml-1">*</span>
      </label>
      <div>
        <div className="grid grid-cols-4 gap-2">
          {BBOX_FIELDS.map(field => (
            <TextField
              key={field}
              form={form}
              label={field}
              name={`layer.nativeBoundingBox.${field}`}
              placeholder=""
              type="number"
              required
              shouldShowErrors={false}
              formItemProps={{ className: 'flex flex-col gap-1' }}
            />
          ))}
        </div>
        {bboxError && (
          <p className="text-sm font-medium text-destructive mt-2" data-testid="nativeBoundingBoxFormMessage">
            {tError(bboxError)}
          </p>
        )}
      </div>
    </div>
  )
}

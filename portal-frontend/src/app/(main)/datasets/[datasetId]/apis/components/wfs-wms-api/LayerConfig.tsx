import { useTranslations } from 'next-intl'
import React from 'react'
import { UseFormReturn } from 'react-hook-form'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { useIsMobile } from '@/hooks/use-mobile'
import { cn } from '@/lib/utils'
import { WfsWmsApiFormData } from '@/types/namedApis'
import { FormItem, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { crsOptions } from '@/const/crs'
import { BoundingBoxConfig } from './BoundingBoxConfig'

interface LayerConfigProps {
  form: UseFormReturn<WfsWmsApiFormData>
}

export const LayerConfig = (props: LayerConfigProps) => {
  const { form } = props
  const t = useTranslations('datasets.overview.completion.apis.config.layer')
  const isMobile = useIsMobile()

  return (
    <div className="flex flex-col">
      {/* Base Info section */}
      <DetailsFieldContainer isTitleField>
        <SubHeader title={t('baseInfo.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2 pt-6">
        <TextField form={form} label={t('baseInfo.title')} name="layer.title" placeholder="" required />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2">
        <TextField form={form} label={t('baseInfo.technicalName')} name="layer.layerName" placeholder="" required />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2 pb-6">
        <TextField form={form} label={t('baseInfo.description')} name="layer.layerDescription" placeholder="" />
      </DetailsFieldContainer>

      {/* Data Selection section */}
      <DetailsFieldContainer isTitleField>
        <SubHeader title={t('dataSelection.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2 pt-6">
        <TextField form={form} label={t('dataSelection.table')} name="layer.table" placeholder="" required />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2">
        <TextField form={form} label={t('dataSelection.attributes')} name="layer.attribute" placeholder="" required />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2 pb-6">
        <TextField form={form} label={t('dataSelection.filter')} name="layer.cqlFilter" placeholder="" />
      </DetailsFieldContainer>

      {/* Geometry section */}
      <DetailsFieldContainer isTitleField>
        <SubHeader title={t('geometry.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2 pt-6">
        <TextField
          form={form}
          label={t('geometry.geometryField')}
          name="layer.geometryColumnRef"
          placeholder=""
          required
        />
      </DetailsFieldContainer>
      {/* The native CRS is a read-only field and not part of the form. It only represents the crs information from the datastructure */}
      <DetailsFieldContainer className="border-b-0 py-2">
        <FormItem className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]')}>
          <FormLabel>{t('geometry.nativeCrs')}</FormLabel>
          <Input
            data-testid="nativeCrsTextField"
            data-test-element="formField"
            className="disabled:opacity-100 disabled:border-transparent disabled:shadow-none disabled:h-9 disabled:py-0"
            value="Test CRS"
            disabled={true}
          />
        </FormItem>
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2 pb-6">
        <FormSelect
          form={form}
          label={t('geometry.definedCrs')}
          name="layer.crs"
          id="wfsWmsLayerCrs"
          options={crsOptions}
        />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2 pb-6">
        <BoundingBoxConfig form={form}/>
      </DetailsFieldContainer>

      {/* Style section */}
      <DetailsFieldContainer isTitleField>
        <SubHeader title={t('style.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2 pt-6">
        <TextField form={form} label={t('style.standardStyle')} name="layer.title" placeholder="" required />
      </DetailsFieldContainer>
      <DetailsFieldContainer className="border-b-0 py-2">
        <TextField form={form} label={t('style.alternativeStyles')} name="layer.layerName" placeholder="" required />
      </DetailsFieldContainer>
      <div className={cn(isMobile ? 'grid gap-4' : 'grid grid-cols-[minmax(0,270px)_minmax(0,384px)]')}>
        <span />
        <Button variant="outline">{t('style.addAlternativeStyle')}</Button>
      </div>
    </div>
  )
}

import { useTranslations } from 'next-intl'
import { UseFormReturn } from 'react-hook-form'

import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormComboboxMulti } from '@/components/form/fields/FormComboboxMulti'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { TextField } from '@/components/form/fields/TextField'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { Button } from '@/components/ui/button'
import { UMLAttribute, UMLClass } from '@/components/uml-modeler/types/uml'
import { crsOptions } from '@/const/crs'
import { Datasink } from '@/types/datasinks'
import { DatastructureVersion } from '@/types/datastructures'
import { LayerFormData, Style, WfsWmsApiFormData } from '@/types/namedApis'

import { BoundingBoxConfig } from './BoundingBoxConfig'
import { LayerSidebar } from './LayerSidebar'

interface LayerConfigProps {
  form: UseFormReturn<WfsWmsApiFormData>
  existingLayers: LayerFormData[]
  styles: Style[]
  postgisDatasinks: Datasink[]
  postGisDatastructures: DatastructureVersion[]
  selectedLayerId: string | null
  onSelectLayer: (layerId: string) => void
  onAddLayer: () => void
  onTableChange: (datasinkId: string) => void
}

const getUmlClass = (
  datastructures: DatastructureVersion[],
  datastructureVersionId: string | undefined,
): UMLClass | undefined =>
  datastructures.find(d => d.id === datastructureVersionId)?.styles?.nodes[0].data.element as UMLClass | undefined

const toAttributeOptions = (umlClass: UMLClass | undefined) =>
  umlClass?.attributes?.map((attr: UMLAttribute) => ({ value: attr.name, label: attr.name })) ?? []

export const LayerConfig = (props: LayerConfigProps) => {
  const {
    form,
    existingLayers,
    styles,
    postgisDatasinks,
    postGisDatastructures,
    selectedLayerId,
    onSelectLayer,
    onAddLayer,
    onTableChange,
  } = props
  const t = useTranslations('datasets.overview.completion.apis.config.layer')

  const tableWatch = form.watch('layer.dataSinkId')
  const crsWatch = form.watch('layer.crs')

  const handleCalculateFromCrs = () => {
    const crsOption = crsOptions.find(o => o.value === crsWatch)
    if (!crsOption) return
    const [minX, minY, maxX, maxY] = crsOption.nativeBounds
    form.setValue('layer.nativeBoundingBox.minX', String(minX), { shouldDirty: true })
    form.setValue('layer.nativeBoundingBox.minY', String(minY), { shouldDirty: true })
    form.setValue('layer.nativeBoundingBox.maxX', String(maxX), { shouldDirty: true })
    form.setValue('layer.nativeBoundingBox.maxY', String(maxY), { shouldDirty: true })
  }

  const wideField = { className: 'grid-cols-[minmax(0,270px)_minmax(0,512px)]' }

  const styleOptions = styles.map(style => ({ value: style.id, label: style.name }))

  const tableOptions = postgisDatasinks?.map(datasink => ({
    value: datasink.id,
    label: datasink.configuration.tableName,
  }))

  const currentDatastructureVersion = postgisDatasinks?.find(datasink => datasink.id === tableWatch)?.configuration
    .dataStructureVersion

  const umlClass = getUmlClass(postGisDatastructures, currentDatastructureVersion?.id)
  const attributeOptions = toAttributeOptions(umlClass)

  return (
    <>
      <DetailsFieldContainer className="pt-0 pb-3">
        <SubHeader title={t('sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
      </DetailsFieldContainer>
      <div className="flex gap-6">
        <LayerSidebar
          existingLayers={existingLayers}
          selectedLayerId={selectedLayerId}
          onSelectLayer={onSelectLayer}
          onAddLayer={onAddLayer}
        />
        <div className="flex flex-col">
          {/* Base Info section */}
          <DetailsFieldContainer isTitleField>
            <SubHeader title={t('baseInfo.sectionTitle')} titleClassName="text-2xl leading-none font-bold mt-6" />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2 pt-6">
            <TextField
              form={form}
              label={t('baseInfo.title')}
              name="layer.title"
              placeholder=""
              required
              formItemProps={wideField}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2">
            <TextField
              form={form}
              label={t('baseInfo.technicalName')}
              name="layer.layerName"
              placeholder=""
              required
              formItemProps={wideField}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2 pb-6">
            <TextField
              form={form}
              label={t('baseInfo.description')}
              name="layer.description"
              placeholder=""
              formItemProps={wideField}
            />
          </DetailsFieldContainer>

          {/* Data Selection section */}
          <DetailsFieldContainer isTitleField>
            <SubHeader title={t('dataSelection.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2 pt-6">
            <FormSelect
              form={form}
              id="tableSelect"
              label={t('dataSelection.table')}
              name="layer.dataSinkId"
              options={tableOptions}
              onChange={onTableChange}
              formItemProps={wideField}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2">
            <FormComboboxMulti
              form={form}
              id="layerAttribute"
              items={attributeOptions}
              label={t('dataSelection.attributes')}
              name="layer.attribute"
              required
              hasSelectAllOption
              formItemProps={wideField}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2 pb-6">
            <TextField
              form={form}
              label={t('dataSelection.filter')}
              name="layer.cqlFilter"
              placeholder=""
              formItemProps={wideField}
            />
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
              formItemProps={wideField}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2">
            <TextField
              form={form}
              label={t('geometry.nativeCrs')}
              name="layer.nativeCRS"
              className="disabled:opacity-100 disabled:border-transparent disabled:shadow-none disabled:h-9 disabled:py-0"
              disabled={true}
              placeholder=""
              formItemProps={wideField}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2 pb-6 flex flex-col gap-2">
            <FormSelect
              form={form}
              label={t('geometry.definedCrs')}
              name="layer.crs"
              id="wfsWmsLayerCrs"
              options={crsOptions}
              formItemProps={wideField}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2 pb-6">
            <BoundingBoxConfig form={form} className="grid-cols-[minmax(0,270px)_minmax(0,512px)]" />
            <div className="grid grid-cols-[minmax(0,270px)_minmax(0,512px)]">
              <div />
              <div className="mt-1">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  disabled={!crsWatch}
                  onClick={handleCalculateFromCrs}
                  className="text-xs h-7 px-2 hover:text-primary hover:bg-transparent"
                >
                  {t('geometry.calculateFromCrs')}
                </Button>
              </div>
            </div>
          </DetailsFieldContainer>

          {/* Style section */}
          <DetailsFieldContainer isTitleField>
            <SubHeader title={t('style.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2 pt-6">
            <FormSelect
              form={form}
              id="defaultStyleId"
              label={t('style.standardStyle')}
              name="layer.defaultStyleId"
              options={styleOptions}
              placeholder={t('style.default')}
              formItemProps={wideField}
            />
          </DetailsFieldContainer>
          <DetailsFieldContainer className="border-b-0 py-2 pb-6">
            <FormComboboxMulti
              form={form}
              id="alternativeStyleIds"
              items={styleOptions}
              label={t('style.alternativeStyles')}
              name="layer.alternativeStyleIds"
              formItemProps={wideField}
            />
          </DetailsFieldContainer>
        </div>
      </div>
    </>
  )
}

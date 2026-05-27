import { useTranslations } from 'next-intl'
import { FieldPath, UseFormReturn } from 'react-hook-form'

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
  selectedLayerIndex: number | null
  onSelectLayer: (index: number) => void
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
    selectedLayerIndex,
    onSelectLayer,
    onAddLayer,
    onTableChange,
  } = props
  const t = useTranslations('datasets.overview.completion.apis.config.layer')

  const lp = (path: string): FieldPath<WfsWmsApiFormData> =>
    `layers.${selectedLayerIndex}.${path}` as FieldPath<WfsWmsApiFormData>

  const allLayers = form.watch('layers')
  const selectedLayer = selectedLayerIndex !== null ? allLayers[selectedLayerIndex] : null
  const tableWatch = selectedLayer?.dataSinkId ?? ''
  const crsWatch = selectedLayer?.crs ?? ''

  const handleCalculateFromCrs = () => {
    if (selectedLayerIndex === null) return
    const crsOption = crsOptions.find(o => o.value === crsWatch)
    if (!crsOption) return
    const [minX, minY, maxX, maxY] = crsOption.nativeBounds
    form.setValue(lp('nativeBoundingBox.minX'), String(minX), { shouldDirty: true })
    form.setValue(lp('nativeBoundingBox.minY'), String(minY), { shouldDirty: true })
    form.setValue(lp('nativeBoundingBox.maxX'), String(maxX), { shouldDirty: true })
    form.setValue(lp('nativeBoundingBox.maxY'), String(maxY), { shouldDirty: true })
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
          selectedLayerIndex={selectedLayerIndex}
          onSelectLayer={onSelectLayer}
          onAddLayer={onAddLayer}
        />
        {selectedLayerIndex !== null && (
          <div className="flex flex-col">
            <DetailsFieldContainer isTitleField>
              <SubHeader title={t('baseInfo.sectionTitle')} titleClassName="text-2xl leading-none font-bold mt-6" />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2 pt-6">
              <TextField
                form={form}
                label={t('baseInfo.title')}
                name={lp('title')}
                placeholder={t('baseInfo.title')}
                required
                formItemProps={wideField}
              />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2">
              <TextField
                form={form}
                label={t('baseInfo.technicalName')}
                name={lp('layerName')}
                placeholder={t('baseInfo.technicalName')}
                required
                formItemProps={wideField}
              />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2 pb-6">
              <TextField
                form={form}
                label={t('baseInfo.description')}
                name={lp('description')}
                placeholder={t('baseInfo.description')}
                formItemProps={wideField}
              />
            </DetailsFieldContainer>

            <DetailsFieldContainer isTitleField>
              <SubHeader title={t('dataSelection.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2 pt-6">
              <FormSelect
                form={form}
                id="tableSelect"
                label={t('dataSelection.table')}
                name={lp('dataSinkId')}
                options={tableOptions}
                onChange={onTableChange}
                formItemProps={wideField}
                placeholder={t('dataSelection.tablePlaceHolder')}
                required
              />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2">
              <FormComboboxMulti
                form={form}
                id="layerAttribute"
                items={attributeOptions}
                label={t('dataSelection.attributes')}
                name={lp('attribute')}
                placeholder={t('dataSelection.attributesPlaceHolder')}
                required
                hasSelectAllOption
                formItemProps={wideField}
              />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2 pb-6">
              <TextField
                form={form}
                label={t('dataSelection.filter')}
                name={lp('cqlFilter')}
                placeholder={t('dataSelection.filter')}
                formItemProps={wideField}
              />
            </DetailsFieldContainer>

            <DetailsFieldContainer isTitleField>
              <SubHeader title={t('geometry.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2 pt-6">
              <FormSelect
                form={form}
                id="geometryColumnRef"
                label={t('geometry.geometryField')}
                name={lp('geometryColumnRef')}
                options={attributeOptions}
                placeholder={t('geometry.geometryFieldPlaceHolder')}
                required
                formItemProps={wideField}
              />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2">
              <TextField
                form={form}
                label={t('geometry.nativeCrs')}
                name={lp('nativeCRS')}
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
                name={lp('crs')}
                id="wfsWmsLayerCrs"
                options={crsOptions}
                placeholder={t('geometry.crsPlaceHolder')}
                formItemProps={wideField}
              />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2 pb-6">
              <BoundingBoxConfig
                form={form}
                layerFieldIndex={selectedLayerIndex}
                className="grid-cols-[minmax(0,270px)_minmax(0,512px)]"
              />
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

            <DetailsFieldContainer isTitleField>
              <SubHeader title={t('style.sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
            </DetailsFieldContainer>
            <DetailsFieldContainer className="border-b-0 py-2 pt-6">
              <FormSelect
                form={form}
                id="defaultStyleId"
                label={t('style.standardStyle')}
                name={lp('defaultStyleId')}
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
                name={lp('alternativeStyleIds')}
                placeholder={t('style.alternativeStylesPlaceHolder')}
                formItemProps={wideField}
              />
            </DetailsFieldContainer>
          </div>
        )}
      </div>
    </>
  )
}

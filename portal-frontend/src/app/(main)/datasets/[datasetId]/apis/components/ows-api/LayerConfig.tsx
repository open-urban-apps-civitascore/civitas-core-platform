import { List, Plus, Trash2 } from 'lucide-react'
import { useTranslations } from 'next-intl'
import { useState } from 'react'
import { FieldPath, UseFormReturn } from 'react-hook-form'

import { ContentCard } from '@/components/content-card/ContentCard'
import { DetailsFieldContainer } from '@/components/form/DetailsFieldContainer'
import { FormComboboxMulti } from '@/components/form/fields/FormComboboxMulti'
import { FormSelect } from '@/components/form/fields/FormSelect'
import { TextField } from '@/components/form/fields/TextField'
import { WarningModal } from '@/components/modals/warning-modal/WarningModal'
import { NoDataCard } from '@/components/no-data/no-data-card/NoDataCard'
import { SubHeader } from '@/components/page-header/sub-header/SubHeader'
import { SidebarList } from '@/components/sidebar-list/SidebarList'
import { AlertBox } from '@/components/text-box/TextBox'
import { Button } from '@/components/ui/button'
import { FormItem, FormLabel } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { UMLAttribute, UMLClass } from '@/components/uml-modeler/types/uml'
import { crsOptions } from '@/const/crs'
import { cn } from '@/lib/utils'
import { DataSink } from '@/types/datasinks'
import { DatastructureVersion } from '@/types/datastructures'
import { OwsApiFormData } from '@/types/namedApis'
import { Style } from '@/types/styles'
import { getEmptyLabelIndex, isNewItem } from '@/utils/common'

import { BoundingBoxConfig } from './BoundingBoxConfig'

interface LayerConfigProps {
  form: UseFormReturn<OwsApiFormData>
  styles: Style[]
  postgisDataSinks: DataSink[]
  postGisDatastructures: DatastructureVersion[]
  selectedLayerIndex: number | null
  isReadOnly: boolean
  isDeleteLayerLoading?: boolean
  onSelectLayer: (index: number) => void
  onAddLayer: () => void
  onDeleteLayer?: () => Promise<void>
  onTableChange: (dataSinkId: string) => void
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
    styles,
    postgisDataSinks,
    postGisDatastructures,
    selectedLayerIndex,
    isReadOnly,
    isDeleteLayerLoading,
    onSelectLayer,
    onAddLayer,
    onDeleteLayer,
    onTableChange,
  } = props
  const t = useTranslations('datasets.overview.completion.apis.config.layer')
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false)

  const isSelectedLayerNewAndClean = () => {
    if (selectedLayerIndex === null) return false
    const layer = form.getValues(`layers.${selectedLayerIndex}`)
    if (!layer?.id.startsWith('new-')) return false
    return !layer.title && !layer.layerName && !layer.dataSinkId
  }

  const handleDeleteClick = () => {
    if (isSelectedLayerNewAndClean()) {
      void onDeleteLayer?.()
    } else {
      setIsDeleteModalOpen(true)
    }
  }

  const handleConfirmDelete = () => {
    void onDeleteLayer?.().then(() => setIsDeleteModalOpen(false))
  }

  const layerPath = (path: string): FieldPath<OwsApiFormData> =>
    `layers.${selectedLayerIndex}.${path}` as FieldPath<OwsApiFormData>

  const allLayers = form.watch('layers')
  const selectedLayer = selectedLayerIndex !== null ? allLayers[selectedLayerIndex] : null
  const tableWatch = selectedLayer?.dataSinkId ?? ''
  const crsWatch = selectedLayer?.crs ?? ''

  const handleCalculateFromCrs = () => {
    if (selectedLayerIndex === null) return
    const crsOption = crsOptions.find(o => o.value === crsWatch)
    if (!crsOption) return
    const [minX, minY, maxX, maxY] = crsOption.nativeBounds
    form.setValue(layerPath('nativeBoundingBox.minX'), String(minX), { shouldDirty: true, shouldValidate: true })
    form.setValue(layerPath('nativeBoundingBox.minY'), String(minY), { shouldDirty: true, shouldValidate: true })
    form.setValue(layerPath('nativeBoundingBox.maxX'), String(maxX), { shouldDirty: true, shouldValidate: true })
    form.setValue(layerPath('nativeBoundingBox.maxY'), String(maxY), { shouldDirty: true, shouldValidate: true })
  }

  const wideField = { className: 'grid-cols-[minmax(0,270px)_minmax(0,512px)]' }

  const nativeCRSLabel =
    crsOptions.find(o => o.value === selectedLayer?.nativeCRS)?.label ?? selectedLayer?.nativeCRS ?? ''
  const isNativeCrsMissing = !!tableWatch && !selectedLayer?.nativeCRS

  const styleOptions = styles.map(style => ({ value: style.id, label: style.name }))

  const tableOptions = postgisDataSinks?.map(dataSink => ({
    value: dataSink.id,
    label: dataSink.configuration.tableName,
  }))

  const currentDatastructureVersion = postgisDataSinks?.find(dataSink => dataSink.id === tableWatch)?.configuration
    .dataStructureVersion

  const umlClass = getUmlClass(postGisDatastructures, currentDatastructureVersion?.id)
  const attributeOptions = toAttributeOptions(umlClass)

  const layerErrors = form.formState.errors.layers
  const layerLabels = allLayers.map(layer => layer.title)
  const sidebarListLayers = allLayers.map((layer, index) => {
    const fallbackKey = isNewItem(layer) ? 'newLayer' : 'untitledLayer'
    const untitledIndex = !layer.title ? getEmptyLabelIndex(layerLabels, index) : 0
    return {
      label: layer.title,
      value: layer.id,
      displayTitle: layer.title || t(fallbackKey, { index: untitledIndex }),
      hasError: !!layerErrors?.[index],
    }
  })

  return (
    <>
      {allLayers.length === 0 ? (
        <>
          {!isReadOnly && (
            <div className="flex justify-end mb-3">
              <Button type="button" onClick={onAddLayer}>
                <Plus className="h-4 w-4 mr-2" />
                {t('addLayer')}
              </Button>
            </div>
          )}
          <NoDataCard
            icon={<List size={24} />}
            title={t('noData.title')}
            subTitle={t('noData.description')}
            isDisabled={isReadOnly}
          />
        </>
      ) : (
        <ContentCard>
          <DetailsFieldContainer className="pt-0 pb-3">
            <SubHeader title={t('sectionTitle')} titleClassName="text-2xl leading-none font-bold" />
          </DetailsFieldContainer>
          <div className="flex gap-6">
            <SidebarList
              items={sidebarListLayers}
              selectedItemIndex={selectedLayerIndex}
              isReadOnly={isReadOnly}
              addButtonLabel={t('addLayer')}
              addButtonTestId="sidebarAddLayerButton"
              onSelectItem={onSelectLayer}
              onAddItem={onAddLayer}
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
                    name={layerPath('title')}
                    placeholder={t('baseInfo.title')}
                    required
                    disabled={isReadOnly}
                    formItemProps={wideField}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0 py-2">
                  <TextField
                    form={form}
                    label={t('baseInfo.technicalName')}
                    name={layerPath('layerName')}
                    placeholder={t('baseInfo.technicalName')}
                    required
                    disabled={isReadOnly}
                    formItemProps={wideField}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0 py-2 pb-6">
                  <TextField
                    form={form}
                    label={t('baseInfo.description')}
                    name={layerPath('description')}
                    placeholder={t('baseInfo.description')}
                    disabled={isReadOnly}
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
                    name={layerPath('dataSinkId')}
                    options={tableOptions}
                    onChange={onTableChange}
                    formItemProps={wideField}
                    placeholder={t('dataSelection.tablePlaceHolder')}
                    required
                    disabled={isReadOnly}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0 py-2">
                  <FormComboboxMulti
                    form={form}
                    id="layerAttribute"
                    items={attributeOptions}
                    label={t('dataSelection.attributes')}
                    name={layerPath('attribute')}
                    placeholder={t('dataSelection.attributesPlaceHolder')}
                    required
                    hasSelectAllOption
                    disabled={isReadOnly}
                    formItemProps={wideField}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0 py-2 pb-6">
                  <TextField
                    form={form}
                    label={t('dataSelection.filter')}
                    name={layerPath('cqlFilter')}
                    placeholder={t('dataSelection.filter')}
                    disabled={isReadOnly}
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
                    name={layerPath('geometryColumnRef')}
                    options={attributeOptions}
                    placeholder={t('geometry.geometryFieldPlaceHolder')}
                    required
                    disabled={isReadOnly}
                    formItemProps={wideField}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0 py-2 flex flex-col gap-2">
                  <FormItem className={cn('grid', wideField.className)}>
                    <FormLabel>{t('geometry.nativeCrs')}</FormLabel>
                    <Input
                      value={nativeCRSLabel}
                      disabled
                      readOnly
                      className="disabled:opacity-100 disabled:border-transparent disabled:shadow-none disabled:h-9 disabled:py-0"
                    />
                  </FormItem>
                  {isNativeCrsMissing && <AlertBox text={t('geometry.nativeCrsMissingWarning')} />}
                </DetailsFieldContainer>

                <DetailsFieldContainer className="border-b-0 py-2 pb-6 flex flex-col gap-2">
                  <FormSelect
                    form={form}
                    label={t('geometry.definedCrs')}
                    name={layerPath('crs')}
                    id="owsLayerCrs"
                    options={crsOptions}
                    disabled={isReadOnly}
                    formItemProps={wideField}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0 py-2 pb-6">
                  <BoundingBoxConfig
                    form={form}
                    layerFieldIndex={selectedLayerIndex}
                    className="grid-cols-[minmax(0,270px)_minmax(0,512px)]"
                    isDisabled={isReadOnly}
                  />
                  <div className="grid grid-cols-[minmax(0,270px)_minmax(0,512px)] gap-2">
                    <div />
                    <div className="mt-2">
                      <Button
                        type="button"
                        size="sm"
                        disabled={!crsWatch || isReadOnly}
                        onClick={handleCalculateFromCrs}
                        className="text-xs h-7 px-2"
                      >
                        {t('geometry.calculateFromCrs')}
                      </Button>
                    </div>
                  </div>
                </DetailsFieldContainer>

                <DetailsFieldContainer isTitleField>
                  <SubHeader
                    title={t('style.sectionTitle')}
                    subtitle={t('style.sectionSubtitle')}
                    titleClassName="mb-1"
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0 py-2 pt-6">
                  <FormSelect
                    form={form}
                    id="defaultStyleId"
                    label={t('style.standardStyle')}
                    name={layerPath('defaultStyleId')}
                    options={styleOptions}
                    placeholder={t('style.default')}
                    disabled={isReadOnly}
                    formItemProps={wideField}
                  />
                </DetailsFieldContainer>
                <DetailsFieldContainer className="border-b-0 py-2 pb-6">
                  <FormComboboxMulti
                    form={form}
                    id="alternativeStyleIds"
                    items={styleOptions}
                    label={t('style.alternativeStyles')}
                    name={layerPath('alternativeStyleIds')}
                    placeholder={t('style.alternativeStylesPlaceHolder')}
                    disabled={isReadOnly}
                    formItemProps={wideField}
                  />
                </DetailsFieldContainer>
                {!isReadOnly && onDeleteLayer && (
                  <div className="flex justify-end pt-4 pb-2">
                    <Button type="button" variant="outline" size="sm" onClick={handleDeleteClick}>
                      <Trash2 className="h-4 w-4" />
                      {t('deleteLayer')}
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>
        </ContentCard>
      )}

      <WarningModal
        open={isDeleteModalOpen}
        onOpenChange={setIsDeleteModalOpen}
        title={t('deleteConfirm.title')}
        description={
          <>
            <span>{t('deleteConfirm.description')}</span>
            <ul className="mt-2 list-disc pl-5 space-y-1">
              <li>{t('deleteConfirm.impact1')}</li>
              <li>{t('deleteConfirm.impact2')}</li>
              <li>{t('deleteConfirm.impact3')}</li>
            </ul>
          </>
        }
        confirmButtonTitle={t('deleteConfirm.confirm')}
        onDiscard={() => setIsDeleteModalOpen(false)}
        onConfirm={handleConfirmDelete}
        isLoading={isDeleteLayerLoading}
      />
    </>
  )
}

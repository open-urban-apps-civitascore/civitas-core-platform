'use client'

import { Globe, List, Plus } from 'lucide-react'
import { useTranslations } from 'next-intl'
import React from 'react'
import { UseFormReturn } from 'react-hook-form'

import { InfoBox } from '@/components/text-box/TextBox'
import { Button } from '@/components/ui/button'
import { FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { Switch } from '@/components/ui/switch'
import { Datapool } from '@/types/datapools'
import { DATAPOOL_SCOPE_TYPES, DatasourceFormDraft } from '@/types/datasources'

import { DatapoolsStateCard } from './DatapoolsStateCard'
import { DatapoolsTable } from './DatapoolsTable'

type DatapoolsTabProps = {
  form: UseFormReturn<DatasourceFormDraft>
  isReadOnly?: boolean
  assignedDatapools: Datapool[]
  isLoadingDatapools?: boolean
  onDeleteDatapool: (id: string) => void
  onOpenAddModal: () => void
}

export const DatapoolsTab = (props: DatapoolsTabProps) => {
  const { form, isReadOnly, assignedDatapools, isLoadingDatapools, onDeleteDatapool, onOpenAddModal } = props
  const t = useTranslations('datasources.datapoolsTab')

  const scopeType = form.watch('datapoolScope.type') ?? DATAPOOL_SCOPE_TYPES.NONE

  const scopeControls = isReadOnly ? null : (
    <>
      <FormField
        control={form.control}
        name="datapoolScope"
        render={() => (
          <FormItem className="flex flex-row items-center gap-3 space-y-0">
            <FormControl>
              <Switch
                checked={scopeType === DATAPOOL_SCOPE_TYPES.ALL}
                onCheckedChange={isChecked => {
                  if (isChecked) {
                    form.setValue('datapoolScope', { type: DATAPOOL_SCOPE_TYPES.ALL }, { shouldDirty: true })
                  } else {
                    form.setValue(
                      'datapoolScope',
                      { type: DATAPOOL_SCOPE_TYPES.SPECIFIC, datapoolIds: [] },
                      { shouldDirty: true },
                    )
                  }
                }}
              />
            </FormControl>
            <FormLabel>{t('allDatapoolsSwitch')}</FormLabel>
          </FormItem>
        )}
      />
      <Button disabled={scopeType === DATAPOOL_SCOPE_TYPES.ALL} onClick={onOpenAddModal}>
        <Plus />
        {t('addDatapool')}
      </Button>
    </>
  )

  if (scopeType === DATAPOOL_SCOPE_TYPES.ALL)
    return (
      <div className="flex flex-col gap-4" data-testid="tab-datapools">
        {scopeControls && <div className="flex justify-end items-center gap-4">{scopeControls}</div>}
        <DatapoolsStateCard
          icon={Globe}
          title={t('allDatapoolsPage.title')}
          subTitle={t('allDatapoolsPage.description')}
        />
        {!isReadOnly && <InfoBox text={t('allDatapoolsInfo')} />}
      </div>
    )

  return (
    <div className="flex flex-col gap-4" data-testid="tab-datapools">
      {assignedDatapools.length === 0 && !isLoadingDatapools ? (
        <>
          <div className="flex justify-end items-center gap-4">{scopeControls}</div>
          <DatapoolsStateCard icon={List} title={t('noDataPage.title')} subTitle={t('noDataPage.description')} />
        </>
      ) : (
        <DatapoolsTable
          datapools={assignedDatapools}
          isLoading={!!isLoadingDatapools}
          onDeleteClick={isReadOnly ? undefined : onDeleteDatapool}
          tableAction={<div className="flex items-center gap-4">{scopeControls}</div>}
        />
      )}
      {!isReadOnly && <InfoBox text={t('allDatapoolsInfo')} />}
    </div>
  )
}

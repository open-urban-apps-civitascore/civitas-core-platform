import { Page } from '@playwright/test'

export const pickSelectOption = async (page: Page, fieldName: string, optionIndex = 0) => {
  const selectTrigger = page.getByTestId(`${fieldName}SelectTrigger`)
  await selectTrigger.click()

  const selectedOption = page.getByTestId(`${fieldName}SelectItem${optionIndex}`)
  await selectedOption.click()
}

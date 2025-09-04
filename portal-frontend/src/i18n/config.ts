import { defineRouting } from "next-intl/routing";
import { LOCALES } from "./locales";

export const routing = defineRouting({
  locales: LOCALES.map((locale) => locale.key),
  defaultLocale: "de",
});

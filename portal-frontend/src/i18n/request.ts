import { getRequestConfig } from "next-intl/server";
import { cookies } from "next/headers";
import { Locale } from "./locales";
import { routing } from "./routing";

export default getRequestConfig(async () => {
  // get locale from cookie or set default value
  const store = await cookies();
  const cookieLocale = store.get("NEXT_LOCALE")?.value as Locale;
  const locale =
    cookieLocale && routing.locales.includes(cookieLocale)
      ? cookieLocale
      : routing.defaultLocale;

  // for multiple message files:
  const [common, sidebar, languages] = await Promise.all([
    import(`../messages/${locale}/common.json`),
    import(`../messages/${locale}/sidebar.json`),
    import(`../messages/${locale}/languages.json`),
  ]);

  const messages = {
    common: common.default,
    sidebar: sidebar.default,
    languages: languages.default,
  };

  return {
    locale,
    messages,
  };
});

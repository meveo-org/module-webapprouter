# Internationalization

## Class diagram

```mermaid
classDiagram
class WebApplication {
    string code
    string ROOT_PATH
    list~I18NextLocale~ locales
    void importTranslationsFromCsv(File file)
    File generateTranslationsCsv()
    buildI18n()
}
class I18NextTranslation {
    string uuid
    list~I18NextLabel~ labels
    boolean generated
}
class I18NextLocale {
   string uuid
   string localeName
}
class I18NextLabel {
   string value
   I18NextTranslation translation
   I18NextLocale locale
}
class I18NextCft {
    string uuid
    string cftCode
    string templateCode
    I18NextTranslation translation
}
WebApplication --* I18NextLocale
I18NextLabel *-- I18NextTranslation
WebApplication --* I18NextTranslation
I18NextCft --> I18NextTranslation
I18NextLabel --> I18NextLocale
```

## Methods description

### generateTranslationsCsv

1/ For each CFT of the related module, create (only if the corresponding `I18NextCft` does not exist)
- a `I18NextLabel` instance for each locale and with value = the cft description
- a `I18NextTranslation` that has code = {templateCode}.{cftCode}, generated = `true` and that references the previous `I18NextLabel`
- a  `I18NextCft` that has cftCode = cft.code, templateCode=cft.appliesTo, and that references the previous `I18NextTranslation`
- add each of the generated CEIs to the related module

2/ Delete every generated `I18NextTranslation` that are not referenced by an `I18NextCft`

3/ Determine the columns of the CSV
- always the "code" column
- one column per locale of the `WebApplication`

4/ Write the CSV with the name "translations.csv" on the root of current git repository : iterate over all the `I18NextTranslation` and fill the columns of the CSV. The column headers should be included in the CSV.

### importTranslationsFromCsv

**For each line of the CSV :** 

1/ Retrieve or create the `I18NextTranslation` that corresponds to the first row

2/ For each remaining column, create or update the corresponding `I18NextLabel`

3/ Add everything to the related module

3/ Delete all `I18NextTranslation` that are not mentionned in the CSV file

### buildI18n

1/ Convert the data-model to XLIFF format (https://lit.dev/docs/localization/overview/#translation-with-xliff) : create / overwrite one file per locale, put them under /{ROOT_PATH}/locales/{localeUuid}

2/ Run `npm lit-localize build` from /{ROOT_PATH}

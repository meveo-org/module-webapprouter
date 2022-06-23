# Internationalization

## Class diagram

```mermaid
classDiagram
class WebApplication {
    string code
    void importTranslationsFromCsv(File file)
    File generateTranslationsCsv()
    list~I18NextLocale~ locales
}
class I18NextTranslation {
    string code
    list~I18NextLabel~ labels
}
class I18NextLocale {
   string locale
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
    boolean generated
    I18NextTranslation translation
}
WebApplication --* I18NextLocale
I18NextLabel *-- I18NextTranslation
WebApplication --* I18NextTranslation
I18NextCft --> I18NextTranslation
I18NextLabel --> I18NextLocale
```

## Methods description

*generateTranslationsCsv* :

1/ For each CFT of the related module, create (only if the corresponding `I18NextCft` does not exist)
- a `I18NextLabel` instance for each locale and with value = the cft description
- a `I18NextTranslation` that has code = {templateCode}.{cftCode}, generated = `true` and that references the previous `I18NextLabel`
- a  `I18NextCft` that has cftCode = cft.code, templateCode=cft.appliesTo, and that references the previous `I18NextTranslation`

2/ Remove every generated `I18NextTranslation` that are not referenced by an `I18NextCft`

3/ Determine the columns of the CSV
- always the "code" column
- one column per locale of the `WebApplication`

4/ Write the CSV : iterate over all the `I18NextTranslation` and fill the columns of the CSV

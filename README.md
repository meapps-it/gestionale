# Scadenze & Spese

Applicazione Android nativa offline per gestire scadenze, spese ricorrenti e voci miste.

## Stack

- Kotlin e Jetpack Compose
- Room per i dati locali
- WorkManager per i promemoria persistenti
- Storage Access Framework per backup e ripristino
- Material 3 con font di sistema Android

## Build

```bash
./gradlew assembleDebug
./gradlew bundleRelease
```

Gli artefatti vengono prodotti in `app/build/outputs/apk/debug` e
`app/build/outputs/bundle/release`. Codemagic esegue entrambe le build.

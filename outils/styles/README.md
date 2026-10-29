# Styles de carte

Les styles `app/src/main/assets/styles/clair.json` et `sombre.json` sont générés depuis [@protomaps/basemaps](https://github.com/protomaps/basemaps) 5.7.2 (style CC0), avec les noms de lieux en français :

```bash
cd outils/styles
npm install
npm run generer
```

Le script renomme les polices sans espaces (`Noto Sans Regular` → `NotoSans-Regular`) pour les lire dans les assets de l'appli.

## Polices et icônes intégrées

Copiées depuis [protomaps/basemaps-assets](https://github.com/protomaps/basemaps-assets), commit `028c18f713baecad011301ff7a69acc39bcc2ae7` :

- `fonts/` : Noto Sans Regular, Medium et Italic, plages 0-255, 256-511 et 8192-8447 (latin, latin étendu, ponctuation) — licence SIL Open Font License (`app/src/main/assets/fonts/OFL.txt`).
- `sprites/` : `sprites/v4/light*` et `dark*` — dérivés de [tangrams/icons](https://github.com/tangrams/icons) (MIT).

Un caractère hors de ces plages (alphabets non latins) ne s'affiche pas ; à élargir si la carte couvre d'autres pays.

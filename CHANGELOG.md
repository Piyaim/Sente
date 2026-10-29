# Journal des modifications

Toutes les modifications notables de Sente sont consignées ici. Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) ; numérotation [SemVer](https://semver.org/lang/fr/).

## [Non publié]

### Ajouté

- Structure du dépôt : README, licence AGPL-3.0-or-later, CONTRIBUTING, PRIVACY, DECISIONS, CHANGELOG.
- Application « coquille vide » en Kotlin et Jetpack Compose : un écran d'accueil, thème clair et sombre automatique, ni accès Internet ni accès à la position.
- Construction, signature et publication automatiques d'une préversion GitHub à chaque étiquette `v…`.
- Contrôle automatique : la construction échoue si une dépendance Google Play Services, Firebase ou Play apparaît.
- Guide de création de la clé de signature : [docs/signature.md](docs/signature.md).

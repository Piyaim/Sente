# Journal des modifications

Toutes les modifications notables de Sente sont consignées ici. Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) ; numérotation [SemVer](https://semver.org/lang/fr/).

## [Non publié]

### Ajouté

- Recherche hors ligne : adresses (Base Adresse Nationale), rues, communes et lieux (OpenStreetMap), au fil de la frappe, avec accents et abréviations tolérés (« st », « av », « bd »…).
- Catégories en un appui : boulangerie, carburant, recharge, parking, pharmacie, supermarché, restaurant, du plus proche au plus lointain.
- Fiche de lieu : distance, ouvert ou fermé selon les horaires OSM, horaires de la semaine, téléphone, site web, accès fauteuil, partage, lien « Corriger une erreur » vers OpenStreetMap.
- Repère doré du lieu choisi sur la carte.
- Favoris rangés en listes, domicile et travail, recherches récentes ; écran Favoris.
- « Mes données » : export JSON et effacement en un geste.
- Téléchargement de la recherche avec la carte (même vérification d'empreinte) et import manuel d'un fichier `.recherche.db`.
- Nouvelle identité visuelle validée sur maquettes : vert forêt et or, polices Atkinson Hyperlegible Next et Bricolage Grotesque intégrées, icônes au trait.

### Modifié

- Écran « Cartes et réglages » repensé ; la carte affiche la barre de recherche et les raccourcis Domicile, Travail, Favoris.

### Technique

- Bibliothèque d'icônes Material retirée (icônes dessinées dans l'appli).

## [0.1.0-pre1] - 2026-10-29

### Ajouté

- Carte hors ligne MapLibre lisant une région PMTiles, styles clair et sombre automatiques, noms en français, polices et icônes intégrées.
- Écran « Cartes hors ligne » : liste des régions disponibles (sur demande), téléchargement avec progression et vérification (taille, SHA-256, format), import d'un fichier `.pmtiles`, choix de la région affichée, suppression.
- Position GPS sur la carte (LocationManager), bouton pour centrer et suivre, bandeau « Recherche du signal GPS ».
- Réglage « Première position plus rapide » (fournisseur réseau d'Android), désactivé par défaut.
- Mention permanente « © OpenStreetMap » et fenêtre des sources de la carte.

### Technique

- Outil `outils/styles` pour régénérer les styles.
- Chaîne de construction : actions GitHub mises à jour, machine Ubuntu 24.04 figée.
- Notes de préversion plus lisibles : nouveautés, permissions en clair, vérification repliée.
- Kotlin 2.3.20.

## [0.0.1-pre1] - 2026-10-29

### Ajouté

- Structure du dépôt : README, licence AGPL-3.0-or-later, CONTRIBUTING, PRIVACY, DECISIONS, CHANGELOG.
- Application « coquille vide » en Kotlin et Jetpack Compose : un écran d'accueil, thème clair et sombre automatique, ni accès Internet ni accès à la position.
- Construction, signature et publication automatiques d'une préversion GitHub à chaque étiquette `v…`.
- Contrôle automatique : la construction échoue si une dépendance Google Play Services, Firebase ou Play apparaît.
- Guide de création de la clé de signature : [docs/signature.md](docs/signature.md).

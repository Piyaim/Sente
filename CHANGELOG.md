# Journal des modifications

Toutes les modifications notables de Sente sont consignées ici. Format inspiré de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) ; numérotation [SemVer](https://semver.org/lang/fr/).

## [0.3.1-pre1] - 2026-10-30

### Ajouté

- Aperçu du trajet : trajet proposé et variante sans péage, étiquettes « Péages » et routes empruntées, options « Éviter les péages » et « Éviter les autoroutes », bouton Démarrer.
- Guidage pas à pas : bandeau de la prochaine manœuvre et de la suivante, distance, heure d'arrivée, vitesse, carte qui suit la voiture, bouton Recentrer.
- Voix en français (moteur de synthèse vocale d'Android), bouton pour la couper ; la musique baisse pendant les annonces.
- Recalcul automatique en cas d'écart, sur le téléphone.
- Le guidage continue quand Sente n'est plus à l'écran, avec une notification neutre (« Guidage en cours », bouton Arrêter), masquée sur l'écran verrouillé. L'écran reste allumé pendant le guidage ; fermer Sente depuis les applis récentes arrête le guidage.
- Écran d'arrivée, avec ajout aux favoris.
- Essai : « simuler le trajet », pour tester le guidage sans rouler.

### Modifié

- La bande provisoire de l'essai 3a est remplacée par l'aperçu du trajet.

### Technique

- Ajout du cœur de Ferrostar 0.59.0 (BSD-3) ; ses positions viennent uniquement du GPS par le LocationManager.
- Nouvelles permissions : service de premier plan (position) et notifications.

## [0.3.0-pre1] - 2026-10-30

### Ajouté

- Essai d'itinéraire en voiture, calculé entièrement sur le téléphone (Valhalla) : le bouton Itinéraire d'une fiche ou d'une catégorie trace le trajet depuis votre position, avec durée, distance, heure d'arrivée et temps de calcul. Le guidage viendra ensuite.
- Téléchargement de l'itinéraire d'une région (« Ajouter l'itinéraire », empreinte vérifiée) et import manuel d'un fichier `.routage.tar`.

### Technique

- Ajout de valhalla-mobile 0.6.4 (MIT), qui embarque Valhalla 3.9.1. Code natif pour les processeurs 64 bits seulement.
- Construction avec Java 21, exigé par valhalla-mobile.

## [0.2.1-pre1] - 2026-10-30

### Ajouté

- Catégories sur la carte : toucher « Boulangerie » (ou une autre catégorie) place un point par résultat. Une bande en bas montre le lieu mis en avant (distance, ouvert ou fermé, Itinéraire, Fiche) ; les flèches ou un appui sur un point passent à un autre ; « Liste » ramène la liste.
- Fiche sans horaires : « Horaires non renseignés » et lien « Les ajouter sur OpenStreetMap ».

### Corrigé

- Recherche d'adresse : le numéro est aussi accepté après la rue (« place bellecour 20 »).
- Abréviations pendant la frappe : « avenue du pl » trouve de nouveau « avenue du Plateau » (le mot en cours n'est plus remplacé par « place »).

## [0.2.0-pre1] - 2026-10-29

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

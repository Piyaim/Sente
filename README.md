# Sente

**Sente** est une application de navigation GPS pour Android, libre et respectueuse de la vie privée. Elle vise le meilleur de Google Maps (carte, recherche, fiches de lieux) et de Waze (guidage voiture, signalements, alertes), sans aucune collecte de données et sans aucun service Google.

> **État actuel : lot 1 en test.** Carte hors ligne d'une région et position GPS. Pas encore de recherche ni d'itinéraire : ne l'utilisez pas pour naviguer.

## Principes

- **Aucune collecte** : pas de télémétrie, pas d'analytics, pas de publicité, pas de rapport de plantage automatique, pas de compte.
- **Aucun service Google** : ni Google Play Services, ni Firebase, ni Google Maps SDK. Position via le `LocationManager` d'Android.
- **Hors ligne** : carte, recherche d'adresse, calcul d'itinéraire et guidage fonctionnent sans réseau, une fois la région téléchargée.
- **Vos données restent sur le téléphone** : favoris, historique et trajets sont locaux, effaçables en un geste et exportables.
- **Transparence** : tout ce qui sort du téléphone est décrit dans [PRIVACY.md](PRIVACY.md).

## Deux modes, au choix

| | Local uniquement (par défaut) | Services auto-hébergés |
| --- | --- | --- |
| Carte, recherche, itinéraire, guidage | Hors ligne | Hors ligne |
| Signalements communautaires | Désactivés | Via **votre** serveur |
| Données de trafic Bison Futé | Désactivées | Relayées par **votre** serveur |
| Ce qui sort du téléphone | Rien, hors téléchargement des régions | Uniquement vers l'adresse que vous saisissez |

## Feuille de route

| Lot | Contenu |
| --- | --- |
| 0 bis | Structure du dépôt, préversion signée « coquille vide » |
| 1 | Carte hors ligne d'une région, téléchargement, position GPS, thème clair/sombre |
| 2 | Recherche hors ligne, fiches de lieux, favoris, historique |
| 3 | Itinéraire voiture embarqué, guidage vocal, recalcul |
| 4 | Péages portion par portion, zones à éviter, itinéraires alternatifs |
| 5 | Limites de vitesse et alerte de dépassement, voies conseillées |
| 6 | Signalements communautaires et serveur auto-hébergeable |
| 7 | Vélo, piéton, Android Auto (désactivé par défaut) |
| 8 | Transports en commun, vues de rue Panoramax, trafic Bison Futé |

Les choix techniques et leurs raisons sont tenus dans [DECISIONS.md](DECISIONS.md), les nouveautés de chaque version dans [CHANGELOG.md](CHANGELOG.md).

## Installer une préversion

1. Ouvrez la page [Releases](../../releases) du dépôt et prenez la préversion la plus récente.
2. Téléchargez le fichier `sente-….apk` sur votre téléphone.
3. Ouvrez-le et autorisez l'installation depuis votre navigateur ou gestionnaire de fichiers si Android le demande.
4. Facultatif : dépliez « Vérifier le fichier » dans les notes de la préversion et comparez l'empreinte du certificat avec celle de la clé du projet.

## Technologies

Kotlin, Jetpack Compose (Material 3), MapLibre Native (carte), tuiles PMTiles issues d'OpenStreetMap. Prévu : Valhalla embarqué (itinéraires), Ferrostar (guidage).

## Licences et attributions

- Code de l'application : [GNU AGPL v3 ou ultérieure](LICENSE).
- Données cartographiques : © les contributeurs d'[OpenStreetMap](https://www.openstreetmap.org/copyright), sous licence ODbL ; tuiles et style d'après [Protomaps](https://protomaps.com) (style CC0).
- Polices Noto Sans : SIL Open Font License. Icônes de carte : dérivées de tangrams/icons (MIT).
- Cartes téléchargeables : dépôt [Piyaim/Sente-cartes](https://github.com/Piyaim/Sente-cartes).

## Contribuer

Voir [CONTRIBUTING.md](CONTRIBUTING.md).

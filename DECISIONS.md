# Journal des décisions

Chaque décision importante, avec sa date et sa raison. Les plus récentes en haut.

| Date | Décision | Raison |
| --- | --- | --- |
| 2026-10-29 | Identifiant d'application `io.github.piyaim.sente` | Convention des projets hébergés sur GitHub, acceptée par F-Droid, indépendante d'un nom de domaine |
| 2026-10-29 | Android 8.0 minimum (minSdk 26), cible Android 16 (targetSdk 36) | Couvre la quasi-totalité des téléphones en service ; icônes adaptatives natives |
| 2026-10-29 | Versions : Android Gradle Plugin 8.13.0, Gradle 8.14.3, Kotlin 2.2.0, JDK 17 | Combinaison stable et documentée ; AGP 9 change le DSL, migration reportée à un lot ultérieur |
| 2026-10-29 | Publication des préversions par la commande `gh` intégrée aux machines GitHub, sans action tierce | Moins de dépendances à faire confiance dans la chaîne de construction |
| 2026-10-29 | Construction refusée si une dépendance Google Play Services, Firebase ou Play apparaît | Garantie automatique de l'engagement « aucun service Google » |
| 2026-10-29 | Bloc d'informations de dépendances désactivé dans l'APK (`dependenciesInfo`) | Ce bloc chiffré pour Google est refusé par F-Droid et inutile ici |
| 2026-10-29 | Tests sur Honor Magic 7 Pro, via les préversions GitHub construites et signées par GitHub Actions | Aucun APK construit localement ; un bug trouvé est corrigé dans la version suivante |
| 2026-10-29 | GrapheneOS n'est plus une plateforme cible prioritaire | N'est plus utilisé ; l'application reste compatible avec tout Android sans Google |
| 2026-10-29 | Deux modes : « local uniquement » (par défaut) et « services auto-hébergés » | Laisser le choix entre zéro échange réseau et des services communautaires sur un serveur que l'on contrôle |
| 2026-10-29 | Découpage en 8 lots | La recherche sort du lot 1 pour garder un premier lot testable vite ; les éléments risqués arrivent sur une base stable |
| 2026-10-29 | Pas de trafic en temps réel ; seulement les données Bison Futé des DIR sous Licence Ouverte (gratuites, sans compte) | Un trafic façon Waze exige la collecte des positions ; les données « action b » des autoroutes concédées demandent une licence signée prévoyant des données payantes |
| 2026-10-29 | Limites de vitesse (OSM) avec alerte de dépassement ; aucun signalement de contrôle de police ni position de radar | Avertisseurs de radars interdits (décret 2012-3) ; article L. 130-11 du code de la route sur la rediffusion des contrôles |
| 2026-10-29 | Régions téléchargeables hébergées dans les versions GitHub d'un dépôt dédié | N'expose pas la connexion à domicile ; gratuit ; adresse modifiable |
| 2026-10-29 | Itinéraires calculés sur le téléphone par Valhalla ; serveur GraphHopper personnel en option de test | Fonctionnement hors ligne ; le support Android de GraphHopper embarqué est abandonné |
| 2026-10-29 | Partir de zéro sur des briques libres (MapLibre, PMTiles, Valhalla, Ferrostar) plutôt que forker ou contribuer à CoMaps | Aucun projet libre n'a les péages fins ni les signalements ; un fork de CoMaps coûterait trop cher à maintenir |
| 2026-10-29 | Nom : Sente | Court, français (petit sentier), sans homonyme repéré parmi les applis de navigation |
| 2026-10-29 | Licence AGPL-3.0-or-later | Le code, y compris celui du serveur de signalements, reste libre même utilisé en service réseau |

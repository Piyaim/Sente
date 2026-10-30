# Journal des décisions

Chaque décision importante, avec sa date et sa raison. Les plus récentes en haut.

| Date | Décision | Raison |
| --- | --- | --- |
| 2026-10-30 | Petite fenêtre de guidage par l'incrustation d'image d'Android (choix A), pas par la superposition | Aucune permission ; ouverture seulement pendant un guidage ; désactivable dans les réglages Android |
| 2026-10-30 | Favori renommé : le nom d'origine est gardé à part et sert toujours à reconnaître le lieu | L'étoile reste allumée quand on retrouve le lieu par la recherche |
| 2026-10-30 | Guidage en arrière-plan avec service de premier plan et notification (choix B) | Le guidage et la voix continuent quand on ouvre une autre appli ; indispensable aussi pour Android Auto (lot 7) |
| 2026-10-30 | Notification de guidage neutre (« Guidage en cours »), masquée sur l'écran verrouillé | Son texte est lisible sur l'écran verrouillé et par les applis ayant l'accès aux notifications (montres…) : aucune rue ni destination |
| 2026-10-30 | Ferrostar reçoit les positions de notre source GPS, pas de sa source Android | Sa source choisit le fournisseur « fused », qui passe par Google sur la plupart des téléphones |
| 2026-10-30 | Variante proposée : la même route en évitant les péages, plutôt que les « alternatives » de Valhalla | Utile et rapide (0,2 s) ; les alternatives de Valhalla en trouvent rarement sur nos tests |
| 2026-10-30 | Bouton « simuler le trajet » dans l'aperçu, pendant les préversions | Tester guidage, voix et arrivée sans rouler |
| 2026-10-30 | Profils à ajouter après le lot 3 : moto, à pied, à vélo | Demandés ; même moteur Valhalla, réglages différents, une fois la voiture validée de bout en bout |
| 2026-10-30 | Itinéraire : valhalla-mobile 0.6.4 (Valhalla 3.9.1) ; tuiles construites dans Sente-cartes avec la même version | Le format des tuiles change entre versions de Valhalla ; calcul entièrement hors ligne, licence MIT |
| 2026-10-30 | Guidage (lot 3c) : cœur de Ferrostar, écrans et voix de Sente | Logique de guidage éprouvée, sans remplacer le design validé ; module Google de Ferrostar exclu |
| 2026-10-30 | Voiture seule au lot 3 ; moto ensuite | Valider d'abord toute la chaîne avec un profil |
| 2026-10-30 | Code natif pour processeurs 64 bits seulement (`arm64-v8a`) | Appli plus légère ; couvre pratiquement tous les téléphones depuis 2017 |
| 2026-10-30 | Réglages de Valhalla écrits en JSON par Sente, à partir de ceux fournis avec valhalla-mobile, et tout le paquet gardé par R8 | Évite les erreurs de réflexion après réduction du code |
| 2026-10-30 | Catégories affichées directement sur la carte, liste à un appui | Choix 2A sur maquette validée, comme les applis de cartes courantes |
| 2026-10-30 | Horaires manquants : lien vers l'éditeur d'openstreetmap.org, pas de saisie dans Sente | Aucun compte ni envoi à gérer dans l'appli ; l'ajout profite à tout le monde |
| 2026-10-30 | Numéro accepté en fin de texte (1 à 4 chiffres) ; 5 chiffres en fin restent un code postal | « avenue du plateau 69009 » ne doit pas chercher le numéro 69009 |
| 2026-10-29 | Le bouton Itinéraire est visible mais annonce l'arrivée du calcul au lot 3 | Fidèle aux maquettes validées, sans promettre une fonction absente |
| 2026-10-29 | Favoris, listes, domicile, travail et historique dans une base SQLite privée (`perso.db`), sans la bibliothèque Room | Moins de dépendances et d'outils de compilation ; export JSON et effacement en un geste |
| 2026-10-29 | Les favoris gardent une copie complète du lieu | Ils restent valables quand l'index de recherche est remplacé |
| 2026-10-29 | Horaires OSM : lecture des formes courantes seulement (jours, plages, « off », « 24/7 ») ; sinon texte brut, sans état ouvert/fermé | Mieux vaut ne rien affirmer qu'afficher « ouvert » à tort |
| 2026-10-29 | Index SQLite par région (`<region>.recherche.db`) : rues et communes en FTS4 sans contenu, numéros stockés en écart au centre de la rue, lieux OSM filtrés par catégorie | FTS4 est présent sur tous les Android visés (FTS5 ne l'est pas) ; fichier compact |
| 2026-10-29 | Texte normalisé à l'identique dans le script Python et dans l'appli (minuscules, sans accents ni ponctuation) | Indépendant des réglages de SQLite du téléphone |
| 2026-10-29 | Adresses : Base Adresse Nationale ; lieux : OpenStreetMap (extraits Geofabrik) ; index publié sous ODbL | Les numéros sont bien plus complets dans la BAN (Licence Ouverte 2.0, compatible) |
| 2026-10-29 | Identité visuelle validée : vert forêt `#1F4D3A`, or `#E9C46A` réservé au lieu choisi, Atkinson Hyperlegible Next et Bricolage Grotesque intégrées (OFL), formes Material 3 Expressive | Lisibilité d'abord, y compris en conduite ; aucune police téléchargée |
| 2026-10-29 | Carte d'Auvergne-Rhône-Alpes : 631 Mo (rectangle, zoom 14) | Mesure réelle ; pour la France, garder un fichier par région et passer au contour exact |
| 2026-10-29 | Écrans du lot 1 fonctionnels et provisoires ; design Material 3 Expressive avec maquettes à valider avant les écrans de conduite (lot 3) | Tester d'abord la carte hors ligne ; ne pas figer un design sans maquette |
| 2026-10-29 | Une seule région affichée à la fois (choix dans « Cartes hors ligne ») | Le style Protomaps lit une seule source ; plusieurs régions simultanées demandent une autre organisation des fichiers, à décider avant d'ajouter d'autres régions |
| 2026-10-29 | Permission « état du Wi-Fi » ajoutée par MapLibre retirée du manifeste | Inutilisée par MapLibre (vérifié dans son code source 13.7.1) |
| 2026-10-29 | En-tête `User-Agent: Sente` pour le catalogue et les téléchargements | L'en-tête par défaut d'Android contient le modèle du téléphone |
| 2026-10-29 | Aucune requête réseau automatique : le catalogue n'est lu que sur action de l'utilisateur | Mode local par défaut : rien ne sort du téléphone sans geste explicite |
| 2026-10-29 | Import manuel d'un fichier `.pmtiles` (vérification de l'en-tête PMTiles v3) | Seul moyen d'installer une carte sans aucun échange réseau |
| 2026-10-29 | Position par GPS seul par défaut ; fournisseur « réseau » en option désactivée | Sur un téléphone avec services Google, ce fournisseur envoie Wi-Fi et antennes à Google |
| 2026-10-29 | Position fournie à MapLibre par notre propre lecteur `LocationManager` (`useDefaultLocationEngine(false)`) | Contrôle total de la source : jamais de fournisseur « fused » |
| 2026-10-29 | Polices Noto Sans (3 plages latines) et icônes Protomaps intégrées à l'APK ; style Protomaps 5.7.2 généré en français par `outils/styles` | Sans elles, la carte hors ligne n'aurait ni noms ni icônes ; génération reproductible |
| 2026-10-29 | Régions vérifiées après téléchargement : taille, empreinte SHA-256 du catalogue, en-tête PMTiles | Un fichier abîmé ou incomplet n'est jamais affiché |
| 2026-10-29 | Téléchargement par le gestionnaire de téléchargements d'Android | Continue écran éteint et reprend après une coupure, sans service maison |
| 2026-10-29 | Cartes : dépôt `Piyaim/Sente-cartes`, version fixe « regions » (fichiers + `catalogue.json`), découpe rectangulaire de la carte quotidienne Protomaps, zoom maximal 14 | Adresse stable, préversions de l'appli lisibles, fichiers jusqu'à 2 Go ; MapLibre agrandit au-delà du zoom 14 |
| 2026-10-29 | MapLibre Android 13.7.1 ; Kotlin 2.3.20 (aligné sur MapLibre) | Dernière version stable, lecture PMTiles native |
| 2026-10-29 | Chaîne de construction : actions checkout v7, setup-java v6, setup-gradle v6 ; machine `ubuntu-24.04` figée | Versions précédentes dépréciées ; éviter une casse surprise lors du passage d'`ubuntu-latest` à Ubuntu 26 |
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

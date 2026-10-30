# Vie privée : ce qui sort du téléphone

Ce document dit **exactement** ce que Sente envoie, à qui, et pourquoi. Il distingue ce qui existe **aujourd'hui** de ce qui est **prévu**. Il est mis à jour à chaque version qui change un échange réseau ou une permission.

## Version actuelle (préversions 0.3.x : carte, recherche, favoris et essai d'itinéraire hors ligne)

### Permissions Android

| Permission | Pourquoi | Quand |
| --- | --- | --- |
| Internet | Télécharger la liste des régions et une région | Uniquement quand vous appuyez sur « Voir les régions disponibles » puis « Télécharger » |
| Position précise et approximative | Afficher votre position grâce au GPS | Uniquement quand l'appli est à l'écran ; jamais en arrière-plan |
| État du réseau | Exigée par la bibliothèque de carte MapLibre pour savoir si le réseau est disponible | Ne transmet rien |
| Permission interne AndroidX | Réservée à l'appli elle-même | Ne donne accès à rien |

La permission « état du Wi-Fi », ajoutée par MapLibre, est retirée. Les notes de chaque préversion affichent la liste exacte.

### Connexions réseau

Aucune connexion au démarrage ni en arrière-plan. Seulement, à votre demande :

- **Liste des régions** : `catalogue.json` sur `github.com` (dépôt Piyaim/Sente-cartes), redirigé par GitHub vers ses serveurs de fichiers (`*.githubusercontent.com`).
- **Fichiers d'une région** (carte, recherche, itinéraire) : même origine, téléchargés par le gestionnaire de téléchargements d'Android (qui affiche une notification de progression).
- Dans les deux cas, Sente envoie l'en-tête `User-Agent: Sente` à la place de celui d'Android, qui contiendrait le modèle du téléphone. GitHub voit votre adresse IP et l'heure.
- **Sans aucun réseau** : copiez les fichiers `.pmtiles`, `.recherche.db` et `.routage.tar` sur le téléphone et utilisez « Importer un fichier ».

La carte (MapLibre), la recherche et le calcul d'itinéraire (Valhalla) ne contactent aucun serveur : style, polices, icônes, carte, index de recherche et tuiles d'itinéraire sont lus sur le téléphone. Ce que vous tapez dans la recherche, votre point de départ et votre destination ne sortent pas du téléphone.

### Quand vous quittez Sente

Cinq boutons ou liens de la fiche d'un lieu ouvrent une autre appli, seulement quand vous les touchez :

- **Appeler** ouvre le clavier du téléphone avec le numéro ;
- **Site web** ouvre le site du lieu dans votre navigateur ;
- **Corriger une erreur** ouvre la page du lieu sur openstreetmap.org dans votre navigateur ;
- **Les ajouter sur OpenStreetMap** (horaires manquants) ouvre l'éditeur d'openstreetmap.org sur ce lieu, dans votre navigateur ; il faut un compte OpenStreetMap pour enregistrer ;
- **Partager** envoie le nom, l'adresse et la position du lieu à l'appli que vous choisissez.

Ce que fait ensuite cette autre appli ne dépend plus de Sente.

### Position

- Obtenue par le GPS via le `LocationManager` d'Android ; jamais par le Fused Location Provider de Google.
- Ni enregistrée, ni envoyée : elle sert uniquement à placer le point sur la carte et de point de départ au calcul d'itinéraire, fait sur le téléphone.
- Option « Première position plus rapide », **désactivée par défaut** : elle ajoute le fournisseur « réseau » d'Android. Sur un téléphone avec les services Google, c'est le système (pas Sente) qui envoie alors à Google les réseaux Wi-Fi et antennes environnants.

### Données enregistrées sur le téléphone

- Les cartes, index de recherche et tuiles d'itinéraire installés, la région affichée, l'option de position et, pendant un téléchargement, sa référence.
- Vos favoris et leurs listes, domicile et travail, et vos 30 dernières recherches ouvertes (le lieu choisi, pas ce que vous avez tapé), dans une base privée à l'appli.
- Rien de cela n'est envoyé. « Cartes et réglages » → « Mes données » permet de tout exporter en JSON ou de tout effacer d'un geste. Tout disparaît aussi à la désinstallation ; la sauvegarde automatique d'Android est désactivée pour Sente.

## Engagements permanents

- Aucune télémétrie, aucune mesure d'audience, aucune publicité, aucun rapport de plantage envoyé automatiquement.
- Aucun compte, aucun identifiant persistant attribué à l'appareil ou à l'utilisateur.
- Aucune bibliothèque Google Play Services, Firebase, Crashlytics ou Google Maps SDK. La compilation échoue si l'une d'elles apparaît dans les dépendances.
- Position obtenue par le `LocationManager` d'Android (GPS en priorité), jamais par le Fused Location Provider de Google.
- Favoris, historique et trajets restent sur le téléphone, effaçables en un geste et exportables.
- Chaque version est contrôlée : dépendances, permissions et domaines contactés, et vérification Exodus Privacy (aucun traqueur toléré).

## Prévu dans les prochaines versions

### Mode « local uniquement » (mode par défaut)

Rien d'autre ne sort du téléphone. Signalements et trafic sont désactivés et masqués.

### Mode « services auto-hébergés »

L'utilisateur saisit l'adresse de **son** serveur. Sente ne contacte que cette adresse, affichée en clair.

- **Signalements (lot 6)** : quand vous envoyez un signalement, le serveur reçoit son type (accident, bouchon, objet sur la route, travaux, véhicule arrêté, danger), sa position et l'heure. Quand vous consultez les signalements, le serveur reçoit la zone affichée. Aucun trajet n'est envoyé ni stocké. Les signalements expirent seuls.
- **Adresse IP** : le serveur voit forcément l'adresse IP de qui envoie ou consulte un signalement. Le serveur de référence fourni par le projet **ne journalise pas** les adresses IP. Pour limiter les abus, il garde en mémoire vive seulement, pendant une durée courte, une empreinte de l'adresse IP calculée avec un sel renouvelé chaque jour.
- **Trafic (lot 8)** : le serveur récupère les données ouvertes de Bison Futé ; le téléphone ne contacte que votre serveur, jamais directement l'État.
- **Serveur d'itinéraire facultatif** : si vous activez un serveur GraphHopper personnel, votre point de départ et votre destination lui sont envoyés. Désactivé par défaut.

### Services tiers installés à part

- **Synthèse vocale** : Sente utilise le moteur de synthèse vocale choisi dans les réglages d'Android. Ce moteur est une application séparée, avec sa propre politique. SherpaTTS, moteur libre conseillé, télécharge une fois sa voix depuis Hugging Face puis fonctionne hors ligne.
- **Android Auto (lot 7)** : Android Auto est une application Google. Son utilisation passe par un interrupteur désactivé par défaut.

## Contrôles et réclamations

Une question ou un écart constaté entre ce document et le comportement de l'application : ouvrez un ticket sur le dépôt GitHub.

# Vie privée : ce qui sort du téléphone

Ce document dit **exactement** ce que Sente envoie, à qui, et pourquoi. Il distingue ce qui existe **aujourd'hui** de ce qui est **prévu**. Il est mis à jour à chaque version qui change un échange réseau ou une permission.

## Version actuelle (préversions 0.3.x : carte, recherche, favoris, itinéraire et guidage hors ligne)

### Permissions Android

| Permission | Pourquoi | Quand |
| --- | --- | --- |
| Internet | Télécharger la liste des régions et une région | Uniquement quand vous appuyez sur « Voir les régions disponibles » puis « Télécharger » |
| Position précise et approximative | Afficher votre position et vous guider grâce au GPS | Quand l'appli est à l'écran ; en arrière-plan **uniquement pendant un guidage**, avec une notification visible |
| Service de premier plan (position) | Garder le guidage et la voix actifs quand vous passez à une autre appli | Seulement entre « Démarrer » et l'arrivée ou « Arrêter » |
| Notifications | Afficher « Guidage en cours » et un bouton Arrêter pendant un guidage | Demandée au premier guidage ; un refus n'empêche pas de guider |
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
- Ni enregistrée, ni envoyée : elle sert uniquement à placer le point sur la carte, de point de départ au calcul d'itinéraire et au guidage, faits sur le téléphone. Aucun historique de trajets n'est gardé.
- Pendant un guidage, elle est lue directement auprès du GPS, y compris quand Sente n'est plus à l'écran. Le guidage (Ferrostar) ne reçoit que ces positions GPS : le fournisseur « fused » d'Android, qui passe par Google sur la plupart des téléphones, n'est pas utilisé.
- Les autres applis ne peuvent pas lire la position de Sente. La notification de guidage reste neutre : « Guidage en cours », sans rue, destination, heure d'arrivée ni coordonnées.
- **Hors de Sente** : sur un téléphone avec les services Google (ou ceux du fabricant), le système peut collecter la position de son côté si vous l'avez autorisé (« Précision de la localisation », historique des positions). Ces réglages sont dans Paramètres → Localisation ; le GPS seul suffit à Sente.
- Option « Première position plus rapide », **désactivée par défaut** : elle ajoute le fournisseur « réseau » d'Android. Sur un téléphone avec les services Google, c'est le système (pas Sente) qui envoie alors à Google les réseaux Wi-Fi et antennes environnants.

### Voix du guidage

Sente transmet le texte des consignes (par exemple « Tournez à droite dans le cours Jean-Jaurès ») au moteur de synthèse vocale choisi dans les réglages d'Android. Ce moteur est une application séparée, avec sa propre politique : celui de Google peut, selon ses réglages, utiliser des voix en ligne et recevoir ce texte. SherpaTTS, moteur libre, fonctionne entièrement hors ligne après avoir téléchargé sa voix une fois. Le bouton haut-parleur coupe la voix.

### Petite fenêtre flottante

Pendant un guidage, quitter Sente ouvre une petite fenêtre (incrustation d'image d'Android) qui montre la prochaine manœuvre, un morceau de carte et l'heure d'arrivée par-dessus les autres applis. Elle est visible par les personnes qui regardent l'écran, comme le guidage en plein écran. Aucune autre appli ne peut en lire le contenu. Elle se désactive dans les réglages Android de Sente (« Incrustation d'image »).

### Écran verrouillé

La notification de guidage est masquée sur l'écran verrouillé. Même déverrouillée, elle ne dit rien du trajet : c'est voulu, car les applis qui ont l'accès aux notifications (celle d'une montre connectée, par exemple) peuvent lire leur texte.

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

- **Android Auto (lot 7)** : Android Auto est une application Google. Son utilisation passe par un interrupteur désactivé par défaut.

## Contrôles et réclamations

Une question ou un écart constaté entre ce document et le comportement de l'application : ouvrez un ticket sur le dépôt GitHub.

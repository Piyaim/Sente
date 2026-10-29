# Vie privée : ce qui sort du téléphone

Ce document dit **exactement** ce que Sente envoie, à qui, et pourquoi. Il distingue ce qui existe **aujourd'hui** de ce qui est **prévu**. Il est mis à jour à chaque version qui change un échange réseau ou une permission.

## Version actuelle (préversion 0.0.x, « coquille vide »)

- **Permissions Android : aucune donnant accès à vos données ou au réseau.** Ni Internet, ni position. La seule entrée possible dans la liste est une permission interne ajoutée par la bibliothèque AndroidX, réservée à l'application elle-même ; les notes de chaque préversion affichent la liste exacte.
- **Connexions réseau : aucune.**
- **Données enregistrées : aucune.**

## Engagements permanents

- Aucune télémétrie, aucune mesure d'audience, aucune publicité, aucun rapport de plantage envoyé automatiquement.
- Aucun compte, aucun identifiant persistant attribué à l'appareil ou à l'utilisateur.
- Aucune bibliothèque Google Play Services, Firebase, Crashlytics ou Google Maps SDK. La compilation échoue si l'une d'elles apparaît dans les dépendances.
- Position obtenue par le `LocationManager` d'Android (GPS en priorité), jamais par le Fused Location Provider de Google.
- Favoris, historique et trajets restent sur le téléphone, effaçables en un geste et exportables.
- Chaque version est contrôlée : dépendances, permissions et domaines contactés, et vérification Exodus Privacy (aucun traqueur toléré).

## Prévu dans les prochaines versions

### Dans les deux modes : téléchargement des régions (à partir du lot 1)

- **Destination** : les versions (Releases) GitHub d'un dépôt dédié aux cartes du projet. L'adresse est affichée dans les réglages.
- **Ce qui est envoyé** : une requête de téléchargement du fichier de la région choisie.
- **Ce que voit GitHub** : votre adresse IP, l'heure, le fichier demandé et les informations techniques ordinaires d'une requête web. GitHub applique sa propre politique de confidentialité ; Sente n'y ajoute aucun identifiant.
- **Pour n'avoir aucun échange réseau** : une région pourra être importée à la main depuis un fichier.

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

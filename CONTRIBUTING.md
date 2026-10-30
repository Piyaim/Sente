# Contribuer à Sente

Merci de votre intérêt. Sente est un projet personnel ouvert : les contributions sont bienvenues tant qu'elles respectent les principes ci-dessous.

## Principes non négociables

1. **Aucune collecte de données** : pas de télémétrie, d'analytics, de publicité ni de rapport de plantage automatique.
2. **Aucun service Google** : ni Google Play Services, ni Firebase, ni Crashlytics, ni Google Maps SDK. La construction échoue automatiquement si l'une de ces dépendances apparaît.
3. **Position par le `LocationManager` d'Android**, jamais par le Fused Location Provider.
4. **Hors ligne d'abord** : toute fonction qui utilise le réseau doit être facultative, documentée dans [PRIVACY.md](PRIVACY.md), et ne contacter qu'une adresse affichée et modifiable par l'utilisateur.
5. **Licences compatibles** : le code est sous AGPL-3.0-or-later. Toute dépendance ou donnée ajoutée doit avoir une licence compatible, indiquée dans la demande de fusion.

## Signaler un problème

Ouvrez un ticket (« Issue ») en indiquant : version de Sente, modèle de téléphone, version d'Android, étapes pour reproduire, comportement attendu et constaté.

## Proposer une modification

1. Ouvrez d'abord un ticket pour discuter des changements importants.
2. Une demande de fusion (« Pull Request ») = un sujet.
3. Décrivez ce qui change pour l'utilisateur et, le cas échéant, ce qui change dans les échanges réseau ou les permissions.
4. Mettez à jour [CHANGELOG.md](CHANGELOG.md) (rubrique « Non publié ») et, pour un choix structurant, [DECISIONS.md](DECISIONS.md).

## Contrôle avant chaque version

- [ ] Liste des dépendances relue (jointe à chaque préversion : `dependances.txt`).
- [ ] Permissions Android relues (affichées dans les notes de la préversion).
- [ ] Domaines contactés vérifiés et conformes à PRIVACY.md.
- [ ] Vérification Exodus Privacy : application Exodus installée sur le téléphone, analyse de Sente, aucun traqueur.
- [ ] CHANGELOG.md à jour.

## Construction

Les APK officiels sont construits et signés uniquement par GitHub Actions (fichier `.github/workflows/preversion.yml`). Pour construire localement une version de développement non signée : JDK 21 et SDK Android requis, puis `./gradlew :app:assembleDebug`.

## Licence

En contribuant, vous acceptez que votre contribution soit publiée sous licence [GNU AGPL v3 ou ultérieure](LICENSE).

## Vérification avant chaque envoi

Le dossier `.githooks` contient une vérification lancée à chaque `git push` : elle bloque l'envoi si un commit contient un fichier de clé, un fichier de plus de 1 Mo, un secret probable (clé privée, jeton, mot de passe en clair, longue chaîne base64) ou un mot de votre liste personnelle (`~/.config/sente/interdits.txt`, jamais publiée). À activer une fois par dossier :

```bash
git config core.hooksPath .githooks
```

Pour vérifier tout l'historique : `bash .githooks/pre-push --historique`.

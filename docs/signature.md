# Créer la clé de signature et la confier à GitHub

Les APK de Sente sont signés par GitHub Actions avec **votre** clé. Ce guide se fait une seule fois, sur votre PC (Bazzite). La clé ne quitte votre PC que sous forme de secret GitHub chiffré ; personne d'autre, y compris un assistant, ne la manipule.

> **Important** : si vous perdez cette clé, vous ne pourrez plus publier de mise à jour installable par-dessus l'ancienne version. Il faudrait désinstaller l'appli, et donc perdre favoris et historique. Sauvegardez-la (étape 5).

## 1. Ouvrir un conteneur avec Java

Bazzite est un système immuable : on installe Java dans un conteneur jetable plutôt que sur le système.

```bash
distrobox create --name sente-cle --image registry.fedoraproject.org/fedora:latest
distrobox enter sente-cle
sudo dnf install -y java-21-openjdk-headless
```

## 2. Créer la clé

Toujours dans le conteneur. Votre dossier personnel est partagé avec le conteneur : le fichier sera bien sur votre PC.

```bash
mkdir -p ~/Cles-Sente && chmod 700 ~/Cles-Sente
keytool -genkeypair -v -storetype PKCS12 -keystore ~/Cles-Sente/sente-release.p12 -alias sente -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Sente"
```

`keytool` demande un mot de passe deux fois. Choisissez-en un long et notez-le tout de suite dans votre gestionnaire de mots de passe. Avec ce format (PKCS12), le mot de passe de la clé est le même que celui du fichier.

Notez l'empreinte de votre certificat, pour la comparer plus tard avec celle des notes de préversion :

```bash
keytool -list -v -keystore ~/Cles-Sente/sente-release.p12 -alias sente | grep SHA256
```

## 3. Préparer la clé pour GitHub

GitHub stocke des secrets texte : on convertit la clé en texte (base64).

```bash
base64 -w 0 ~/Cles-Sente/sente-release.p12 > ~/Cles-Sente/sente-release.p12.b64
exit
```

## 4. Déposer les quatre secrets dans GitHub

Le dépôt `Piyaim/Sente` doit déjà exister. Avec l'outil `gh` (à installer une fois avec `brew install gh`, puis `gh auth login`) :

```bash
gh secret set SENTE_KEYSTORE_BASE64 --repo Piyaim/Sente < ~/Cles-Sente/sente-release.p12.b64
gh secret set SENTE_KEYSTORE_PASSWORD --repo Piyaim/Sente
gh secret set SENTE_KEY_PASSWORD --repo Piyaim/Sente
gh secret set SENTE_KEY_ALIAS --repo Piyaim/Sente --body sente
```

Les deuxième et troisième commandes demandent la valeur : collez le **même** mot de passe que celui de l'étape 2.

Vérifiez que les quatre secrets existent (leurs valeurs ne s'affichent jamais) :

```bash
gh secret list --repo Piyaim/Sente
```

Sans `gh` : sur github.com, dépôt Sente → Settings → Secrets and variables → Actions → New repository secret, une fois par secret, avec les mêmes noms.

## 5. Ranger et sauvegarder

Supprimez la version texte, devenue inutile :

```bash
shred -u ~/Cles-Sente/sente-release.p12.b64
```

Puis sauvegardez `~/Cles-Sente/sente-release.p12` à **deux** endroits hors du PC (par exemple une clé USB rangée et une sauvegarde chiffrée), et le mot de passe dans votre gestionnaire de mots de passe. Ne mettez jamais ce fichier dans le dépôt : le `.gitignore` l'exclut, mais vérifiez avec `git status` avant chaque commit.

Le conteneur n'est plus utile :

```bash
distrobox rm sente-cle
```

## 6. Changer de clé plus tard ?

Impossible sans réinstaller l'appli sur tous les téléphones. Ne régénérez pas de clé une fois une préversion installée.

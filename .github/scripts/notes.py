#!/usr/bin/env python3
"""Rédige les notes d'une préversion : nouveautés (CHANGELOG), permissions en clair, vérification repliée.

Usage : notes.py <version> <empreinte apk> <certificat> <fichier permissions> > notes.md
"""
import re
import sys

version, empreinte, certificat, fichier_permissions = sys.argv[1:5]

LIBELLES = {
    "android.permission.INTERNET": "Internet : téléchargement des cartes, uniquement à votre demande",
    "android.permission.ACCESS_FINE_LOCATION": "Position précise (GPS) : quand l'appli est à l'écran, et pendant un guidage",
    "android.permission.FOREGROUND_SERVICE": "Service de premier plan : garde le guidage actif hors de l'écran, avec une notification visible",
    "android.permission.FOREGROUND_SERVICE_LOCATION": "Position pendant ce service : uniquement pendant un guidage",
    "android.permission.POST_NOTIFICATIONS": "Notifications : « Guidage en cours », sans détail du trajet, uniquement pendant un guidage",
    "android.permission.ACCESS_COARSE_LOCATION": "Position approximative : demandée avec la précise, comme l'exige Android",
    "android.permission.ACCESS_NETWORK_STATE": "État du réseau : utilisé par la bibliothèque de carte, ne transmet rien",
}
INTERNES = ("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",)


def nouveautes() -> str:
    texte = open("CHANGELOG.md", encoding="utf-8").read()
    for titre in (re.escape(version), "Non publié"):
        trouve = re.search(rf"^## \[{titre}\][^\n]*\n(.*?)(?=^## \[|\Z)", texte, re.S | re.M)
        if trouve and trouve.group(1).strip():
            # La rubrique « Technique » sert aux développeurs : pas dans les notes de préversion.
            section = re.sub(r"### Technique\n.*?(?=^### |\Z)", "", trouve.group(1), flags=re.S | re.M).strip()
            return re.sub(r"^### ", "#### ", section, flags=re.M)
    return "Voir CHANGELOG.md."


permissions = []
for ligne in open(fichier_permissions, encoding="utf-8"):
    nom = re.match(r"uses-permission: name='([^']+)'", ligne)
    if not nom or nom.group(1).endswith(INTERNES):
        continue
    permissions.append(LIBELLES.get(nom.group(1), nom.group(1)))

print("Préversion **de test** : à installer pour essayer, pas pour naviguer.\n")
print("### Nouveautés\n")
print(nouveautes() + "\n")
print("### Permissions demandées\n")
print("\n".join(f"- {p}" for p in permissions) if permissions else "Aucune.")
print()
print("<details><summary>Vérifier le fichier (facultatif)</summary>\n")
print(f"- Empreinte SHA-256 de l'APK : `{empreinte}`")
print(f"- Certificat de signature (SHA-256) : `{certificat}`")
print("- Liste complète des dépendances : fichier `dependances.txt`")
print("\n</details>")

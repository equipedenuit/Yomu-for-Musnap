# 読む Yomu

Petite appli Android pour lire en japonais sur liseuse : une poignée sur le bord de l'écran
ouvre un panneau qui traduit et décompose la phrase copiée, via OpenRouter.

## Fonctionnement

- **Poignée** : une barre fine sur le bord droit ou gauche de l'écran, par-dessus toutes les applis.
  Touche-la pour ouvrir le panneau, fais-la glisser pour la déplacer.
- **Texte pris en compte**, dans l'ordre :
  1. le texte envoyé par le menu de sélection (« Analyser (Yomu) ») ou par « Partager » ;
  2. le bouton « Translate » si Yomu est déclaré comme traducteur (option dans les réglages) ;
  3. sinon, le **presse-papiers** : copie la phrase, puis touche la poignée.
- **Panneau** : traduction, mot à mot, grammaire, nuance. Le texte source est modifiable
  (pour corriger l'OCR) ; « Relancer » refait l'analyse ; un champ permet de poser une question de suivi.
  Le résultat est sélectionnable (tu peux y chercher un mot avec Hoshi).
- **E-ink** : noir et blanc, sans animation, affichage du streaming regroupé pour limiter les rafraîchissements.
- **Cache** : rouvrir le panneau sur la même phrase réaffiche le résultat sans refaire d'appel payant.

## Compiler l'APK avec GitHub (sans rien installer)

1. Crée un dépôt **privé** sur GitHub et envoie-y le contenu de ce dossier
   (y compris le dossier caché `.github`).
2. Onglet **Actions** → **Build APK** → **Run workflow**.
3. Après 3 à 5 minutes, l'APK est dans **Releases** (`Yomu-N.apk`).

Chaque nouvelle compilation se signe avec la même clé (`app/yomu.keystore`) :
les mises à jour s'installent par-dessus l'ancienne version.

## Compiler en local (Linux)

Prérequis : JDK 17, Android SDK (plateforme 35), Gradle 8.11.

```
export ANDROID_HOME=~/Android/Sdk
gradle assembleRelease
# APK : app/build/outputs/apk/release/app-release.apk
```

## Premier lancement

1. Ouvre Yomu, touche **Autoriser l'affichage par-dessus les applis**, active Yomu, reviens.
2. Touche **Afficher la poignée**.
3. Colle ta clé OpenRouter, vérifie le modèle (`anthropic/claude-haiku-5.5` par défaut).
4. **Tester** pour vérifier que tout marche.

La clé est stockée en local sur l'appareil (préférences de l'appli, sauvegarde désactivée).
Elle n'est jamais incluse dans le code ni dans l'APK.

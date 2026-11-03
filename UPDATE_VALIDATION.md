# Validation de la mise à jour GitHub 1.1.3

Essai réel effectué le 3 novembre 2026 sur le Samsung Galaxy S8+ sous Android 9, après les tests IA. L'ancienne APK 1.1.2 a été réinstallée pour cet essai en conservant les données.

1. Réglages de l'application : « Vérifier les mises à jour » détecte la publication GitHub v1.1.3 depuis la version locale 1.1.2.
2. La fenêtre affiche le vrai tag et les notes GitHub, avec « Télécharger l'APK ».
3. Le bouton ouvre Chrome et télécharge `CalorieCoach-1.1.3.apk` depuis l'asset public GitHub.
4. Le SHA-256 du fichier reçu sur le téléphone correspond à celui de l'APK compilée et de l'asset GitHub : `269c2e4d3bb137abf6604270feb790ccbe25ad7cbcfe4f6f8ad30ce0db36fa91`.
5. L'installateur Android propose une mise à jour sans perte de données ni nouvelle permission spéciale. Chrome disposait déjà de l'autorisation d'installation sur ce téléphone.
6. Play Protect a demandé une analyse de l'APK inconnue. L'analyse a été effectuée, sans désactiver Play Protect. Son écran a indiqué « Cette appli semble sûre » et a permis de poursuivre.
7. L'installateur Android a affiché « Application installée ». Le gestionnaire de paquets confirme `versionName=1.1.3`, `versionCode=5`.
8. L'application a été rouverte. Les 14 entrées de conversation locale, dont la dernière réponse du coach, sont conservées.

La mise à niveau finale a été effectuée depuis le fichier téléchargé avec Chrome et les écrans de l'installateur Android, pas par une installation ADB. La préparation de l'ancienne version a utilisé ADB.

Limite : ce résultat valide le parcours manuel depuis les Réglages sur cet appareil. L'app ne propose pas encore une installation silencieuse ni une vérification automatique des versions en arrière-plan. Les autres téléphones peuvent demander une autorisation de source d'installation ou une analyse Play Protect.


# Calorie Coach

Application Android native en français, avec cinq thèmes, des transitions animées et une icône personnalisée.

## Fonctions
- Cinq thèmes mémorisés dans Réglages : Minuit, Forêt, Océan, Crépuscule et Aube (clair), avec couleurs adaptées à l'ensemble des écrans.
- Tableau de bord avec calories consommées, dépensées et progression animée.
- Journal du jour conservé localement entre les ouvertures de l’app.
- Profil local (âge, taille, poids, objectif, formule de sexe et activité hors sport), avec une cible quotidienne indicative calculée automatiquement et ajustable.
- Tutoriel de première ouverture et onglet Coach IA avec conversation conservée localement et synchronisée après connexion.
- Création au choix d’une séance pour aujourd’hui ou d’un programme hebdomadaire.
- Assistant Gemini raccordé à Firebase AI Logic : aucune clé Gemini personnelle n’est demandée ni intégrée à l’APK.
- Comptes Firebase par e-mail et mot de passe. Après connexion, le profil, le journal et les échanges sont sauvegardés dans Firestore, isolés par compte.
- Estimation d’un repas à partir d’une description seule, d’une photo ou des deux, avec quantités et sauces. Les calories sont modifiables avant l'enregistrement et aucune valeur arbitraire n'est enregistrée par défaut.
- Estimation d’une séance décrite en texte avec Gemini; estimation MET disponible par activité.
- Programme sportif personnalisé par IA selon le profil, le niveau, le matériel et le rythme choisis; programme sauvegardé localement.
- Rappels Android configurables par jours et par heure, repris après redémarrage. Android peut décaler légèrement leur heure selon ses règles d’économie d’énergie.
- Vérification manuelle des nouvelles versions GitHub depuis Réglages, avec source officielle visible en lecture seule et accès direct à l’APK. Notes de version embarquées consultables hors ligne.

## Installer l’APK
Transfère CalorieCoach.apk sur ton téléphone Android, ouvre le fichier, puis autorise l’installation si Android te le demande. Crée un compte ou connecte-toi depuis Réglages pour synchroniser tes données. Sans connexion, l’application continue à conserver les données localement.

Les appels IA utilisent Firebase AI Logic et nécessitent Internet. Les photos de repas envoyées sont transmises à Gemini pour estimation; les résultats restent approximatifs et ne remplacent pas un avis médical. Le projet Firebase utilise Firestore en Europe, avec des règles qui limitent l’accès aux données au compte connecté.

Cette APK reste signée avec la clé de développement personnelle pour conserver la compatibilité des mises à jour. Depuis la version 1.1.2, elle utilise Play Integrity pour App Check, y compris dans l’APK installable de développement. Le fournisseur de débogage est réservé à une compilation locale explicite avec `-PappCheckDebug=true` ; ne jamais publier ces builds ni leurs jetons.

Le projet doit activer l’API Google Play Integrity et enregistrer l’application Android dans Firebase App Check avec le SHA-256 du certificat qui signe l’APK. Pour une distribution exclusivement hors Google Play, les réglages Firebase recommandés sont : `PLAY_RECOGNIZED` non requis, `LICENSED` non requis, intégrité de l’appareil requise. Aucun contrôle App Check n’est désactivé par le code. Un appareil modifié ou non certifié peut être refusé.

Depuis la version 1.1.3, l'app utilise Gemini 3.5 Flash-Lite puis Gemini 3.8 Flash en secours uniquement en cas de panne temporaire ou de délai dépassé. Les refus d’accès et quotas sont signalés sans boucle de nouvelles tentatives. Les photos sont des estimations, pas des mesures nutritionnelles exactes.

## Validation sur Android

La version 1.2.0 a été vérifiée sur le téléphone de test : [VALIDATION_1.2.0.md](VALIDATION_1.2.0.md). La suite d'interface se lance avec `adb shell am instrument -w -e features true fr.caloriecoach.app.test/fr.caloriecoach.app.AiDiagnostics` ; elle vérifie les thèmes, leur mémorisation, les notes de version et le formulaire de repas sans enregistrer d'entrée fictive dans le journal.

Les résultats des essais sur le téléphone physique sont décrits dans [AI_VALIDATION.md](AI_VALIDATION.md). Le test d’intégration n’est pas inclus dans l’APK de l’application. Pour le lancer sur un appareil de développement autorisé : compiler `assembleDebug assembleDebugAndroidTest`, installer les deux APK puis exécuter `adb shell am instrument -w -e suite true fr.caloriecoach.app.test/fr.caloriecoach.app.AiDiagnostics`. Il utilise des données fictives et ne lit pas le profil ni l’historique réels.

La vérification des mises à jour utilise le dépôt officiel `zigenounoube-hue/CalorieCoach`. Ce dépôt est affiché mais ne peut pas être modifié dans l'app ; les anciennes préférences de dépôt éditable sont ignorées. Chaque publication GitHub doit inclure son tag de version, ses notes et l’APK signé. Android demandera l’autorisation d’installation à l’utilisateur. Voir [CHANGELOG.md](CHANGELOG.md).

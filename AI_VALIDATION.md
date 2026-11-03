# Validation IA — 1.1.3

Essais du 3 novembre 2026 sur Samsung Galaxy S8+ sous Android 9, connecté en USB. APK versionCode 5, versionName 1.1.3. App Check utilise Play Integrity, sans jeton de débogage. Appareil physique et services Firebase/Gemini réels ; aucune réponse IA simulée.

## Causes identifiées et corrections

- L'API Google Play Integrity n'était pas activée. Elle est maintenant activée et l'application apparaît « Enregistrée » dans Firebase App Check. Les réglages de distribution hors Google Play ont été appliqués avec l'accord de l'utilisateur.
- Le modèle Gemini 3.8 Flash a renvoyé « currently experiencing high demand ». Le modèle principal est maintenant Gemini 3.5 Flash-Lite. Gemini 3.8 Flash est le secours lors d'une erreur temporaire. Un refus d'autorisation ou de quota n'entraîne pas de changement de modèle.
- La conversation restait sur les anciens messages. Elle défile désormais vers les messages récents et l'indication d'attente.
- La limite de réponse des programmes provoquait un arrêt MAX_TOKENS. Les budgets de réponse ont été augmentés, avec des instructions concises.

## Résultats de la suite finale

| Essai | Résultat | Durée mesurée |
| --- | --- | --- |
| Rafraîchissement du jeton App Check | Valide | 3 694 ms |
| Premier appel IA après authentification | Réponse « OK » | environ 2 199 ms (5 893 ms cumulés) |
| Chat avec un historique fictif | Retrouve l'activité « marche » | 1 974 ms |
| Activité décrite : 30 minutes de marche, 70 kg fictifs | JSON valide, 140 kcal estimées | 1 270 ms |
| Programme pour une journée | Texte non vide, sans balises Markdown en gras | 2 158 ms |
| Programme hebdomadaire, 3 séances | Texte non vide, sans balises Markdown en gras | 2 445 ms |
| Image illustrée de test et description d'une banane | JSON valide, 105 kcal estimées | 1 398 ms |

Suite finale : `FAILURES=0`. Les estimations et les durées varient d'un appel à l'autre.

Un test supplémentaire a été réalisé dans l'interface principale : question envoyée avec le bouton Envoyer, réponse du coach affichée automatiquement, sans relancer une deuxième demande. La conversation locale existante a été conservée lors de la mise à jour.

## Limites

- L'image utilisée est une illustration synthétique. Ce test valide l'envoi multimodal et l'analyse JSON, pas la précision nutritionnelle d'une photographie réelle.
- Les programmes générés restent des suggestions ; ce test ne constitue pas une validation médicale ou sportive.
- Le modèle de secours et les délais sont implémentés ; une panne forcée du fournisseur n'a pas été provoquée dans cette suite réelle.
- App Check a été validé sur ce Galaxy. Des appareils modifiés ou non certifiés peuvent être refusés.
- Les comptes, la restauration multiappareil et les notifications ne font pas partie de cette correction IA et n'ont pas été revalidés dans cette suite.

## Exécuter la suite

Compiler `assembleDebug assembleDebugAndroidTest`, installer les APK de l'app et du test sur l'appareil autorisé, puis lancer :

```text
adb shell am instrument -w -e suite true fr.caloriecoach.app.test/fr.caloriecoach.app.AiDiagnostics
```

Le test ne lit ni le profil réel ni l'historique de conversations. Il ne journalise pas le jeton App Check.

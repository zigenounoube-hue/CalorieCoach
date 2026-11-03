# Journal des mises à jour

## 1.2.0 — thèmes et repas décrits

- Cinq ambiances sélectionnables dans Réglages : Minuit, Forêt, Océan, Crépuscule et Aube (clair). La palette est appliquée à toute l'application et mémorisée sur l'appareil.
- La description d'un repas peut être envoyée seule à l'IA pour estimer ses calories. La photo reste facultative.
- Suppression de la valeur automatique de 450 kcal. Une estimation valide ou une saisie manuelle explicite est nécessaire pour enregistrer un repas.
- Les calories restent modifiables. Changer la description ou la photo annule l'ancienne estimation pour éviter une valeur périmée.
- Suppression de l'encart de connexion technique dans Réglages.
- Dépôt GitHub visible en lecture seule, source officielle fixe pour les vérifications de mises à jour.
- Notes de version disponibles dans l'app, même hors ligne, à la place du bouton de simulation.

## 1.1.3 — 3 novembre 2026

- App Check enregistré avec Play Integrity et API Google Play Integrity activée pour les APK distribuées hors Google Play.
- Modèle Gemini Flash-Lite plus rapide, avec un second modèle en cas de panne temporaire ; attente limitée par tentative.
- Défilement automatique du chat vers les nouveaux messages et la réponse du coach.
- Réponses et programmes autorisés à être suffisamment longs pour éviter l’arrêt MAX_TOKENS.
- Tests sur téléphone physique : jeton App Check, chat avec historique, activité décrite, séances à la journée et à la semaine, image de test. Aucun échec dans la suite finale.

## 1.1.2 — version de test

- Fournisseur Play Integrity utilisé dans les APK distribuées. Le mode App Check de débogage nécessite désormais une option de compilation explicite.

## 1.1.1 — version de test

- Les refus App Check, accès refusés, modèles introuvables et quotas sont signalés sans nouvelles tentatives inutiles.
- Limite de durée ajoutée autour de chaque appel IA.
- Compilation APK réussie ; essais IA et installation de mise à jour encore à valider sur Android.

## 1.1.0

- Connexion des estimations de repas, des séances et des programmes à Firebase AI Logic sans demander une clé personnelle dans l’application.
- Nouvelle tentative automatique lors d’un incident temporaire du service IA.
- Contrôle des nouvelles publications GitHub dans Réglages, avec les notes de version et l’accès au téléchargement APK.
- Les estimations d’activité et les programmes peuvent être utilisés sans saisir de clé API dans l’app.

Les calories sont des estimations indicatives. L’installation d’un APK téléchargé est confirmée par Android.


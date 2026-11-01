# Calorie Coach

Application Android native en français, en thème sombre avec transitions animées, accents lumineux et une icône personnalisée.

## Fonctions
- Tableau de bord avec calories consommées, dépensées et progression animée.
- Journal du jour conservé localement entre les ouvertures de l’app.
- Profil local (âge, taille, poids, objectif, formule de sexe et activité hors sport), avec une cible quotidienne indicative calculée automatiquement et ajustable.
- Tutoriel de première ouverture et onglet Coach IA avec conversation conservée localement et synchronisée après connexion.
- Création au choix d’une séance pour aujourd’hui ou d’un programme hebdomadaire.
- Assistant Gemini raccordé à Firebase AI Logic : aucune clé Gemini personnelle n’est demandée ni intégrée à l’APK.
- Comptes Firebase par e-mail et mot de passe. Après connexion, le profil, le journal et les échanges sont sauvegardés dans Firestore, isolés par compte.
- Estimation d’un repas à partir d’une photo avec Gemini, modifiable avant l’enregistrement.
- Estimation d’une séance décrite en texte avec Gemini; estimation MET disponible par activité.
- Programme sportif personnalisé par IA selon le profil, le niveau, le matériel et le rythme choisis; programme sauvegardé localement.
- Rappels Android configurables par jours et par heure, repris après redémarrage. Android peut décaler légèrement leur heure selon ses règles d’économie d’énergie.
- Vérification manuelle des nouvelles versions GitHub depuis Réglages, avec notes de version et accès direct à l’APK d’une publication.

## Installer l’APK
Transfère CalorieCoach.apk sur ton téléphone Android, ouvre le fichier, puis autorise l’installation si Android te le demande. Crée un compte ou connecte-toi depuis Réglages pour synchroniser tes données. Sans connexion, l’application continue à conserver les données localement.

Les appels IA utilisent Firebase AI Logic et nécessitent Internet. Les photos de repas envoyées sont transmises à Gemini pour estimation; les résultats restent approximatifs et ne remplacent pas un avis médical. Le projet Firebase utilise Firestore en Europe, avec des règles qui limitent l’accès aux données au compte connecté.

Cette APK est une version de débogage personnelle. App Check utilise le fournisseur de débogage pour Gemini; Firebase demande d’enregistrer le jeton de débogage de l’appareil avant d’autoriser ses requêtes IA. Pour une diffusion publique, générer une version de production et configurer Play Integrity.

Pour activer la vérification des mises à jour, indique dans Réglages le dépôt GitHub au format `propriétaire/projet`. Chaque publication GitHub doit inclure son tag de version, ses notes et l’APK signé. Android demandera l’autorisation d’installation à l’utilisateur. Voir [CHANGELOG.md](CHANGELOG.md).

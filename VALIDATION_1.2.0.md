# Validation Android — Calorie Coach 1.2.0

Essais réalisés le 3 novembre 2026 sur le téléphone physique de test Samsung Galaxy S8+ sous Android 9. Application 1.2.0, code de version 6, installée en mise à jour sans effacement des données.

## Fonctionnalités vérifiées dans l'interface

- Sélection des cinq thèmes Minuit, Forêt, Océan, Crépuscule et Aube, avec changement visible du tableau de bord.
- Mémorisation du thème et conservation après redémarrage de l'activité.
- Contraste d'au moins 4,5:1 pour les paires texte/fond principales, secondaires et boutons de chaque palette.
- Dépôt GitHub affiché en lecture seule et absence du bouton de simulation. La vérification des versions utilise la source officielle constante, sans dépendre de l'ancienne préférence éditable.
- Ouverture des notes de version embarquées avec les nouveautés de la 1.2.0.
- Absence de valeur calorique initiale : Enregistrer est désactivé tant qu'aucune estimation ou saisie explicite n'existe.
- Description « Une pomme de 150 g » envoyée réellement à l'IA depuis le formulaire, sans photo : résultat affiché 78 kcal.
- Correction manuelle des calories possible ; modifier la description annule ensuite l'estimation précédente et désactive l'enregistrement.
- Aucun repas fictif n'a été enregistré dans le journal pour ces essais. Le thème initial est restauré après les tests.

La suite finale d'interface renvoie `FAILURES=0`. Les captures sont conservées dans les livrables locaux de validation.

## Appels IA réels

| Essai | Résultat observé |
| --- | --- |
| Jeton Firebase App Check | Valide |
| Réponse simple | OK |
| Conversation avec historique fictif | Activité préférée correctement rappelée |
| Séance décrite | Marche rapide, 140 kcal |
| Programme du jour | Généré |
| Programme de la semaine | Généré |
| Image de test représentant une banane | 105 kcal |
| Description : pomme de 150 g | 78 kcal |
| Description : eau plate de 250 ml | 0 kcal |
| Description : riz cuit 200 g, poulet 150 g, huile d'olive 15 g | 537 kcal |
| Description vide | Refusée avant tout appel réseau |

La suite IA renvoie `FAILURES=0`. Les descriptions ont pris environ 0,8 à 1,7 seconde lors de cette exécution. Ces nombres montrent que les entrées sont analysées ; ils ne constituent pas une validation nutritionnelle ni une garantie de temps de réponse. L'image de test est une illustration, pas une photo de repas réel.

## Compilation et limites

`assembleDebug assembleDebugAndroidTest` a réussi. L'APK de l'application conserve la signature de développement utilisée pour les mises à jour précédentes et utilise Play Integrity, sans activer le fournisseur App Check de débogage.

Ces essais concernent un appareil physique. Les services distants nécessitent Internet et restent soumis aux disponibilités et quotas Firebase/Gemini. Les comptes et la synchronisation n'ont pas été modifiés dans cette version ; aucun compte personnel ni historique réel n'a été utilisé pour les requêtes de validation.

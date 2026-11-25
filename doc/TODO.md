# TODO (évolutions éventuelles)

## Interface Web Admin OIE
- Ajouter un panneau de configuration (**Settings**) dans le Web Admin d'OIE (`webadmin/plugin.js` + `plugin.json`) pour :
  - visualiser et modifier le répertoire des profils (`GET/POST /api/extensions/hl7v2-validation/settings`) ;
  - déclencher le rechargement des jeux (`POST /sets/reload`) ;
  - lister les jeux de profils chargés et leur couverture HAPI / NIST (`GET /sets`) ;
  - éventuellement proposer un écran de test de validation rapide sur un message ER7.

## Packaging / Structure
- Simplifier éventuellement le projet en mono-module Maven si le module `core` n'est pas consommé séparément hors du plugin.

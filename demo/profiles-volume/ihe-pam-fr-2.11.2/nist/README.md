# `nist/` — le jeu au format de validation NIST

Ce sont les profils 2B du jeu, traduits au format qu'exporte IGAMT et que lit le moteur de
validation du NIST (`gov.nist:hl7-v2-validation`), completes des regles que le 2B ne sait pas
exprimer. Ce dossier est exclu de l'indexation des profils 2B, comme `hapi/tables/`.

| Fichier | Contenu |
|---|---|
| `Profile.xml` | les 35 messages ; segments et types partages en variantes (`PID`, ou `PID_1`, `PID_2`… quand le jeu en decrit plusieurs) |
| `ValueSets.xml` | les tables de `../tables/` liees a un element |
| `Constraints.xml` | predicats des usages C, et assertions (co-contraintes comprises) |

Bilan de la derniere generation : 35 messages, 65 variantes de segment, 91 variantes de type, 127 jeux de valeurs ; 12 predicats, 255 assertions ; 2581 usages C sur 11479 rendus en O; composites decrits sans composant : TS.

Pas de `CoConstraints.xml` ni de `Slicings.xml` : les co-contraintes de PAM FR s'ecrivent en
assertions (`IMPLY`), et une assertion posee sur un type (CX, XPN) vaut pour chaque repetition,
ce que le decoupage (slicing) apporterait.

## Traduction des profils 2B

- usage C (et CE) : garde en C avec son predicat quand la specification enonce la condition, remplace
  par un usage simple quand le texte en donne un, rendu en O sinon (comme HAPI, qui ne le verifie
  pas) ;
- tables liees aux seuls elements primitifs, et seulement quand le jeu fournit la table non vide :
  exactement les cas ou HAPI controle les codes ;
- valeurs constantes (MSH-12…) gardees en attribut `ConstantValue`, que le moteur NIST verifie ;
- OBX-5 (`Varies`) traite comme primitif.

Schemas : <https://github.com/Jungyubw/NIST_healthcare_hl7_v2_profile_schema>, dossier
`Schema/NIST Validation Schema` ; les trois fichiers y sont conformes (`xmllint --schema`).

## Assertions

*Regles* : assertions de la famille ; *Contextes* : variantes de segment ou de type, ou messages,
ou elles sont posees.

| Assertion | Sur | Regles | Contextes | Niveau | Regle | Source |
|---|---|---|---|---|---|---|
| PID-INS-VALI | PID | 1 | 2 | erreur | Le matricule INS n'est vehicule que si l'identite est qualifiee : PID-32 vaut VALI. | PAM FR 2.11.2 §6.6.15 p.41 |
| CX-INS-AUTORITE | CX | 1 | 2 | erreur | Un INS (CX-5 = INS) a pour autorite l'OID INS-NIR ou INS-NIA (production, test, demonstration), de type ISO. | Appendix N 1.8.2 §N.1 p.7 |
| CX-INS-FORMAT | CX | 1 | 2 | erreur | « Il est necessaire de vehiculer l'INS avec sa cle » : 15 caracteres. | PAM FR 2.11.2 §6.6.1 p.36 |
| CX-INS-DATES | CX | 1 | 2 | avertissement | Seul le dernier INS est transmis : « les composants CX.7 et CX.8 sont alors vides ». | PAM FR 2.11.2 §6.6.1 p.36 |
| MSH-21-PAM-FR | MSH | 1 | 5 | erreur | Declaration de conformite 2.11^IHE_FRANCE-2.11-PAM. Approchee : chaque composant est cherche dans une repetition, pas forcement la meme. | PAM FR 2.11.2 §6.5 p.34 |
| XPN-5-MLLE | XPN | 1 | 2 | avertissement | La civilite Mademoiselle (Mlle) ne devrait pas etre utilisee. | Appendix N 1.8.2 §N.10 p.37 |
| PV1-VENUE-* | PV1 | 9 | 27 | erreur | Mode de traitement 03… | PAM FR 2.11.2 §6.10.19 p.59 |
| PV1-DISCIPLINE-* | PV1 | 227 | 681 | avertissement | Discipline 035 (Scanographie X)… | PAM FR 2.11.2 §7.3 p.122 |
| IN1-AMO-REQUIS | IN1 | 1 | 2 | erreur | Couverture AMO, CMU ou AME : IN1-3 (regime, caisse, centre) et IN1-17 (qualite du beneficiaire) sont requis. | PAM FR 2.11.2 §6.20.1 p.90 |
| IN1-AMO-NATURE | IN1 | 1 | 2 | avertissement | Couverture AMO : nature d'assurance (IN1-31) 10, 13, 30, 41 ou 90 (norme B2). Liste citee par le tableau, la norme B2 fait foi : avertissement. | PAM FR 2.11.2 §6.20.1 p.91 |
| IN1-AMO-ACCIDENT | IN1 | 1 | 2 | erreur | IN1-36 porte le n° d'accident du travail (nature 41) ou la date de grossesse, d'accouchement ou d'adoption (nature 30). Pour 10 et 13, seulement en cas d'accident : non verifiable. | PAM FR 2.11.2 §6.20.1 p.92 |
| IN1-AMC-REQUIS | IN1 | 1 | 2 | erreur | Couverture AMC, CMUC ou AMEC : IN1-3 (n° d'organisme complementaire) et IN1-17 (qualite du beneficiaire) sont requis. | PAM FR 2.11.2 §6.20.2 p.93 |
| IN1-AMC-CONTRAT | IN1 | 1 | 2 | avertissement | Couverture AMC : type de contrat (IN1-31) 85, 87, 88, 89, 01 ou 02. Liste citee par le tableau, fournie par la caisse : avertissement. | PAM FR 2.11.2 §6.20.2 p.94 |
| Z-NON-VIDE-* | ZFA, ZFD, ZFP, ZFV, ZFM | 5 | 7 | erreur | Le segment ZFA, sans champ obligatoire, n'est pas transmis vide.… | PAM FR 2.11.2 §5.2 p.24 |
| ROL-APRES-PID | message | 1 | 15 | erreur | Le ROL qui suit PID/PD1 porte le medecin traitant (ODRP) ; remplacant (SUBS), correspondant (RT), adressant (RP) et consulte (CP, §6.8.2) suivent PV1/PV2. | PAM FR 2.11.2 §6.8.6 p.45 |
| IN1-AUTRE-DEBITEUR-GT1 | message | 1 | 12 | erreur | Un autre debiteur (IN1-2 : PAT, ASS, EMP, ETB, DEP) est decrit par une sequence [GT1, IN1, IN2] : le message porte un GT1. | PAM FR 2.11.2 §6.20.3 p.95 |
| ROL-APRES-PV1 | message | 1 | 13 | erreur | Le medecin traitant (ODRP) est communique dans le ROL qui suit PID/PD1, pas dans celui qui suit PV1/PV2. | PAM FR 2.11.2 §6.8.6 p.45 |

## Matrice de couverture

Sorts : **2B** — verifie par les profils du jeu (HAPI) ; **NIST** — verifie par `Constraints.xml` ;
**partiel** — en partie ; **O** — usage C laisse optionnel, faute de condition enoncee ; **a faire** —
exprimable, pas encore ecrit ; **hors de portee** — non verifiable sur un message isole.

### Usages conditionnels de la specification

| Element | Sort | Detail | Source |
|---|---|---|---|
| PID-7 Date/Time of Birth | NIST | predicat C(R/O) : Date de naissance obligatoire quand l'INS est vehicule et l'identite qualifiee. | PAM FR 2.11.2 §6.6.4 p.38 |
| PID-8 Administrative Sex | NIST | predicat C(R/O) : Sexe obligatoire quand l'INS est vehicule et l'identite qualifiee. | PAM FR 2.11.2 §6.6.5 p.38 |
| PID-11 Patient Address | O | aucune condition enoncee (§6.6.6 renvoie au type XAD) | PAM FR 2.11.2 §6.6 p.35 |
| PID-18 Patient Account Number | 2B | par profil : R dans ITI-31 (§6.6.9), O dans ITI-30 | PAM FR 2.11.2 §6.6 p.35 |
| PID-25 Birth Order | NIST | predicat C(X/O) : Rang de naissance d'une naissance multiple, vide pour une naissance simple : interdit quand PID-24 dit naissance simple (N). PID-24 vide ne dit rien, PID-25 reste alors optionnel. | PAM FR 2.11.2 §6.6.12 p.40 |
| PID-31 Identity Unknown Indicator | O | aucune condition dans PAM FR | PAM FR 2.11.2 §6.6 p.35 |
| PID-33 Last Update Date/Time | O | aucune condition dans PAM FR | PAM FR 2.11.2 §6.6 p.35 |
| PID-35 Species Code | O | aucune condition dans PAM FR (code d'espece, patients animaux) | PAM FR 2.11.2 §6.6 p.35 |
| PID-36 Breed Code | O | aucune condition dans PAM FR (race, patients animaux) | PAM FR 2.11.2 §6.6 p.35 |
| ROL-1 Role Instance ID | O | aucune condition dans PAM FR | PAM FR 2.11.2 §6.8 p.44 |
| PV1-3 Assigned Patient Location | O | composants « a renseigner lorsqu'ils sont connus », sans condition sur le champ (§6.10.2) | PAM FR 2.11.2 §6.10 p.51 |
| PV1-5 Preadmit Number | hors de portee | depend d'une pre-admission anterieure (§6.10.4), information absente du message | PAM FR 2.11.2 §6.10 p.51 |
| PV1-6 Prior Patient Location | O | aucune condition dans PAM FR | PAM FR 2.11.2 §6.10 p.51 |
| PV1-11 Temporary Location | O | aucune condition dans PAM FR | PAM FR 2.11.2 §6.10 p.51 |
| PV1-19 Visit Number | 2B | par profil : R dans ITI-31 | PAM FR 2.11.2 §6.10 p.51 |
| PV1-42 Pending Location | O | aucune condition dans PAM FR | PAM FR 2.11.2 §6.10 p.51 |
| PV2-1 Prior Pending Location | O | aucune condition dans PAM FR | PAM FR 2.11.2 §6.11 p.61 |
| PV2-47 Expected LOA Return Date/Time | NIST | usage R dans PES/ITI-31/ADT_A21 : Date de retour prevue, renseignee avec l'evenement A21 (depart en permission). | PAM FR 2.11.2 §6.11.11 p.65 |
| ZBE-6 Original trigger event code | NIST | predicat C(R/O) : ZBE-6 est renseigne quand ZBE-4 vaut UPDATE ou CANCEL. | PAM FR 2.11.2 §6.13.6 p.68 |
| ZBE-7 Ward of medical responsibility in the period starting with this movement | O | composants « a renseigner lorsqu'ils sont connus », sans condition sur le champ (§6.13.7) | PAM FR 2.11.2 §6.13 p.67 |
| ZBE-8 Ward of care responsibility in the period starting with this movement | O | composants « a renseigner lorsqu'ils sont connus », sans condition sur le champ (§6.13.8) | PAM FR 2.11.2 §6.13 p.67 |
| OBX-5 Valeur de l’observation | O | aucune condition dans PAM FR | PAM FR 2.11.2 §6.21 p.98 |
| OBX-6 Unité | NIST | predicat C(RE/O) : Unite requise pour une observation NM ou SN qui est une mesure : « mesure » n'est pas verifiable, d'ou RE et non R. | PAM FR 2.11.2 §6.21.4 p.99 |
| CX-7 Effective Date | NIST | predicat C(R/O) : CX-7 porte la date de calcul de l'INS-C ; optionnel pour les autres identifiants. | Appendix N 1.8.2 §N.1 p.11 |
| HD-2 Universal ID | NIST | predicat C(R/X) : HD-2 et HD-3 sont renseignes ensemble ou pas du tout. | HL7 v2.5 §2.A.33 |
| HD-3 Universal ID Type | NIST | predicat C(R/X) : HD-2 et HD-3 sont renseignes ensemble ou pas du tout. | HL7 v2.5 §2.A.33 |
| PL-6 Person Location Type | O | aucune condition enoncee (§N.4) | Appendix N 1.8.2 §N.4 p.15 |
| XCN-10 Name Type Code | NIST | predicat C(R/O) : XCN-10 est renseigne des que XCN-2 ou XCN-3 l'est. | Appendix N 1.8.2 §N.8 p.31 |
| XCN-13 Identifier Type Code | NIST | predicat C(R/O) : XCN-13 est obligatoire des que XCN-1 contient un identifiant. | Appendix N 1.8.2 §N.8 p.31 |
| XPN-1 Family Name | NIST | usage RE : Nom de famille « requis si connu ». | Appendix N 1.8.2 §N.10 p.36 |
| XPN-2 Given Name | NIST | usage R : Premier prenom : « ce champ est obligatoire ». | Appendix N 1.8.2 §N.10 p.36 |
| XPN-3 Second and Further Given Names or Initials Thereof | O | aucune condition enoncee, seulement des exemples (§N.10) | Appendix N 1.8.2 §N.10 p.36 |
| XTN-4 Email Address | NIST | predicat C(R/X) : XTN-4 porte l'adresse e-mail, a condition que XTN-2 vaille NET. | Appendix N 1.8.2 §N.11 p.40 |
| XTN-12 Unformatted Telephone number | NIST | predicat C(R/X) : XTN-12 porte le numero de telephone quand XTN-2 differe de NET ou est vide. | Appendix N 1.8.2 §N.11 p.40 |

Usages C herites de Gazelle, absents des tableaux de la specification, rendus en O (element,
occurrences dans les profils) : `CE-1` (12), `CNE-7` (30), `DG1-20` (18), `DG1-21` (18), `EI-2` (394), `EI-3` (394), `EI-4` (394), `EVN-3` (5), `MSH-18` (35), `OBX-4` (25), `OSP-2` (10), `OSP-3` (10), `PR1-19` (12), `PR1-20` (12), `XCN-12` (140), `XCN-8` (140).

### Regles hors 2B

Bilan : hors de portee : 17, NIST : 7, partiel : 4.

| Regle | Sort | Detail | Source |
|---|---|---|---|
| PID-32 doit valoir VALI des que PID-3 porte le matricule INS ; sinon le recepteur integre le message sans l'INS. Co-contrainte entre PID-3 (CX-5 = INS) et PID-32. | NIST | assertion PID-INS-VALI | PAM FR 2.11.2 §6.6.15 p.41 |
| Statuts et attributs d'identite de la table 0445 : combinaisons autorisees decrites par le Referentiel National d'Identitovigilance ; un attribut va dans une repetition de PID-32. | hors de portee | combinaisons decrites par le RNIV, hors de la specification | PAM FR 2.11.2 §6.6.15 p.41 |
| Passage d'un INS-NIA a un INS-NIR : les deux peuvent figurer en PID-3 ; evenement A31. | hors de portee | enchainement de messages | PAM FR 2.11.2 §4.4.1 p.18 |
| Modification de l'INS-NIR : evenement A47, ancien matricule en MRG-1, nouveau en PID-3. | hors de portee | enchainement de messages, ancien et nouveau matricule | PAM FR 2.11.2 §4.4.2 p.18 |
| Suppression d'un INS : A47, INS en MRG-1, PID-3 avec l'IPP et "" pour l'OID de l'INS ; le statut de l'identite suit en PID-32 ou dans un A31. | hors de portee | enchainement de messages | PAM FR 2.11.2 §4.4.3 p.19 |
| Changement de statut identite qualifiee vers identite recuperee : evenement A47. | hors de portee | enchainement de messages | PAM FR 2.11.2 §4.4.4 p.19 |
| Evenements et options obligatoires d'ITI-30 en France (option Merge requise : A28, A31, A47, A40). | hors de portee | capacite de l'acteur, pas d'un message | PAM FR 2.11.2 §2.1 p.13 |
| Evenements et options obligatoires d'ITI-31 en France, selon les options de l'acteur. | hors de portee | capacite de l'acteur, pas d'un message | PAM FR 2.11.2 §2.2 p.13 |
| Un segment Z dont aucun champ n'est obligatoire et dont tous les champs sont vides ne doit pas etre transmis. | NIST | assertions Z-NON-VIDE, une par segment Z sans champ obligatoire | PAM FR 2.11.2 §5.2 p.24 |
| Granularite des messages traduisant les mouvements. | hors de portee | enchainement de messages | PAM FR 2.11.2 §5.3.1 p.25 |
| Evenements declencheurs associes a chaque mouvement. | hors de portee | enchainement de messages | PAM FR 2.11.2 §5.3.2 p.25 |
| Evenements composites. | hors de portee | enchainement de messages | PAM FR 2.11.2 §5.3.3 p.27 |
| Emission de messages consecutifs de meme nature. | hors de portee | enchainement de messages | PAM FR 2.11.2 §5.3.4 p.27 |
| Bascules de dossier A06/A07. | hors de portee | enchainement de messages | PAM FR 2.11.2 §5.3.5 p.27 |
| Gestion des messages A16. | hors de portee | enchainement de messages | PAM FR 2.11.2 §5.3.6 p.28 |
| Mouvements historiques : ZBE-5 (indicateur), ZBE-6 (evenement d'origine), selon l'action ZBE-4. | partiel | predicat ZBE-6 selon ZBE-4 ; l'historique lui-meme est hors de portee | PAM FR 2.11.2 §5.3.7 p.28 |
| Reutilisation des identifiants de dossier, venue et mouvement. | hors de portee | historique des identifiants | PAM FR 2.11.2 §5.4.1 p.30 |
| Identifiants de dossier, venue et mouvement dans un environnement complexe. | hors de portee | organisation des domaines d'identification | PAM FR 2.11.2 §5.4.2 p.31 |
| Regles et conditions d'utilisation du segment ROL selon le role (ROL-3) et sa position. | partiel | assertions ROL-APRES-PID et ROL-APRES-PV1 : le role selon la place du ROL. ODRP « requis en A01 et A04 si connu » : non verifiable | PAM FR 2.11.2 §6.8.6 p.46 |
| Modes de venue : combinaisons de PV1-2 (classe), PV1-4 (type d'admission), PV1-10 (discipline), PV1-19 (identifiant) et PV1-21 (mode de traitement). | NIST | assertions PV1-VENUE-* | PAM FR 2.11.2 §6.10.19 p.59 |
| Correspondance entre disciplines de prestation (table 0069) et modes de traitement (table 0032). | NIST | assertions PV1-DISCIPLINE-*, en avertissement | PAM FR 2.11.2 §7.3 p.122 |
| Modes PMSI (ZFM-1 a ZFM-4) : valeurs du Guide methodologique ATIH selon le champ PMSI (MCO, SSR, HAD, PSY) ; certains codes ne valent que pour la provenance ou la destination. | hors de portee | valeurs du guide ATIH selon le champ PMSI, que le message ne porte pas | PAM FR 2.11.2 §6.17 p.79 |
| Couverture IN1/IN2/GT1 : champs et usages differents selon le cas (AMO, AMC/CMUC/AMEC, autre debiteur) — tableaux de couverture des §6.20.1 a 6.20.3. | NIST | assertions IN1-AMO-*, IN1-AMC-*, IN1-AUTRE-DEBITEUR-GT1 selon le type de debiteur IN1-2 ; les champs RE ne sont pas repris | PAM FR 2.11.2 §6.20 p.90 |
| OBX-3 : vocabulaire du Referentiel d'interoperabilite de l'ANS, a defaut LOINC — liste ouverte, les codes donnes ne sont que des exemples. | hors de portee | liste ouverte | PAM FR 2.11.2 §6.21.3 p.99 |
| OBX-6 : unites UCUM, obligatoires si OBX-2 vaut NM ou SN et que l'observation est une mesure. | partiel | predicat OBX-6 selon OBX-2 ; le controle UCUM demande un vocabulaire externe | PAM FR 2.11.2 §6.21.4 p.99 |
| MSH-21 : une repetition doit valoir 2.11^IHE_FRANCE-2.11-PAM (declaration de conformite). | NIST | assertion MSH-21-PAM-FR (approchee) | PAM FR 2.11.2 §6.5 p.34 |
| CX : contenu attendu des composants selon le type d'identifiant (INS-C, INS-NIR, INS-NIA, IPP…) — tableau des identifiants, autorites et OID. | partiel | INS : autorite, format, dates (CX-INS-*) ; IPP, NDA, venue : autorite locale | Appendix N 1.8.2 §N.1 p.6 |
| XPN-5 : civilites M. et Mme ; Mlle ne devrait pas etre utilisee. | NIST | assertion XPN-5-MLLE, en avertissement | Appendix N 1.8.2 §N.10 p.37 |

## Identifiants des messages

L'identifiant NIST d'un message est le chemin du profil 2B dans le jeu, sans extension :

- `hapi/messages/PDC/ITI-30/ACK_ALL`
- `hapi/messages/PDS/ITI-30/ADT_A28`
- `hapi/messages/PDS/ITI-30/ADT_A31`
- `hapi/messages/PDS/ITI-30/ADT_A40`
- `hapi/messages/PDS/ITI-30/ADT_A47`
- `hapi/messages/PEC/ITI-31/ACK_ALL`
- `hapi/messages/PES/ITI-31/ADT_A01`
- `hapi/messages/PES/ITI-31/ADT_A02`
- `hapi/messages/PES/ITI-31/ADT_A03`
- `hapi/messages/PES/ITI-31/ADT_A04`
- `hapi/messages/PES/ITI-31/ADT_A05`
- `hapi/messages/PES/ITI-31/ADT_A06`
- `hapi/messages/PES/ITI-31/ADT_A07`
- `hapi/messages/PES/ITI-31/ADT_A09`
- `hapi/messages/PES/ITI-31/ADT_A10`
- `hapi/messages/PES/ITI-31/ADT_A11`
- `hapi/messages/PES/ITI-31/ADT_A12`
- `hapi/messages/PES/ITI-31/ADT_A13`
- `hapi/messages/PES/ITI-31/ADT_A14`
- `hapi/messages/PES/ITI-31/ADT_A15`
- `hapi/messages/PES/ITI-31/ADT_A16`
- `hapi/messages/PES/ITI-31/ADT_A21`
- `hapi/messages/PES/ITI-31/ADT_A22`
- `hapi/messages/PES/ITI-31/ADT_A25`
- `hapi/messages/PES/ITI-31/ADT_A26`
- `hapi/messages/PES/ITI-31/ADT_A27`
- `hapi/messages/PES/ITI-31/ADT_A32`
- `hapi/messages/PES/ITI-31/ADT_A33`
- `hapi/messages/PES/ITI-31/ADT_A38`
- `hapi/messages/PES/ITI-31/ADT_A44`
- `hapi/messages/PES/ITI-31/ADT_A52`
- `hapi/messages/PES/ITI-31/ADT_A53`
- `hapi/messages/PES/ITI-31/ADT_A54`
- `hapi/messages/PES/ITI-31/ADT_A55`
- `hapi/messages/PES/ITI-31/ADT_Z99`

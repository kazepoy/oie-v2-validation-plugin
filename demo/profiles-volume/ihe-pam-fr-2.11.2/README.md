# Jeu `ihe-pam-fr-2.11.2` — IHE PAM France 2.11.2

Profils de conformite **construits** a partir des profils Gazelle `IHE_FR` (`../gazelle-interopsante-ihe-fr.zip`,
branche 2.10), alignes sur la specification **IHE PAM France 2.11.2** et sur son
**Appendix N 1.8.2** (contraintes francaises sur les types de donnees). Jeu **local** :
il n'est pas opposable comme « profil IHE ».

## Sources

| Document | Version | Fichier | SHA-256 |
|---|---|---|---|
| IHE PAM - National Extension France | 2.11.2 | `Publication-IHE_FRANCE_PAM_National_Extension_v2.11.2.pdf` | `64455d4b2d8e6d17…` |
| IHE France Constraints on HL7 data types for ITI (Appendix N) | 1.8.2 | `IHE_France_Constraints_on_HL7_data_types_for_ITI_V1.8.2.pdf` | `5e7a47eabdc9262c…` |

Publies par Interop'Sante : <https://github.com/Interop-Sante/ihe.iti.pam.fr>, repertoire `Publication/`.

## Ou trouver la justification d'un element

Chaque element decrit par la specification porte une **note d'implementation** (`ImpNote`) qui cite le
document, la section et la page, et resume la contrainte : par exemple
`PAM FR 2.11.2 §6.6 tableau PID, p.35 : R [1..*], LEN 20, IS, table 0445 (contrainte francaise) ; detail PAM FR 2.11.2 §6.6.15, p.41.`
Ces notes apparaissent sous le nom de l'element dans les profils. Un element **sans
note** n'est pas decrit par la specification francaise : il garde la contrainte du profil Gazelle.

## Comment la specification est appliquee

1. **Tableaux de segment** (chapitre 6 : PID, ROL, NK1, PV1, PV2, ACC, ZBE, ZFA, ZFP, ZFV, ZFM, ZFD,
   ZFS, OBX) : usage, cardinalite, longueur des types primitifs, type, table. Un tableau de segment est
   une base commune aux transactions : quand il laisse le choix (`O`) ou pose une condition (`C`), le
   profil de transaction plus contraint est **conserve** et la note le dit ; il est **aligne** quand la
   specification dit `R`, `RE` ou `X`. Les segments francais (ZBE, ZF*) suivent leur tableau tel quel.
   Les longueurs des types composes ne sont pas touchees : les profils Gazelle y portent la somme des
   longueurs des composants.
2. **Structure des messages** : usage et cardinalite des segments francais dans les messages ITI-31
   (§5.2, structure du message A05) ; segments francais d'ITI-30 (§4.3).
3. **Appendix N** : chaque composant des types CX, HD, PL, TS, XAD, SAD, XCN, XON, XPN, XTN, partout ou
   le type est employe. Une correction est appliquee par rapport au PDF : **`HD-3 Universal ID
   Type` porte la table 0301** (`ISO`, `DNS`, `GUID`, `UUID`…) et non 0203, qui est la table des
   *types d'identifiant* (3628 composants corriges pour accepter `&ISO`).
4. **Listes de valeurs** (`hapi/tables/HL7Table-2.5.xml`) : la table generique HL7 2.5, ou chaque liste
   francaise **remplace** la table HL7 de meme numero — la specification les dit « strictement permises
   en France » —, completee des tables `IHE-FRANCE-*`. Chaque liste est liee a ses champs. Pour un type
   code compose (CE, CWE), la table est portee aussi par le premier composant : HAPI ne verifie une table
   que sur un element primitif.
5. **Ajustements specifiques** : MSH-12 (`2.5^FRA^2.11`), MSH-21, NK1, champs
   obsoletes, et :

- PDS/ITI-30/ADT_A28.xml : ZFD retire — ADT_A28 Gazelle porte deux ZFD, le premier avant ZFA. La structure (§5.2, reprise pour ITI-30 au §4.3 p.18) n'en place qu'un, apres ZFM. (PAM FR 2.11.2 §5.2, p.24)

## Modifications par rapport a Gazelle

| Attribut | Modifications |
|---|---|
| Usage | 14821 |
| Datatype | 2023 |
| Length | 1654 |
| Table | 1542 |
| Max | 1009 |
| Min | 90 |
| ConstantValue | 70 |
| Field | 28 |
| occurrence 1 retiree | 1 |

Total : 21238 modifications sur 35 profils.

## Tables de codes

| Table | Libelle | Mode | Codes | Champs | Source |
|---|---|---|---|---|---|
| `0001` | Administrative Sex | remplace la table HL7 | 3 | PID-8 | PAM §6.6.5 p.38 |
| `0002` | Marital Status | remplace la table HL7 | 8 | PID-16 | PAM §6.6.8 p.39 |
| `0445` | Identity Reliability Code | remplace la table HL7 | 21 | PID-32 | PAM §6.6.15 p.41 |
| `0220` | Living Arrangement | remplace la table HL7 | 7 | PD1-2 | PAM §6.7.1 p.43 |
| `0136` | Yes/No Indicator | remplace la table HL7 | 2 | PD1-12, PV2-22, ZFD-4 | PAM §6.7.2 p.43; PAM §6.11.8 p.64; PAM §6.18.4 p.85 |
| `0287` | Problem/goal action code | remplace la table HL7 | 4 | ROL-2 | PAM §6.8.1 p.44 |
| `0443` | Provider role | remplace la table HL7 | 8 | ROL-3 | PAM §6.8.2 p.45 |
| `0063` | Relationship | remplace la table HL7 | 33 | NK1-3 | PAM §6.9.1 p.48-49 |
| `0131` | Contact Role | remplace la table HL7 | 11 | NK1-7 | PAM §6.9.2 p.49-50 |
| `0004` | Patient Class | remplace la table HL7 | 6 | PV1-2 | PAM §6.10.1 p.52 |
| `0116` | Bed Status | remplace la table HL7 | 2 | PV1-3.5 | PAM §6.10.2 p.52 |
| `0007` | Admission Type | remplace la table HL7 | 7 | PV1-4 | PAM §6.10.3 p.53 |
| `0023` | Admit Source | remplace la table HL7 | 8 | PV1-14 | PAM §6.10.8 p.55 |
| `0099` | VIP Indicator | remplace la table HL7 | 2 | PV1-16 | PAM §6.10.9 p.55 |
| `0032` | Charge Price Indicator | remplace la table HL7 | 30 | PV1-21 | PAM §6.10.13 p.57 |
| `0045` | Courtesy Code | remplace la table HL7 | 2 | PV1-22 | PAM §6.10.14 p.58 |
| `0112` | Discharge Disposition | remplace la table HL7 | 14 | PV1-36 | PAM §6.10.15 p.58 |
| `0117` | Account Status | remplace la table HL7 | 2 | PV1-41 | PAM §6.10.18 p.59 |
| `0129` | Accommodation Code | remplace la table HL7 | 2 | PV2-2 | PAM §6.11.1 p.62 |
| `0130` | Visit User Code | remplace la table HL7 | 28 | PV2-7 | PAM §6.11.3 p.62; PAM §6.11.3 p.63 |
| `0214` | Special Program Code | remplace la table HL7 | 3 | PV2-18 | PAM §6.11.7 p.64 |
| `0218` | Charge adjustment | remplace la table HL7 | 2 | PV2-30 | PAM §6.11.9 p.65 |
| `0430` | Mode of Arrival Code | remplace la table HL7 | 12 | PV2-38 | PAM §6.11.10 p.65 |
| `0050` | Accident Code | remplace la table HL7 | 9 | ACC-2 | PAM §6.12.2 p.66 |
| `IHE-FRANCE-ZBE-4` | Action on the movement | nouvelle | 3 | ZBE-4 | PAM §6.13.4 p.68 |
| `IHE-FRANCE-ZBE-9` | Nature of movement | nouvelle | 9 | ZBE-9 | PAM §6.13.9 p.69 |
| `IHE-FRANCE-ZFA-1` | Statut du DMP du patient | nouvelle | 3 | ZFA-1 | PAM §6.14.1 p.71 |
| `IHE-FRANCE-ZFA-9` | Information et opposition a l'alimentation du DMP | nouvelle | 3 | ZFA-9 | PAM §6.14.9 p.72 |
| `IHE-FRANCE-ZFA-11` | Information et consentement a la consultation du DMP | nouvelle | 3 | ZFA-11 | PAM §6.14.11 p.72 |
| `IHE-FRANCE-ZFP-1` | Activite socio-professionnelle | nouvelle | 8 | ZFP-1 | PAM §6.15.1 p.74 |
| `IHE-FRANCE-ZFP-2` | Categorie socioprofessionnelle | nouvelle | 42 | ZFP-2 | PAM §6.15.2 p.74; PAM §6.15.2 p.75 |
| `IHE-FRANCE-ZFV-11` | Prise en charge durant le transport | nouvelle | 3 | ZFV-11 | PAM §6.16.11 p.77-78 |
| `IHE-FRANCE-ZFM-1` | Mode d'entree PMSI | nouvelle | 6 | ZFM-1 | PAM §6.17.1 p.80 |
| `IHE-FRANCE-ZFM-2` | Mode de sortie PMSI | nouvelle | 7 | ZFM-2 | PAM §6.17.2 p.80 |
| `IHE-FRANCE-ZFM-3-4` | Mode de provenance et destination PMSI | nouvelle | 11 | ZFM-3, ZFM-4 | PAM §6.17.3 p.81 |
| `IHE-FRANCE-ZFM-5` | Passage par une structure des urgences | nouvelle | 3 | ZFM-5 | PAM §6.17.5 p.83 |
| `IHE-FRANCE-ZFD-5` | Mode d'obtention de l'identite | nouvelle | 5 | ZFD-5 | PAM §6.18.5 p.85 |
| `IHE-FRANCE-ZFD-7` | Type de justificatif d'identite | nouvelle | 14 | ZFD-7 | PAM §6.18.7 p.86 |
| `IHE-FRANCE-ZFS-6` | Mode legal de soin (psychiatrie) | nouvelle | 11 | ZFS-6 | PAM §6.19.6 p.88-89 |
| `IHE-FRANCE-ZFS-7` | Code RIM-P (psychiatrie) | nouvelle | 7 | ZFS-7 | PAM §6.19.7 p.89 |
| `0072` | Insurance plan ID / type de debiteur | remplace la table HL7 | 11 | IN1-2 | PAM §6.20.4 p.97 |
| `0148` | Type de regle de remboursement (IN3-5.1) | remplace la table HL7 | 5 | IN3-5 | PAM §6.20.5 p.98 |
| `0085` | Observation result status | remplace la table HL7 | 3 | OBX-11 | PAM §6.21.5 p.99-100 |
| `0069` | Hospital Service | remplace la table HL7 | 258 | PV1-10 | PAM §7.2 p.117-118-119-120-121-122 |
| `0203` | Identifier Type | remplace la table HL7 | 24 | CX-5, XCN-13, XON-7 | App. N §N.1 p.10 |
| `0399` | Country Code (ISO 3166-1 alpha-3) | remplace la table HL7 | 244 | XAD-6 | App. N §N.6 p.20; App. N §N.6 p.21; App. N §N.6 p.22; App. N §N.6 p.23; App. N §N.6 p.24 |
| `0190` | Address Type | remplace la table HL7 | 16 | XAD-7 | App. N §N.6 p.25 |
| `0200` | Name Type | remplace la table HL7 | 4 | XPN-7 | App. N §N.10 p.37-38 |

Listes vues et ecartees, parce qu'elles ne sont pas fermees :

- §6.21.3 p.99 : exemples de codes LOINC pour OBX-3, liste non fermee
- §6.21.4 p.99 : exemples d'unites UCUM pour OBX-6, liste non fermee
- §N.10 p.37 : civilites de XPN-5 (M., Mme), champ sans table

## Limites connues du controle

- **Segments Z** (ZBE, ZFA…) : HAPI n'a pas de modele pour eux et lit leurs champs comme un type
  generique (`Varies`). Leur longueur est controlee, pas leur table : une valeur hors des listes
  `IHE-FRANCE-*` n'est pas refusee par HAPI. Les tables sont posees dans les profils et verifiees par le moteur **NIST**.
- **Ordre des segments francais dans ITI-30** : les profils Gazelle A28 et A31 placent ZFD avant ZFA,
  la ou la structure (§5.2) met ZFA en premier ; HAPI signale alors « ZFA not found » ou « ZFS not
  found » sur un message qui ne les porte pas. Defaut herite de Gazelle, conserve ici en attendant
  d'etre traite.
- **Usage `C`** : HAPI ne verifie ni la presence ni l'absence d'un element conditionnel.

## Ce que ce jeu ne peut pas valider — a implementer avec IGAMT

Le format du chapitre 2B exprime usage, cardinalite, longueur, type et table ; il n'exprime ni
predicat, ni co-contrainte entre champs, ni enchainement de messages. Ce qui suit est donc dans la
specification mais **hors de portee de ce jeu**. Voir `doc/igamt-reflexion.md`.

Le dossier [`nist/`](nist/) porte le jeu traduit au format de validation NIST (celui d'IGAMT),
avec ses predicats et assertions. Sa matrice de couverture,
[`nist/README.md`](nist/README.md), donne le sort de chacune des regles ci-dessous : verifiee par
NIST, en partie, a faire, ou hors de portee.

### Usages conditionnels

Un usage `C` porte une condition, ecrite sous le tableau ou dans la sous-section du champ. Le profil la
garde en `C` : HAPI ne verifie alors ni presence ni absence.

| Element | Usage | Condition decrite |
|---|---|---|
| PID-7 Date/Time of Birth | C | PAM FR 2.11.2 §6.6.4 p.38 |
| PID-8 Administrative Sex | C | PAM FR 2.11.2 §6.6.5 p.38 |
| PID-11 Patient Address | C | PAM FR 2.11.2 §6.6.6 p.38 |
| PID-18 Patient Account Number | C | PAM FR 2.11.2 §6.6.9 p.39 |
| PID-25 Birth Order | C | PAM FR 2.11.2 §6.6.12 p.40 |
| PID-31 Identity Unknown Indicator | CE | PAM FR 2.11.2 §6.6 p.35 |
| PID-33 Last Update Date/Time | C | PAM FR 2.11.2 §6.6 p.35 |
| PID-35 Species Code | C | PAM FR 2.11.2 §6.6 p.35 |
| PID-36 Breed Code | C | PAM FR 2.11.2 §6.6 p.35 |
| ROL-1 Role Instance ID | C | PAM FR 2.11.2 §6.8 p.44 |
| PV1-3 Assigned Patient Location | C | PAM FR 2.11.2 §6.10.2 p.52 |
| PV1-5 Preadmit Number | C | PAM FR 2.11.2 §6.10.4 p.53 |
| PV1-6 Prior Patient Location | C | PAM FR 2.11.2 §6.10 p.51 |
| PV1-11 Temporary Location | C | PAM FR 2.11.2 §6.10 p.51 |
| PV1-19 Visit Number | C | PAM FR 2.11.2 §6.10.11 p.55 |
| PV1-42 Pending Location | C | PAM FR 2.11.2 §6.10 p.51 |
| PV2-1 Prior Pending Location | C | PAM FR 2.11.2 §6.11 p.61 |
| PV2-47 Expected LOA Return Date/Time | C | PAM FR 2.11.2 §6.11.11 p.65 |
| ZBE-6 Original trigger event code | C | PAM FR 2.11.2 §6.13.6 p.68 |
| ZBE-7 Ward of medical responsibility in the period starting with this movement | C | PAM FR 2.11.2 §6.13.7 p.68 |
| ZBE-8 Ward of care responsibility in the period starting with this movement | C | PAM FR 2.11.2 §6.13.8 p.69 |
| OBX-5 Valeur de l’observation | C | PAM FR 2.11.2 §6.21 p.98 |
| OBX-6 Unité | C | PAM FR 2.11.2 §6.21.4 p.99 |
| CX-7 Effective Date | C | Appendix N 1.8.2 §N.1 p.11 |
| HD-2 Universal ID | C | Appendix N 1.8.2 §N.3 p.13 |
| HD-3 Universal ID Type | C | Appendix N 1.8.2 §N.3 p.13 |
| PL-6 Person Location Type | C | Appendix N 1.8.2 §N.4 p.15 |
| XCN-10 Name Type Code | C | Appendix N 1.8.2 §N.8 p.31 |
| XCN-13 Identifier Type Code | C | Appendix N 1.8.2 §N.8 p.31 |
| XPN-1 Family Name | C | Appendix N 1.8.2 §N.10 p.36 |
| XPN-2 Given Name | C | Appendix N 1.8.2 §N.10 p.36 |
| XPN-3 Second and Further Given Names or Initials Thereof | C | Appendix N 1.8.2 §N.10 p.37 |
| XTN-4 Email Address | C | Appendix N 1.8.2 §N.11 p.40 |
| XTN-12 Unformatted Telephone number | C | Appendix N 1.8.2 §N.11 p.40 |

### Co-contraintes et regles de gestion

| Reference | Regle |
|---|---|
| PAM FR 2.11.2 §6.6.15 p.41 | PID-32 doit valoir VALI des que PID-3 porte le matricule INS ; sinon le recepteur integre le message sans l'INS. Co-contrainte entre PID-3 (CX-5 = INS) et PID-32. |
| PAM FR 2.11.2 §6.6.15 p.41 | Statuts et attributs d'identite de la table 0445 : combinaisons autorisees decrites par le Referentiel National d'Identitovigilance ; un attribut va dans une repetition de PID-32. |
| PAM FR 2.11.2 §4.4.1 p.18 | Passage d'un INS-NIA a un INS-NIR : les deux peuvent figurer en PID-3 ; evenement A31. |
| PAM FR 2.11.2 §4.4.2 p.18 | Modification de l'INS-NIR : evenement A47, ancien matricule en MRG-1, nouveau en PID-3. |
| PAM FR 2.11.2 §4.4.3 p.19 | Suppression d'un INS : A47, INS en MRG-1, PID-3 avec l'IPP et "" pour l'OID de l'INS ; le statut de l'identite suit en PID-32 ou dans un A31. |
| PAM FR 2.11.2 §4.4.4 p.19 | Changement de statut identite qualifiee vers identite recuperee : evenement A47. |
| PAM FR 2.11.2 §2.1 p.13 | Evenements et options obligatoires d'ITI-30 en France (option Merge requise : A28, A31, A47, A40). |
| PAM FR 2.11.2 §2.2 p.13 | Evenements et options obligatoires d'ITI-31 en France, selon les options de l'acteur. |
| PAM FR 2.11.2 §5.2 p.24 | Un segment Z dont aucun champ n'est obligatoire et dont tous les champs sont vides ne doit pas etre transmis. |
| PAM FR 2.11.2 §5.3.1 p.25 | Granularite des messages traduisant les mouvements. |
| PAM FR 2.11.2 §5.3.2 p.25 | Evenements declencheurs associes a chaque mouvement. |
| PAM FR 2.11.2 §5.3.3 p.27 | Evenements composites. |
| PAM FR 2.11.2 §5.3.4 p.27 | Emission de messages consecutifs de meme nature. |
| PAM FR 2.11.2 §5.3.5 p.27 | Bascules de dossier A06/A07. |
| PAM FR 2.11.2 §5.3.6 p.28 | Gestion des messages A16. |
| PAM FR 2.11.2 §5.3.7 p.28 | Mouvements historiques : ZBE-5 (indicateur), ZBE-6 (evenement d'origine), selon l'action ZBE-4. |
| PAM FR 2.11.2 §5.4.1 p.30 | Reutilisation des identifiants de dossier, venue et mouvement. |
| PAM FR 2.11.2 §5.4.2 p.31 | Identifiants de dossier, venue et mouvement dans un environnement complexe. |
| PAM FR 2.11.2 §6.8.6 p.46 | Regles et conditions d'utilisation du segment ROL selon le role (ROL-3) et sa position. |
| PAM FR 2.11.2 §6.10.19 p.59 | Modes de venue : combinaisons de PV1-2 (classe), PV1-4 (type d'admission), PV1-10 (discipline), PV1-19 (identifiant) et PV1-21 (mode de traitement). |
| PAM FR 2.11.2 §7.3 p.122 | Correspondance entre disciplines de prestation (table 0069) et modes de traitement (table 0032). |
| PAM FR 2.11.2 §6.17 p.79 | Modes PMSI (ZFM-1 a ZFM-4) : valeurs du Guide methodologique ATIH selon le champ PMSI (MCO, SSR, HAD, PSY) ; certains codes ne valent que pour la provenance ou la destination. |
| PAM FR 2.11.2 §6.20 p.90 | Couverture IN1/IN2/GT1 : champs et usages differents selon le cas (AMO, AMC/CMUC/AMEC, autre debiteur) — tableaux de couverture des §6.20.1 a 6.20.3. |
| PAM FR 2.11.2 §6.21.3 p.99 | OBX-3 : vocabulaire du Referentiel d'interoperabilite de l'ANS, a defaut LOINC — liste ouverte, les codes donnes ne sont que des exemples. |
| PAM FR 2.11.2 §6.21.4 p.99 | OBX-6 : unites UCUM, obligatoires si OBX-2 vaut NM ou SN et que l'observation est une mesure. |
| PAM FR 2.11.2 §6.5 p.34 | MSH-21 : une repetition doit valoir 2.11^IHE_FRANCE-2.11-PAM (declaration de conformite). |
| Appendix N 1.8.2 §N.1 p.6 | CX : contenu attendu des composants selon le type d'identifiant (INS-C, INS-NIR, INS-NIA, IPP…) — tableau des identifiants, autorites et OID. |
| Appendix N 1.8.2 §N.10 p.37 | XPN-5 : civilites M. et Mme ; Mlle ne devrait pas etre utilisee. |

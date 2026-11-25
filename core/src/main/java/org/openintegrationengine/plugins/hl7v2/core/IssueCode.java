package org.openintegrationengine.plugins.hl7v2.core;

/**
 * La nature d'une anomalie, en code machine : ce qui entre dans une statistique, ce qu'un
 * collecteur classe, ce que l'editeur relie a l'element du profil. Le texte du moteur reste a
 * cote, pour un humain.
 *
 * Les codes de structure viennent des deux moteurs ; {@link #PREDICATE}, {@link #ASSERTION} et
 * {@link #FORMAT} du moteur NIST seul, qui est le seul a lire une regle.
 */
public enum IssueCode {
    /** Element requis (R) absent. */
    MISSING_REQUIRED,
    /** Element requis mais pouvant etre vide (RE) absent : un avertissement, jamais une erreur. */
    MISSING_RECOMMENDED,
    /** Element interdit (X, ou retire W) present. */
    FORBIDDEN_PRESENT,
    /** Moins d'occurrences que le minimum. */
    CARDINALITY_MIN,
    /** Plus d'occurrences que le maximum. */
    CARDINALITY_MAX,
    /** Valeur plus longue que la longueur du profil. */
    LENGTH,
    /** Le type de donnee du message n'est pas celui du profil, ou un segment Z lu en bloc par HAPI. */
    DATATYPE,
    /** Code hors de la table, ou du jeu de valeurs, lie a l'element. */
    CODE_NOT_IN_TABLE,
    /** Element present dans le message et absent du profil. */
    NOT_IN_PROFILE,
    /** La structure du message ne suit pas le profil : type de message, groupe, segment inattendu. */
    STRUCTURE,
    /** Valeur differente de la constante du profil. */
    CONSTANT,
    /** Valeur hors du format (expression reguliere) ; NIST seul. */
    FORMAT,
    /** Predicat d'un usage conditionnel non satisfait ; NIST seul. */
    PREDICATE,
    /** Assertion, co-contrainte ou regle de contenu non satisfaite ; NIST seul. */
    ASSERTION,
    /** Defaut des fichiers du jeu, pas du message : signale sans invalider. */
    SPEC_ERROR,
    /** Le moteur n'a pas pu conclure sur cet element. */
    ENGINE_ERROR,
    /** Rien de ce qui precede : le texte seul dit de quoi il s'agit. */
    OTHER
}

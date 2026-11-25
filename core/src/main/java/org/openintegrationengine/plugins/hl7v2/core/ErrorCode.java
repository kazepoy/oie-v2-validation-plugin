package org.openintegrationengine.plugins.hl7v2.core;

/**
 * Ce qui empeche une validation d'avoir lieu, en code machine. Avec le statut HTTP que le service
 * en fait : un collecteur classe par ce code, jamais par le texte du message.
 */
public enum ErrorCode {
    /** Corps de requete vide. */
    EMPTY_BODY(400),
    /** Le parseur HAPI ne lit pas le message. */
    UNREADABLE_MESSAGE(400),
    /** Pas de segment MSH lisible, ou MSH-9 sans type, evenement ni structure. */
    UNREADABLE_MSH(400),
    /** Le jeu de profils n'est pas donne. */
    SET_REQUIRED(400),
    /** Un parametre est mal forme : profil qualifie du jeu, moteur inconnu, nom invalide. */
    INVALID_PARAMETER(400),
    /** Le jeu demande n'existe pas. */
    UNKNOWN_SET(404),
    /** Le profil impose n'est pas dans le jeu. */
    UNKNOWN_PROFILE(404),
    /** Aucun profil du jeu ne declare le MSH-9 du message. */
    NO_PROFILE_FOR_MESSAGE(404),
    /** Plusieurs profils du jeu repondent au message et le jeu ne tranche pas. */
    AMBIGUOUS_PROFILE(409),
    /** Le moteur demande ne couvre pas ce profil (pas de nist/, pas de 2B). */
    ENGINE_NOT_AVAILABLE(422),
    /** Le moteur a echoue sur ce message ou ce profil : aucun verdict. */
    VALIDATION_FAILED(422),
    /** Le nom existe deja, le jeu est un repertoire qu'on ne remplace pas, etc. */
    CONFLICT(409),
    /** Une archive deposee n'est pas un jeu utilisable. */
    INVALID_SET(422),
    /** L'administration n'est pas ouverte ou la cle est fausse. */
    FORBIDDEN(403);

    private final int status;

    ErrorCode(int status) {
        this.status = status;
    }

    /** Le statut HTTP que le service rend pour ce code. */
    public int status() {
        return status;
    }
}

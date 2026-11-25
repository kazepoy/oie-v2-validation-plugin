package org.openintegrationengine.plugins.hl7v2.core;

import java.util.List;

/**
 * Une validation qui n'a pas pu avoir lieu : message illisible, jeu ou profil inconnu, moteur
 * indisponible. Ce n'est <b>pas</b> un verdict — un message non conforme est un {@link Report},
 * pas une exception. Le service en fait une reponse 4xx ; le code dit quoi, le message pourquoi.
 */
public final class Refusal extends RuntimeException {

    private final ErrorCode code;
    private final List<String> candidates;

    public Refusal(ErrorCode code, String message) {
        this(code, message, List.of(), null);
    }

    public Refusal(ErrorCode code, String message, Throwable cause) {
        this(code, message, List.of(), cause);
    }

    public Refusal(ErrorCode code, String message, List<String> candidates, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.candidates = List.copyOf(candidates);
    }

    public ErrorCode code() {
        return code;
    }

    /** Le statut HTTP du code. */
    public int status() {
        return code.status();
    }

    /** Les profils en concurrence, pour une ambiguite ; vide sinon. */
    public List<String> candidates() {
        return candidates;
    }

    /** Message exploitable meme quand la cause n'en porte pas. */
    public static String messageOf(Throwable cause) {
        if (cause == null) {
            return "Unknown error";
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}

package org.openintegrationengine.plugins.hl7v2.core;

/** Petites manipulations d'un message ER7. */
public final class Hl7 {

    private Hl7() {
    }

    /** Fins de ligne en CR, comme la norme l'exige ; LF, CRLF et CR sont acceptes en entree. */
    public static String eolToCr(String hl7) {
        return hl7.replace("\r\n", "\r").replace('\n', '\r').trim();
    }

    /** Fins de ligne en LF, pour lire ou comparer. */
    public static String eolToLf(String hl7) {
        return hl7.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}

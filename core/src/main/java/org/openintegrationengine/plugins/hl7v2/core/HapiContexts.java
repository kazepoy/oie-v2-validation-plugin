package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.util.idgenerator.NanoTimeGenerator;

/**
 * Les contextes HAPI du coeur : <b>un par fil d'execution</b>.
 *
 * Un contexte porte un parseur, qui n'est pas thread-safe — le {@code PipeParser} alimente une
 * HashMap d'instance non synchronisee, le cache des StructureDefinition par type de message, que
 * deux requetes simultanees corrompent. Plutot qu'un verrou global, qui mettait tout le service
 * en file indienne, chaque fil a le sien.
 */
public final class HapiContexts {

    private static final ThreadLocal<HapiContext> CONTEXTS = ThreadLocal.withInitial(HapiContexts::nonValidating);

    private HapiContexts() {
    }

    /** Le contexte du fil courant. */
    public static HapiContext current() {
        return CONTEXTS.get();
    }

    /** Un contexte neuf, sans validation a l'analyse : c'est le profil qui juge, pas HAPI. */
    public static HapiContext nonValidating() {
        HapiContext context = new DefaultHapiContext();

        // La conformite est verifiee par le conformance profile, pas par les regles de
        // validation par defaut de HAPI : les laisser actives ferait echouer le parse avant que
        // le profil ne soit lu, et sur ses propres criteres.
        context.getParserConfiguration().setValidating(false);

        // Generateur des MSH-10 que HAPI pose sur les ACK qu'il construit. Le defaut,
        // FileBasedHiLoGenerator, persiste un compteur dans un fichier du repertoire de travail,
        // qui n'a pas a etre inscriptible. NanoTimeGenerator reste en memoire et ne se repete pas
        // d'un redemarrage a l'autre ; ses 19 chiffres tiennent dans les 20 de MSH-10.
        context.getParserConfiguration().setIdGenerator(new NanoTimeGenerator());

        return context;
    }
}

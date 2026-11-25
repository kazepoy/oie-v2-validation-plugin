package org.openintegrationengine.plugins.hl7v2.core;

import java.util.List;

/**
 * Le compte rendu d'une validation : le <b>rapport v2</b> (doc/specification/report.md du studio).
 *
 * Les noms des champs sont ceux du rapport v1 du service, completes : un lecteur du v1 (le
 * collecteur) continue de lire {@code valid}, {@code profile}, {@code engine} et
 * {@code issues[].severity/location/message} ; le v2 ajoute {@code schema}, {@code set},
 * {@code counts} et, sur chaque anomalie, {@code code}, {@code element}, {@code table} et
 * {@code value}.
 *
 * <b>Donnees patient</b> : {@code code}, {@code element}, {@code table} et {@code location}
 * n'en portent jamais ; {@code value} et {@code message} peuvent en porter et restent dans le
 * detail, jamais dans une statistique ni un journal.
 *
 * @param schema             {@value #SCHEMA}
 * @param valid              aucune anomalie de severite ERROR : un avertissement n'invalide pas
 * @param acknowledgmentCode MSA-1 du ACK : AA (valide) ou AE (invalide)
 * @param profile            identifiant qualifie du profil applique, {@code <jeu>/<profil>}
 * @param messageType        MSH-9 du message, {@code ADT^A01^ADT_A01}
 * @param hl7Version         MSH-12-1
 * @param messageControlId   MSH-10
 * @param engine             moteur applique : HAPI ou NIST, un seul par rapport
 * @param set                le jeu, avec sa version et son empreinte : contre quoi le verdict a ete rendu
 * @param counts             nombre d'erreurs et d'avertissements
 * @param issues             les anomalies relevees par ce moteur
 */
public record Report(
        String schema,
        boolean valid,
        String acknowledgmentCode,
        String profile,
        String messageType,
        String hl7Version,
        String messageControlId,
        String engine,
        SetRef set,
        Counts counts,
        List<Issue> issues) {

    public static final String SCHEMA = "oie.hl7v2.report/2";
    public static final String HAPI = "HAPI";
    public static final String NIST = "NIST";
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";

    /** Le jeu contre lequel le message a ete valide. */
    public record SetRef(String name, String version, String hash) {
    }

    public record Counts(long error, long warning) {
    }

    /**
     * Une anomalie.
     *
     * @param severity ERROR ou WARNING
     * @param location emplacement en notation HAPI, {@code PID-3(1)-5}, ou vide (message entier)
     * @param message  texte du moteur, pour un humain ; peut citer une valeur du message
     * @param engine   HAPI ou NIST
     * @param code     la nature, en code machine
     * @param element  l'element du profil, {@code PID.3.5} : segment ou type puis positions, sans
     *                 occurrence — l'identifiant du modele du studio et de son editeur
     * @param table    table ou jeu de valeurs cite ({@code 0203}), vide sinon
     * @param value    valeur refusee citee par le moteur, vide sinon ; peut etre une donnee patient
     */
    public record Issue(String severity, String location, String message, String engine,
                        IssueCode code, String element, String table, String value) {

        public boolean hasError() {
            return ERROR.equals(severity);
        }
    }

    public Report {
        issues = List.copyOf(issues);
    }

    /** Produit un rapport d'un seul moteur ; verdict, ACK et compteurs sont deduits des anomalies. */
    public static Report of(String profile, String messageType, String hl7Version,
                            String messageControlId, String engine, SetRef set, List<Issue> issues) {
        if (!HAPI.equals(engine) && !NIST.equals(engine)) {
            throw new IllegalArgumentException("Unknown engine: " + engine);
        }
        for (Issue issue : issues) {
            if (!engine.equals(issue.engine())) {
                throw new IllegalArgumentException("A report contains issues from one engine only");
            }
            if (!ERROR.equals(issue.severity()) && !WARNING.equals(issue.severity())) {
                throw new IllegalArgumentException("Unknown severity: " + issue.severity());
            }
        }
        long errors = issues.stream().filter(Issue::hasError).count();
        boolean valid = errors == 0;
        return new Report(SCHEMA, valid, valid ? "AA" : "AE", profile, messageType, hl7Version, messageControlId,
                engine, set, new Counts(errors, issues.size() - errors), issues);
    }

    public long errorCount() {
        return counts.error();
    }

    public long warningCount() {
        return counts.warning();
    }

    /** Rendu texte, pour un humain : la page web et un curl sans Accept JSON. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(valid
                ? "Message valide — profil " + profile
                : "Message invalide — profil " + profile + " — " + errorCount() + " erreur(s)");
        if (warningCount() > 0) {
            sb.append(" — ").append(warningCount()).append(" avertissement(s)");
        }
        sb.append("\n\n");
        sb.append("type       : ").append(orDash(messageType)).append('\n');
        sb.append("version    : ").append(orDash(hl7Version)).append('\n');
        sb.append("control id : ").append(orDash(messageControlId)).append('\n');
        sb.append("moteur     : ").append(engine).append('\n');
        sb.append("ACK        : ").append(acknowledgmentCode).append("\n\n");

        if (issues.isEmpty()) {
            sb.append("Aucune anomalie relevee par le profil.\n");
            return sb.toString();
        }
        for (Issue issue : issues) {
            sb.append(issue.severity()).append("  ");
            if (!issue.location().isEmpty()) {
                sb.append(issue.location()).append(" - ");
            }
            sb.append(issue.message()).append(" [").append(issue.engine()).append("]\n");
        }
        return sb.toString();
    }

    private static String orDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }
}

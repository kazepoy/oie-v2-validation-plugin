package org.openintegrationengine.plugins.hl7v2.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Des textes des moteurs aux codes du rapport v2.
 *
 * Les deux moteurs ne disent la nature d'une anomalie que dans leur texte : les gabarits de
 * {@code DefaultValidator} (hapi-base 2.6.0) pour HAPI, les categories et gabarits du
 * {@code reference.conf} du moteur NIST 1.7.3. Cette classe est la seule a les connaitre ; un
 * changement de version d'un moteur se traite ici, et {@code IssueCodesTest} le dit.
 *
 * Ce qu'elle extrait aussi : la table citee, la valeur refusee (la seule donnee patient possible,
 * a part le texte) et l'element du profil depuis l'emplacement.
 */
public final class IssueCodes {

    // Une citation s'ouvre et se ferme hors d'un mot : 'L'HERMITE' est une seule valeur, et
    // l'apostrophe de « Patient's » n'ouvre rien. Meme regle que synthese.py et le collecteur.
    private static final Pattern QUOTED = Pattern.compile("(?<![\\p{L}\\p{N}])'(.*?)'(?![\\p{L}\\p{N}])", Pattern.DOTALL);
    private static final Pattern HAPI_TABLE = Pattern.compile("not found in table\\s+(\\S+?)[,.]?(\\s|$)");
    private static final Pattern NIST_VALUE_SET = Pattern.compile("[Vv]alue [Ss]et:?\\s*\\[?([A-Za-z0-9_.-]+)\\]?");
    private static final Pattern NIST_CARDINALITY = Pattern.compile("range of \\[(\\d+), (\\d+|\\*)]; (\\d+) occurrences");
    private static final Pattern OCCURRENCE = Pattern.compile("\\(\\d+\\)");
    private static final Pattern ELEMENT = Pattern.compile("[A-Z][A-Z0-9]{2}(?:-[1-9][0-9]*){0,3}");
    private static final Pattern NIST_CONSTANT_FOUND = Pattern.compile("but '(.*?)' was found", Pattern.DOTALL);

    private IssueCodes() {
    }

    /** Le code d'une anomalie du {@code DefaultValidator} de HAPI, d'apres son texte. */
    public static IssueCode fromHapi(String message) {
        String m = message == null ? "" : message;
        if (m.contains("not found in table")) {
            return IssueCode.CODE_NOT_IN_TABLE;
        }
        if (m.startsWith("Required element") && m.endsWith("is missing")) {
            return IssueCode.MISSING_REQUIRED;
        }
        if (m.contains("not found in message")) {
            return IssueCode.MISSING_REQUIRED;
        }
        if (m.contains("specified as not used (X)")) {
            return IssueCode.FORBIDDEN_PRESENT;
        }
        if (m.contains("must have at least")) {
            return IssueCode.CARDINALITY_MIN;
        }
        if (m.contains("must have no more than")) {
            return IssueCode.CARDINALITY_MAX;
        }
        if (m.contains("exceeds max") || m.contains("exceeds maximum")) {
            return IssueCode.LENGTH;
        }
        if (m.contains("doesn't equal constant value")) {
            return IssueCode.CONSTANT;
        }
        if (m.contains("doesn't match profile datatype") || m.contains("has type primitive")
                || m.contains("has primitive type")) {
            return IssueCode.DATATYPE;
        }
        if (m.contains("appears in the message but not in the profile")
                || m.contains("not defined in the profile")) {
            return IssueCode.NOT_IN_PROFILE;
        }
        if (m.contains("doesn't match profile type") || m.contains("Mismatch between")
                || m.contains("More components in profile") || m.contains("More subcomponents in profile")) {
            return IssueCode.STRUCTURE;
        }
        return IssueCode.OTHER;
    }

    /**
     * Le code d'une entree du rapport NIST, d'apres sa categorie (reference.conf du moteur) puis,
     * pour les categories qui en melangent plusieurs, son texte.
     */
    public static IssueCode fromNist(String category, String classification, String description) {
        String c = category == null ? "" : category;
        String d = description == null ? "" : description;
        if ("Specification Error".equals(classification)) {
            return IssueCode.SPEC_ERROR;
        }
        switch (c) {
            case "Usage":
                if (d.contains("is missing. Depending on the use case")) {
                    return IssueCode.MISSING_RECOMMENDED;
                }
                if (d.contains("is present whereas")) {
                    return IssueCode.FORBIDDEN_PRESENT;
                }
                if (d.contains("is missing")) {
                    return IssueCode.MISSING_REQUIRED;
                }
                return IssueCode.OTHER;
            case "Cardinality": {
                Matcher m = NIST_CARDINALITY.matcher(d);
                if (m.find()) {
                    try {
                        long found = Long.parseLong(m.group(3));
                        long min = Long.parseLong(m.group(1));
                        return found < min ? IssueCode.CARDINALITY_MIN : IssueCode.CARDINALITY_MAX;
                    } catch (NumberFormatException e) {
                        return IssueCode.CARDINALITY_MAX;
                    }
                }
                return IssueCode.CARDINALITY_MAX;
            }
            case "Length":
                return IssueCode.LENGTH;
            case "Format":
            case "Unescaped Separator":
                return IssueCode.FORMAT;
            case "Constant Value":
                return IssueCode.CONSTANT;
            case "Extra":
                return IssueCode.NOT_IN_PROFILE;
            case "Unexpected":
            case "Invalid Content":
            case "Unresolved Field Model":
                return IssueCode.STRUCTURE;
            case "Predicate Failure":
                return IssueCode.PREDICATE;
            case "Constraint Failure":
            case "Content Failure":
            case "High-level Content Error":
            case "CoConstraint Failure":
            case "Co-Constraint":
                return IssueCode.ASSERTION;
            case "Code Not Found":
            case "EVS":
            case "Single Code Failure":
            case "Coded Element":
            case "No Code Usage":
                return IssueCode.CODE_NOT_IN_TABLE;
            case "External Value Set Spec Error":
                return IssueCode.ENGINE_ERROR;
            default:
                return d.contains("is not member of the value set") ? IssueCode.CODE_NOT_IN_TABLE : IssueCode.OTHER;
        }
    }

    /** La table ou le jeu de valeurs cite : {@code HL70399} devient {@code 0399}. Vide sinon. */
    public static String tableOf(String message) {
        if (message == null) {
            return "";
        }
        Matcher h = HAPI_TABLE.matcher(message);
        if (h.find()) {
            return normaliseTable(h.group(1));
        }
        Matcher n = NIST_VALUE_SET.matcher(message);
        if (n.find()) {
            return normaliseTable(n.group(1));
        }
        return "";
    }

    private static String normaliseTable(String id) {
        String t = id.replaceAll("[,.\\]]+$", "");
        if (t.toUpperCase(Locale.ROOT).startsWith("HL7") && t.length() == 7 && t.substring(3).chars().allMatch(Character::isDigit)) {
            return t.substring(3);
        }
        return t;
    }

    /** La premiere valeur citee entre apostrophes par le moteur ; vide si aucune. */
    public static String valueOf(String message) {
        if (message == null) {
            return "";
        }
        Matcher m = QUOTED.matcher(message);
        return m.find() ? m.group(1) : "";
    }

    /**
     * L'element du profil depuis un emplacement en notation HAPI : {@code PID-3(1)-5} donne
     * {@code PID.3.5}, {@code PID} donne {@code PID}, un emplacement vide reste vide.
     */
    public static String elementOf(String location) {
        if (location == null || location.isEmpty()) {
            return "";
        }
        String s = OCCURRENCE.matcher(location).replaceAll("");
        // Un chemin de groupe (ADT_A01/PATIENT) ou de message n'est pas un element.
        if (s.contains("/")) {
            s = s.substring(s.lastIndexOf('/') + 1);
        }
        return ELEMENT.matcher(s).matches() ? s.replace('-', '.') : "";
    }

    /**
     * Emplacement NIST vers la notation HAPI, celle du ACK et des rapports : {@code PID[1]-3[2].4.3}
     * donne {@code PID-3(1)-4-3}, {@code MSH[1]} donne {@code MSH}. Une assertion de niveau
     * message ({@code ADT_A01[1]}) n'a pas d'emplacement.
     */
    public static String locationOfNistPath(String path) {
        if (path == null) {
            return "";
        }
        Matcher m = NIST_PATH.matcher(path.substring(path.lastIndexOf('/') + 1));
        if (!m.matches()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(m.group(1));
        if (m.group(2) != null) {
            sb.append('-').append(m.group(2)).append('(').append(Integer.parseInt(m.group(3)) - 1).append(')');
            for (String part : m.group(4).split("\\.")) {
                if (!part.isEmpty()) {
                    sb.append('-').append(part);
                }
            }
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    // PID[1]-3[2].4.3 : segment, champ et repetition, composant, sous-composant.
    private static final Pattern NIST_PATH =
            Pattern.compile("([A-Z][A-Z0-9]{2})\\[\\d+](?:-(\\d+)\\[(\\d+)]((?:\\.\\d+)*))?");

    /** Une anomalie HAPI complete : code, element, table et valeur deduits du texte. */
    public static Report.Issue hapiIssue(String location, String message) {
        String loc = location == null ? "" : location;
        String text = message == null ? "" : message;
        return new Report.Issue(Report.ERROR, loc, text, Report.HAPI, fromHapi(text), elementOf(loc),
                tableOf(text), valueOf(text));
    }

    /** Une anomalie NIST complete. */
    public static Report.Issue nistIssue(String severity, String path, String category, String classification,
                                         String description) {
        String loc = locationOfNistPath(path);
        String text = description == null ? "" : description;
        Matcher found = NIST_CONSTANT_FOUND.matcher(text);
        String value = "Constant Value".equals(category) && found.find() ? found.group(1) : valueOf(text);
        return new Report.Issue(severity, loc, text, Report.NIST, fromNist(category, classification, text),
                elementOf(loc), tableOf(text), value);
    }
}

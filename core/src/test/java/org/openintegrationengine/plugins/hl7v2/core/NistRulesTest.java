package org.openintegrationengine.plugins.hl7v2.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les regles de tools/pamfr/rules.py, une par une, dans le moteur NIST : chaque regle sur un message
 * qui la viole, et sur un message qui la respecte.
 *
 * Le message de base est l'exemple Gazelle ADT_A01, modifie champ par champ ; une regle est
 * reperee par son identifiant d'assertion (PID-INS-VALI) ou, pour un predicat, par l'emplacement
 * de l'element (PID-7). Si une regle change, ce test le dit avant les flux.
 */
class NistRulesTest {

    private static final String PROFILE = "PES/ITI-31/ADT_A01";
    private static final String INS = "180012A09912345^^^ASIP-SANTE-INS-NIR&1.2.250.1.213.1.4.8&ISO^INS";
    private static final String IPP = "DDS-14004^^^DDS&1.3.6.1.4.1.12559.11.1.4.1.2&ISO^PI";
    private static final String GT1 = "GT1|1||EMPLOYEUR SA";

    private static final Validator validator = new Validator();
    private static ProductSet products;
    private static List<String> base;

    @BeforeAll
    static void setUp() throws IOException {
        products = ProductSet.open(Fixtures.PAM_ZIP);
        base = Arrays.asList(Files.readString(
                Path.of("core/src/test/resources/messages/adt-a01-ihe-pam-fr.hl7"),
                StandardCharsets.ISO_8859_1).replace("\n", "\r").strip().split("\r"));
    }

    @AfterAll
    static void closeSet() {
        products.close();
    }

    // ─── Modifications du message ────────────────────────────────────────────

    /** Remplace le champ n du premier segment seg (MSH-n compte le separateur comme champ 1). */
    private static UnaryOperator<List<String>> set(String seg, int n, String value) {
        return msg -> {
            List<String> out = new ArrayList<>(msg);
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i).startsWith(seg + "|")) {
                    List<String> f = new ArrayList<>(Arrays.asList(out.get(i).split("\\|", -1)));
                    int idx = seg.equals("MSH") ? n - 1 : n;
                    while (f.size() <= idx) {
                        f.add("");
                    }
                    f.set(idx, value);
                    out.set(i, String.join("|", f));
                    break;
                }
            }
            return out;
        };
    }

    /** Insere un segment juste apres le premier segment after. */
    private static UnaryOperator<List<String>> after(String after, String segment) {
        return msg -> {
            List<String> out = new ArrayList<>(msg);
            for (int i = 0; i < out.size(); i++) {
                if (out.get(i).startsWith(after + "|")) {
                    out.add(i + 1, segment);
                    break;
                }
            }
            return out;
        };
    }

    private static String rol(String role) {
        return "ROL||AD|" + role + "|123^MEDECIN^JEAN";
    }

    private static String in1(String debtor, String f3, String f17, String f31) {
        String[] f = new String[32];
        Arrays.fill(f, "");
        f[0] = "IN1";
        f[1] = "1";
        f[2] = debtor;
        f[3] = f3;
        f[17] = f17;
        f[31] = f31;
        return String.join("|", f);
    }

    @SafeVarargs
    private static Arguments rule(String name, List<String> present, List<String> absent,
                                  UnaryOperator<List<String>>... changes) {
        return Arguments.of(name, List.of(changes), present, absent);
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                rule("INS sans identite qualifiee", List.of("PID-INS-VALI"), List.of("CX-INS-AUTORITE", "CX-INS-FORMAT"),
                        set("PID", 3, IPP + "~" + INS), set("PID", 32, "PROV")),
                rule("INS et identite qualifiee", List.of(), List.of("PID-INS-VALI", "CX-INS-AUTORITE", "CX-INS-FORMAT", "PID-7(0)"),
                        set("PID", 3, IPP + "~" + INS), set("PID", 32, "VALI")),
                rule("INS qualifie sans date de naissance", List.of("PID-7(0)"), List.of(),
                        set("PID", 3, IPP + "~" + INS), set("PID", 32, "VALI"), set("PID", 7, "")),
                rule("sans INS ni date de naissance", List.of(), List.of("PID-7(0)"),
                        set("PID", 7, "")),
                rule("INS d'une autre autorite", List.of("CX-INS-AUTORITE"), List.of(),
                        set("PID", 3, INS.replace("1.4.8", "1.4.3")), set("PID", 32, "VALI")),
                rule("INS sans cle", List.of("CX-INS-FORMAT"), List.of(),
                        set("PID", 3, INS.replace("180012A09912345", "1800120991234")), set("PID", 32, "VALI")),
                rule("INS avec date de validite", List.of("CX-INS-DATES"), List.of(),
                        set("PID", 3, INS + "^^20200101"), set("PID", 32, "VALI")),
                rule("civilite Mlle", List.of("XPN-5-MLLE"), List.of(), set("PID", 5, "Fabbri^Giulia^^^Mlle^^L")),
                rule("civilite Mme", List.of(), List.of("XPN-5-MLLE"), set("PID", 5, "Fabbri^Giulia^^^Mme^^L")),
                rule("hospitalisation complete en externe", List.of("PV1-VENUE-03"), List.of(),
                        set("PV1", 2, "O"), set("PV1", 21, "03")),
                rule("hospitalisation complete", List.of(), List.of("PV1-VENUE-03"),
                        set("PV1", 2, "I"), set("PV1", 21, "03")),
                rule("retrocession hors mode 09", List.of("PV1-VENUE-RM"), List.of(),
                        set("PV1", 2, "O"), set("PV1", 4, "RM"), set("PV1", 21, "07")),
                rule("scanographie en hospitalisation de jour", List.of("PV1-DISCIPLINE-035"), List.of(),
                        set("PV1", 10, "035"), set("PV1", 21, "04")),
                rule("scanographie en hospitalisation complete", List.of(), List.of("PV1-DISCIPLINE-035"),
                        set("PV1", 10, "035"), set("PV1", 21, "03")),
                rule("mise a jour de mouvement sans evenement d'origine", List.of("ZBE-6(0)"), List.of(),
                        set("ZBE", 4, "UPDATE"), set("ZBE", 6, "")),
                rule("insertion de mouvement sans evenement d'origine", List.of(), List.of("ZBE-6(0)"),
                        set("ZBE", 4, "INSERT"), set("ZBE", 6, "")),
                rule("segment ZFP vide", List.of("Z-NON-VIDE-ZFP"), List.of(), set("ZFP", 1, ""), set("ZFP", 2, "")),
                rule("adresse e-mail absente", List.of("PID-13(0)-4"), List.of(), set("PID", 13, "^NET^Internet^")),
                rule("adresse e-mail", List.of(), List.of("PID-13(0)-4", "PID-13(0)-12"),
                        set("PID", 13, "^NET^Internet^info@ihe.net")),
                rule("declaration de conformite 2.11", List.of(), List.of("MSH-21-PAM-FR"),
                        set("MSH", 21, "2.11^IHE_FRANCE-2.11-PAM")),
                rule("declaration de conformite 2.10", List.of("MSH-21-PAM-FR"), List.of(),
                        set("MSH", 21, "2.10^IHE_FRANCE-2.10-PAM")),
                rule("rang de naissance d'une naissance simple", List.of("PID-25(0)"), List.of(),
                        set("PID", 24, "N"), set("PID", 25, "1")),
                rule("rang de naissance sans indicateur", List.of(), List.of("PID-25(0)"),
                        set("PID", 24, ""), set("PID", 25, "1")),
                rule("medecin traitant apres PID", List.of(), List.of("ROL-APRES-PID", "ROL-APRES-PV1"),
                        after("PID", rol("ODRP"))),
                rule("remplacant apres PID", List.of("ROL-APRES-PID"), List.of(), after("PID", rol("SUBS"))),
                rule("medecin adressant apres la venue", List.of(), List.of("ROL-APRES-PID", "ROL-APRES-PV1"),
                        after("ZFD", rol("RP"))),
                rule("medecin traitant apres la venue", List.of("ROL-APRES-PV1"), List.of(), after("ZFD", rol("ODRP"))),
                rule("couverture AMO complete", List.of(), List.of("IN1-AMO-REQUIS", "IN1-AMO-NATURE", "IN1-AMO-ACCIDENT"),
                        after("ZFD", in1("AMO", "01123456", "00", "10"))),
                rule("couverture AMO sans qualite du beneficiaire", List.of("IN1-AMO-REQUIS"), List.of(),
                        after("ZFD", in1("AMO", "01123456", "", ""))),
                rule("accident du travail sans numero", List.of("IN1-AMO-ACCIDENT"), List.of(),
                        after("ZFD", in1("AMO", "01123456", "00", "41"))),
                rule("nature d'assurance inconnue", List.of("IN1-AMO-NATURE"), List.of(),
                        after("ZFD", in1("AMO", "01123456", "00", "99"))),
                rule("couverture AMC sans numero d'organisme", List.of("IN1-AMC-REQUIS"), List.of(),
                        after("ZFD", in1("AMC", "", "00", ""))),
                rule("patient debiteur sans GT1", List.of("IN1-AUTRE-DEBITEUR-GT1"), List.of(),
                        after("ZFD", in1("PAT", "", "", ""))),
                rule("patient debiteur avec GT1", List.of(), List.of("IN1-AUTRE-DEBITEUR-GT1"),
                        after("ZFD", in1("PAT", "", "", "")), after("ZFD", GT1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void eachRuleFailsWhereItIsBrokenAndOnlyThere(String name, List<UnaryOperator<List<String>>> changes,
                                                  List<String> present, List<String> absent) {
        List<String> msg = base;
        for (UnaryOperator<List<String>> change : changes) {
            msg = change.apply(msg);
        }
        List<Report.Issue> issues = validator.validate(String.join("\r", msg) + "\r", products, PROFILE, "NIST").report().issues();
        // Une regle se lit dans le texte (identifiant d'assertion) ou dans l'emplacement (predicat).
        List<String> seen = issues.stream().map(i -> i.location() + " " + i.message()).toList();
        String report = seen.stream().collect(Collectors.joining("\n"));

        for (String rule : present) {
            assertTrue(seen.stream().anyMatch(s -> s.contains(rule)), name + " : " + rule + " attendu\n" + report);
        }
        for (String rule : absent) {
            assertTrue(seen.stream().noneMatch(s -> s.contains(rule)), name + " : " + rule + " inattendu\n" + report);
        }
    }
}

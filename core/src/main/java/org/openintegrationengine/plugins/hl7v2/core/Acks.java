package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.ACK;
import ca.uhn.hl7v2.util.Terser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Construction des messages ACK renvoyes par le service.
 *
 * Trois codes d'acquittement, selon la convention HL7 :
 * <ul>
 *   <li>AA — Application Accept : le profil n'a rien releve ;</li>
 *   <li>AE — Application Error : le message est lu mais non conforme, les erreurs sont dans
 *       les segments ERR ;</li>
 *   <li>AR — Application Reject : la validation n'a pas pu avoir lieu, message illisible ou
 *       profil inconnu.</li>
 * </ul>
 *
 * Le ACK est une enveloppe, pas le compte rendu de reference : il porte un ERR par erreur avec
 * son emplacement et son libelle, mais le detail structure est dans le JSON et le texte, que
 * rend {@link Report}. Les champs sont donc poses au mieux, et un champ absent de la
 * version du message est passe sans faire echouer l'appel.
 */
public final class Acks {

    private static final Logger log = LoggerFactory.getLogger(Acks.class);

    // Table HL7 0357, code generique : 101 ("required field missing") pour toutes les erreurs de
    // conformite, faute d'une correspondance fiable entre les libelles du DefaultValidator de
    // HAPI et cette table.
    private static final String HL7_ERROR_CODE = "101";

    // Les erreurs ont change de forme en 2.5 : avant, un champ ERR-1 (ELD) repetable dans un
    // segment ERR unique ; depuis, un segment ERR repetable, avec ERR-3 (code) et ERR-4
    // (severite). Un ACK 2.3 et un ACK 2.5 ne se renseignent donc pas au meme endroit.
    private static final Set<String> PRE_25_VERSIONS = Set.of("2.0", "2.1", "2.2", "2.3", "2.3.1", "2.4");

    private Acks() {
    }

    /**
     * ACK d'acquittement d'un message lu : AA s'il est conforme, AE sinon.
     *
     * Le ACK est genere depuis le message recu, il est donc de sa version, et HAPI y recopie son
     * MSH-10 en MSA-2.
     *
     * Le message est parse et acquitte dans le meme fil ; aucun parseur partage.
     */
    public static String acknowledge(Message request, Report report) {
        try {
            String acknowledgmentCode = report.acknowledgmentCode();
            String text = report.valid() ? "Message valide" : "Message invalide : voir segments ERR";
            List<Report.Issue> issues = report.issues();
            Message ack = request.generateACK();
            Terser terser = new Terser(ack);
            terser.set("/MSA-1", acknowledgmentCode);
            // MSA-3 (Text Message) est retire de la norme a partir de 2.7 : setIfPresent le
            // passe en silence sur un ACK de cette version plutot que de faire echouer l'appel.
            setIfPresent(terser, "/MSA-3", text);

            boolean pre25 = PRE_25_VERSIONS.contains(ack.getVersion());
            for (int i = 0; i < issues.size(); i++) {
                Report.Issue issue = issues.get(i);
                String detail = issue.location().isEmpty()
                        ? issue.message()
                        : issue.location() + " - " + issue.message();
                if (pre25) {
                    setIfPresent(terser, "/ERR-1(" + i + ")-4-1", HL7_ERROR_CODE);
                    setIfPresent(terser, "/ERR-1(" + i + ")-4-2", detail);
                } else {
                    setIfPresent(terser, "/ERR(" + i + ")-3-1", HL7_ERROR_CODE);
                    setIfPresent(terser, "/ERR(" + i + ")-3-2", detail);
                    setIfPresent(terser, "/ERR(" + i + ")-4", issue.hasError() ? "E" : "W");
                }
            }
            return ack.encode();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Construction du ACK impossible : " + Refusal.messageOf(e), e);
        }
    }

    /**
     * ACK de rejet (AR), construit sans message d'origine.
     *
     * Appele quand la validation n'a pas pu avoir lieu : le message n'a pas ete parse, ou son
     * profil est introuvable. Le ACK est alors en 2.5, la version des profils livres, et MSA-2
     * vaut N/A faute de MSH-10 a recopier.
     *
     * Le contexte HAPI est propre au fil appelant.
     */
    public static String reject(String text) {
        try {
            // newMessage rattache le message au contexte du fil, dont le generateur ne touche pas au disque.
            ACK ack = HapiContexts.current().newMessage(ACK.class);
            ack.initQuickstart("ACK", "", "P");
            ack.getMSA().getAcknowledgmentCode().setValue("AR");
            ack.getMSA().getMessageControlID().setValue("N/A");
            ack.getMSA().getTextMessage().setValue(text);
            ack.insertERR(0).getHL7ErrorCode().getIdentifier().setValue(HL7_ERROR_CODE);
            ack.getERR(0).getHL7ErrorCode().getText().setValue(text);
            ack.getERR(0).getSeverity().setValue("E");
            return ack.encode();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Construction du ACK de rejet impossible : " + Refusal.messageOf(e), e);
        }
    }

    /**
     * Pose une valeur si le champ existe dans cette version du message.
     *
     * Les positions utilisees ici sont stables de 2.3 a 2.8.1, a l'exception de MSA-3 ; le
     * try/catch couvre ce cas et les versions futures, sans masquer d'erreur utile : l'echec
     * est trace et le ACK reste exploitable, son MSA-1 portant deja le verdict.
     */
    private static void setIfPresent(Terser terser, String path, String value) {
        try {
            terser.set(path, value);
        } catch (Exception e) {
            log.debug("Champ {} absent de ce ACK, valeur ignoree", path);
        }
    }
}

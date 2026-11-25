package org.openintegrationengine.plugins.hl7v2.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Le profil d'un message, dans un jeu donne, d'apres son MSH-9 (doc/RESOLUTION.md).
 *
 * <ol>
 *   <li>un profil du jeu declare exactement ce {@code MsgType^EventType^MsgStructID} ;</li>
 *   <li>a defaut, le type et l'evenement, quelle que soit la structure annoncee. Un profil qui
 *       declare {@code EventType="ALL"} repond a tout evenement de son type : la convention des
 *       profils de ACK ;</li>
 *   <li>a defaut, la structure seule, pour un evenement sans profil propre ({@code ADT^A08^ADT_A01}).
 *       Si plusieurs profils la declarent, celui qui en est <b>proprietaire</b> l'emporte — celui qui
 *       la nomme d'apres son propre evenement, {@code ADT_A01} pour {@code A01}.</li>
 * </ol>
 *
 * Plusieurs candidats a l'arrivee : le {@code resolution.yaml} du jeu tranche, cherche sous le MSH-9
 * du message puis sous celui que les candidats declarent (les deux different des qu'un profil
 * joue le joker {@code ALL}). Sans arbitrage, c'est un refus 409 : le service ne choisit pas au
 * hasard. La recherche ne sort jamais du jeu.
 */
public final class Resolver {

    /** Evenement joker d'un profil : repond a tout evenement de son type. */
    public static final String ANY_EVENT = "ALL";

    private Resolver() {
    }

    public static String key(String msgType, String eventType, String structure) {
        return msgType + "^" + eventType + "^" + structure;
    }

    /** Le profil retenu, ou un {@link Refusal} 404 (aucun) ou 409 (plusieurs, non arbitres). */
    public static ProductSet.Profile resolve(ProductSet set, String msgType, String eventType, String structure) {
        String key = key(msgType, eventType, structure);
        List<ProductSet.Profile> exact = matching(set, p -> p.key().equals(key));
        if (!exact.isEmpty()) {
            return choose(set, key, exact);
        }
        List<ProductSet.Profile> byEvent = matching(set, p -> p.msgType().equals(msgType)
                && (p.eventType().equals(eventType) || p.eventType().equals(ANY_EVENT)));
        if (!byEvent.isEmpty()) {
            return owner(set, key, byEvent);
        }
        List<ProductSet.Profile> byStructure = matching(set, p -> !structure.isEmpty() && p.structure().equals(structure));
        if (!byStructure.isEmpty()) {
            return owner(set, key, byStructure);
        }
        throw new Refusal(ErrorCode.NO_PROFILE_FOR_MESSAGE, "Aucun profil du jeu " + set.name()
                + " ne declare le message " + label(msgType, eventType, structure)
                + ". Profils du jeu : GET /sets/" + set.name() + "/profiles.");
    }

    /** {@code ADT^A01^ADT_A01}, sans les parties vides : la forme lisible d'un MSH-9. */
    public static String label(String msgType, String eventType, String structure) {
        StringBuilder sb = new StringBuilder(msgType);
        if (!eventType.isEmpty()) {
            sb.append('^').append(eventType);
        }
        if (!structure.isEmpty()) {
            sb.append('^').append(structure);
        }
        return sb.toString();
    }

    /** Les MSH-9 declares par plus d'un profil du jeu et non arbitres, avec leurs candidats. */
    public static Map<String, List<String>> ambiguous(ProductSet set) {
        Map<String, List<String>> byKey = new TreeMap<>();
        for (ProductSet.Profile p : set.profiles().values()) {
            if (p.msgType().isEmpty() && p.eventType().isEmpty() && p.structure().isEmpty()) {
                continue;
            }
            byKey.computeIfAbsent(p.key(), k -> new ArrayList<>()).add(p.id());
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        byKey.forEach((k, ids) -> {
            if (ids.size() > 1 && !set.arbitration().containsKey(k)) {
                out.put(k, ids);
            }
        });
        return out;
    }

    private static List<ProductSet.Profile> matching(ProductSet set, Predicate<ProductSet.Profile> test) {
        return set.profiles().values().stream()
                // Un fichier parasite, sans en-tete : il ne repond a aucun message.
                .filter(p -> !(p.msgType().isEmpty() && p.eventType().isEmpty() && p.structure().isEmpty()))
                .filter(test)
                .sorted(Comparator.comparing(ProductSet.Profile::id)).toList();
    }

    /** Parmi plusieurs, le profil proprietaire de la structure (nommee d'apres son evenement) l'emporte. */
    private static ProductSet.Profile owner(ProductSet set, String key, List<ProductSet.Profile> found) {
        if (found.size() > 1) {
            List<ProductSet.Profile> owners = found.stream()
                    .filter(p -> p.structure().equals(p.msgType() + "_" + p.eventType())).toList();
            if (owners.size() == 1) {
                return owners.get(0);
            }
        }
        return choose(set, key, found);
    }

    private static ProductSet.Profile choose(ProductSet set, String key, List<ProductSet.Profile> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        List<String> keys = new ArrayList<>();
        keys.add(key);
        for (ProductSet.Profile p : candidates) {
            if (!keys.contains(p.key())) {
                keys.add(p.key());
            }
        }
        List<String> ids = candidates.stream().map(ProductSet.Profile::id).toList();
        for (String k : keys) {
            String chosen = set.arbitration().get(k);
            if (chosen != null && ids.contains(chosen)) {
                return set.profiles().get(chosen);
            }
        }
        throw new Refusal(ErrorCode.AMBIGUOUS_PROFILE, "Dans le jeu " + set.name()
                + ", plusieurs profils repondent au message " + key + " : " + ids
                + ". Preciser le parametre profile, ou trancher dans le " + ProductSet.RESOLUTION
                + " du jeu (cle ambiguous." + keys.get(keys.size() - 1) + ").", ids, null);
    }
}

package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.conf.store.CodeStore;
import ca.uhn.hl7v2.conf.store.ProfileCodeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/** Tables par version, completees des codes locaux ; une table vide ne refuse aucun code. */
public final class Tables {
    private static final Logger log = LoggerFactory.getLogger(Tables.class);

    private Tables() {
    }

    public static Optional<CodeStore> forSet(ProductSet set, String version) {
        return set.cache("hapi/tables/" + version, () -> {
            CodeStore generic = load(set, "HL7Table-" + version + ".xml");
            CodeStore local = load(set, ProductSet.LOCAL_TABLES);
            if (generic == null) {
                return Optional.ofNullable(local);
            }
            return Optional.of(local == null ? generic : new WithLocalCodes(generic, local));
        });
    }

    private static CodeStore load(ProductSet set, String file) {
        Optional<byte[]> bytes = set.bytes(ProductSet.HAPI_TABLES + "/" + file);
        if (bytes.isEmpty() && set.isLegacyLayout()) {
            bytes = set.bytes("tables/" + file);
        }
        if (bytes.isEmpty()) {
            return null;
        }
        Path temp = null;
        try {
            temp = Files.createTempFile("hl7-table-", ".xml");
            Files.write(temp, bytes.get());
            // ProfileCodeStore charge tout dans son constructeur : le fichier peut etre supprime aussitot.
            return new SkipEmptyTables(new ProfileCodeStore(temp.toUri().toURL()));
        } catch (Exception e) {
            // Comportement historique : une table illisible n'empeche pas le controle de structure.
            log.warn("Table {} du jeu {} illisible, codes non controles", file, set.name());
            return null;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (java.io.IOException e) {
                    temp.toFile().deleteOnExit();
                }
            }
        }
    }

    private static final class WithLocalCodes implements CodeStore {

        private final CodeStore generic;
        private final CodeStore local;

        WithLocalCodes(CodeStore generic, CodeStore local) {
            this.generic = generic;
            this.local = local;
        }

        @Override
        public boolean knowsCodes(String codeSystem) {
            return generic.knowsCodes(codeSystem) || local.knowsCodes(codeSystem);
        }

        @Override
        public String[] getValidCodes(String codeSystem) throws ca.uhn.hl7v2.conf.ProfileException {
            LinkedHashSet<String> codes = new LinkedHashSet<>();
            if (generic.knowsCodes(codeSystem)) {
                codes.addAll(List.of(generic.getValidCodes(codeSystem)));
            }
            if (local.knowsCodes(codeSystem)) {
                codes.addAll(List.of(local.getValidCodes(codeSystem)));
            }
            return codes.toArray(new String[0]);
        }

        @Override
        public boolean isValidCode(String codeSystem, String code) {
            boolean inGeneric = generic.knowsCodes(codeSystem);
            boolean inLocal = local.knowsCodes(codeSystem);
            if (!inGeneric && !inLocal) {
                return true;
            }
            return (inGeneric && generic.isValidCode(codeSystem, code))
                    || (inLocal && local.isValidCode(codeSystem, code));
        }
    }

    /**
     * Masque les tables declarees sans aucun code.
     *
     * Les tables HL7 dites <i>user-defined</i> — 0300 Namespace ID, 0361 Application,
     * 0362 Facility, 0363 Assigning authority — sont declarees dans les fichiers Gazelle mais
     * <b>vides</b> : la norme laisse chaque site y mettre ses propres valeurs. HAPI, lui,
     * repond {@code knowsCodes == true} puis rend un tableau vide, et <b>toute</b> valeur
     * devient alors « Code 'X' not found in table HL70300 ». Mesure sur un ADT PAM FR : sans ce
     * filtre, 11 fausses erreurs sur ces quatre tables.
     *
     * Une table sans code n'enumere rien : la verifier n'a pas de sens. Pour en verifier une, on
     * declare ses codes dans les codes locaux du jeu.
     */
    private static final class SkipEmptyTables implements CodeStore {

        private final CodeStore delegate;

        SkipEmptyTables(CodeStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean knowsCodes(String codeSystem) {
            if (!delegate.knowsCodes(codeSystem)) {
                return false;
            }
            try {
                String[] codes = delegate.getValidCodes(codeSystem);
                return codes != null && codes.length > 0;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public String[] getValidCodes(String codeSystem) throws ca.uhn.hl7v2.conf.ProfileException {
            return delegate.getValidCodes(codeSystem);
        }

        @Override
        public boolean isValidCode(String codeSystem, String code) {
            return !knowsCodes(codeSystem) || delegate.isValidCode(codeSystem, code);
        }
    }
}

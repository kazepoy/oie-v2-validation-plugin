package org.openintegrationengine.plugins.hl7v2.core;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Un jeu de profils tel qu'un validateur le lit : ses <b>produits</b> — {@code hapi/messages/},
 * {@code hapi/tables/}, {@code nist/}, {@code resolution.yaml} — et rien d'autre. Un repertoire ou
 * une archive {@code .zip} du meme contenu (doc/JEU-DE-PROFILS.md du service, specification D12 du
 * studio) ; jamais le {@code model/} du studio.
 *
 * <pre>
 * &lt;jeu&gt;/
 *   set.yaml                                        facultatif : version et empreinte, ecrits par le studio
 *   hapi/messages/&lt;acteur&gt;/&lt;transaction&gt;/&lt;profil&gt;.xml  les profils 2B, lus par HAPI
 *   hapi/tables/HL7Table-&lt;version&gt;.xml, HL7Table-local.xml   les tables de codes, pour HAPI seul
 *   nist/Profile.xml, Constraints.xml, ValueSets.xml      le moteur NIST, facultatif
 *   resolution.yaml                                 arbitrage des MSH-9 ambigus, facultatif
 * </pre>
 *
 * L'identifiant d'un profil est son chemin sous {@code hapi/messages/}, sans extension :
 * {@code PES/ITI-31/ADT_A01}. Un message du {@code nist/Profile.xml} est rattache au profil 2B de
 * meme chemin (identifiant {@code hapi/messages/<id>}, {@code <id>} ou {@code IHE_FR/<id>} selon le
 * generateur), a defaut au profil qui declare le meme triplet MSH-9 ; un message sans profil 2B
 * est un profil <b>NIST seul</b>, validable par ce moteur seulement.
 *
 * Un jeu sans sous-arbre {@code hapi/messages/} est lu a l'ancienne — tout le jeu sauf les tables
 * et {@code nist/} — pour qu'un jeu plus ancien reste lisible.
 *
 * Les moteurs rangent leur etat propre a ce jeu (profils parses, magasins de codes, contexte
 * NIST) dans {@link #cache} : relire le jeu, c'est en construire un nouveau, et tout repart.
 * Sans Spring, sans studio : ce que charge aussi le testeur du studio et le plugin OIE.
 */
public final class ProductSet implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ProductSet.class);

    public static final String HAPI_DIR = "hapi";
    public static final String HAPI_MESSAGES = "hapi/messages";
    public static final String HAPI_TABLES = "hapi/tables";
    public static final String LOCAL_TABLES = "HL7Table-local.xml";
    public static final String NIST_DIR = "nist";
    public static final String NIST_PROFILE = "nist/Profile.xml";
    public static final String RESOLUTION = "resolution.yaml";
    public static final String MANIFEST = "set.yaml";
    public static final String README = "README.md";
    public static final String SUMMARY = "summary.md";
    /** Les fichiers du dossier nist/, dans l'ordre ou on les montre. */
    public static final List<String> NIST_FILES = List.of("README.md", "Profile.xml", "Constraints.xml",
            "CoConstraints.xml", "Slicings.xml", "ValueSets.xml", "ValueSetBindings.xml");

    /** Nom d'un jeu : celui du repertoire ou de l'archive, sans chemin. */
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    /**
     * Un profil du jeu.
     *
     * @param id            identifiant relatif au jeu, {@code PES/ITI-31/ADT_A01}
     * @param msgType       MsgType declare, {@code ADT}
     * @param eventType     EventType declare, {@code A01} — {@code ALL} pour un ACK
     * @param structure     MsgStructID declare, {@code ADT_A01}
     * @param eventDesc     libelle du profil 2B, vide pour un profil NIST seul
     * @param hl7Version    HL7Version du profil 2B
     * @param hapiFile      le profil 2B, null pour un profil NIST seul
     * @param nistMessageId identifiant du message dans nist/Profile.xml, null si le NIST ne le couvre pas
     */
    public record Profile(String id, String msgType, String eventType, String structure, String eventDesc,
                          String hl7Version, Path hapiFile, String nistMessageId) {

        /** {@code ADT^A01^ADT_A01} : la cle de resolution, celle que porte aussi le MSH-9 d'un message. */
        public String key() {
            return msgType + "^" + eventType + "^" + structure;
        }

        public String name() {
            int slash = id.lastIndexOf('/');
            return slash < 0 ? id : id.substring(slash + 1);
        }

        public boolean hasHapi() {
            return hapiFile != null;
        }

        public boolean hasNist() {
            return nistMessageId != null;
        }

        Profile withNist(String messageId) {
            return new Profile(id, msgType, eventType, structure, eventDesc, hl7Version, hapiFile, messageId);
        }
    }

    private final String name;
    private final Path location;
    private final Path root;
    private final FileSystem zip;
    private final boolean legacyLayout;
    private final Map<String, Profile> profiles = new TreeMap<>();
    private final Map<String, String> arbitration = new TreeMap<>();
    private final String version;
    private final String hash;
    private final ConcurrentHashMap<String, Object> caches = new ConcurrentHashMap<>();

    private ProductSet(String name, Path location, Path root, FileSystem zip) {
        this.name = name;
        this.location = location;
        this.root = root;
        this.zip = zip;
        this.legacyLayout = !Files.isDirectory(root.resolve(HAPI_MESSAGES));
        index();
        readResolution();
        Map<?, ?> manifest = yaml(MANIFEST);
        this.version = java.util.Objects.toString(manifest.get("version"), "");
        Object products = manifest.get("products");
        String modelHash = products instanceof Map<?, ?> map ? java.util.Objects.toString(map.get("modelHash"), "") : "";
        this.hash = zip != null ? "sha256:" + sha256(location) : modelHash;
    }

    /** Ouvre un jeu depuis son repertoire ou son archive {@code .zip} ; le nom est celui du fichier. */
    public static ProductSet open(Path location) {
        String file = location.getFileName().toString();
        if (Files.isRegularFile(location) && file.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            String name = file.substring(0, file.length() - 4);
            FileSystem fs;
            try {
                fs = FileSystems.newFileSystem(location, Map.of());
            } catch (IOException | RuntimeException e) {
                throw new IllegalArgumentException("archive illisible : " + location + " (" + e.getMessage() + ")", e);
            }
            try {
                return new ProductSet(name, location, fs.getPath("/"), fs);
            } catch (RuntimeException e) {
                try {
                    fs.close();
                } catch (IOException ignored) {
                    // l'erreur d'origine est la plus utile
                }
                throw e;
            }
        }
        if (!Files.isDirectory(location)) {
            throw new IllegalArgumentException("ni un repertoire ni une archive .zip : " + location);
        }
        return new ProductSet(file, location, location, null);
    }

    /** Vrai si le repertoire ou l'archive a la forme d'un jeu : des profils 2B ou un Profile.xml NIST. */
    public static boolean looksLikeSet(Path location) {
        if (Files.isDirectory(location)) {
            return Files.isDirectory(location.resolve(HAPI_MESSAGES)) || Files.isRegularFile(location.resolve(NIST_PROFILE))
                    || hasXmlFiles(location);
        }
        return Files.isRegularFile(location) && location.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static boolean hasXmlFiles(Path dir) {
        try (Stream<Path> s = Files.walk(dir, 3)) {
            return s.anyMatch(p -> Files.isRegularFile(p) && isXml(p));
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ what the set is

    public String name() {
        return name;
    }

    /** {@code directory} ou {@code archive}. */
    public String form() {
        return zip != null ? "archive" : "directory";
    }

    /** Le repertoire ou l'archive sur le disque. */
    public Path location() {
        return location;
    }

    /** La racine des fichiers du jeu : le repertoire, ou l'interieur de l'archive. */
    public Path root() {
        return root;
    }

    /** {@code version} du set.yaml, vide sans manifeste. */
    public String version() {
        return version;
    }

    /** SHA-256 du ZIP applique ; en repertoire, products.modelHash, sinon vide. */
    public String hash() {
        return hash;
    }

    /** Vrai pour un jeu sans sous-arbre hapi/messages/, lu a l'ancienne. */
    public boolean isLegacyLayout() {
        return legacyLayout;
    }

    /** Les profils, par identifiant, tries. */
    public Map<String, Profile> profiles() {
        return java.util.Collections.unmodifiableMap(profiles);
    }

    public Optional<Profile> profile(String id) {
        return Optional.ofNullable(profiles.get(id));
    }

    /** Arbitrages du resolution.yaml : {@code MSH-9 declare -> identifiant de profil}. */
    public Map<String, String> arbitration() {
        return java.util.Collections.unmodifiableMap(arbitration);
    }

    public boolean hasNist() {
        return exists(NIST_PROFILE);
    }

    /** Les fichiers du dossier nist/ presents, dans l'ordre de {@link #NIST_FILES}. */
    public List<String> nistFiles() {
        List<String> out = new ArrayList<>();
        for (String f : NIST_FILES) {
            if (exists(NIST_DIR + "/" + f)) {
                out.add(f);
            }
        }
        return out;
    }

    /** Versions HL7 pour lesquelles le jeu porte une table de codes ({@code HL7Table-<version>.xml}). */
    public List<String> tableVersions() {
        Path tables = root.resolve(HAPI_TABLES);
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(tables)) {
            return out;
        }
        Pattern p = Pattern.compile("^HL7Table-(\\d+\\.\\d+(?:\\.\\d+)?)\\.xml$");
        try (Stream<Path> s = Files.list(tables)) {
            for (Path f : s.sorted().toList()) {
                Matcher m = p.matcher(f.getFileName().toString());
                if (m.matches()) {
                    out.add(m.group(1));
                }
            }
        } catch (IOException e) {
            log.warn("jeu {} : tables illisibles : {}", name, e.toString());
        }
        return out;
    }

    public boolean hasLocalTables() {
        return exists(HAPI_TABLES + "/" + LOCAL_TABLES);
    }

    // ------------------------------------------------------------------ files

    /** Un fichier du jeu, par chemin relatif ({@code hapi/tables/HL7Table-2.5.xml}), s'il existe. */
    public Optional<Path> file(String relative) {
        Path p = root.resolve(relative);
        return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
    }

    public boolean exists(String relative) {
        return Files.isRegularFile(root.resolve(relative));
    }

    public Optional<byte[]> bytes(String relative) {
        return file(relative).map(p -> {
            try {
                return Files.readAllBytes(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Le texte d'un fichier du jeu, en UTF-8. */
    public Optional<String> text(String relative) {
        return bytes(relative).map(b -> new String(b, StandardCharsets.UTF_8));
    }

    /** La source 2B d'un profil, telle qu'elle est livree, sans reecriture. */
    public Optional<String> source(String profileId) {
        Profile p = profiles.get(profileId);
        if (p == null || p.hapiFile() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(p.hapiFile(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Etat propre a ce jeu, garde par un moteur ; recalcule quand le jeu est relu. */
    @SuppressWarnings("unchecked")
    public <T> T cache(String key, Supplier<T> supplier) {
        return (T) caches.computeIfAbsent(key, k -> supplier.get());
    }

    @Override
    public void close() {
        caches.clear();
        if (zip != null) {
            try {
                zip.close();
            } catch (IOException e) {
                log.debug("archive {} : fermeture en echec : {}", location, e.toString());
            }
        }
    }

    @Override
    public String toString() {
        return name + " (" + form() + ", " + profiles.size() + " profil(s))";
    }

    // ------------------------------------------------------------------ index

    private void index() {
        Path messages = root.resolve(HAPI_MESSAGES);
        if (Files.isDirectory(messages)) {
            indexProfiles(messages, messages);
        } else {
            // Ancien rangement : tout le jeu sauf les tables et nist/.
            log.warn("jeu {} : pas de sous-arbre {}/, lecture a l'ancienne", name, HAPI_MESSAGES);
            indexProfiles(root, root);
        }
        Path nist = root.resolve(NIST_PROFILE);
        if (Files.isRegularFile(nist)) {
            attachNistMessages(nist);
        }
    }

    private void indexProfiles(Path base, Path messages) {
        Path tables = root.resolve(HAPI_TABLES);
        Path legacyTables = root.resolve("tables");
        Path nist = root.resolve(NIST_DIR);
        try (Stream<Path> s = Files.walk(base)) {
            for (Path f : s.filter(p -> Files.isRegularFile(p) && isXml(p)).sorted().toList()) {
                if (f.startsWith(tables) || f.startsWith(legacyTables) || f.startsWith(nist)) {
                    continue;
                }
                String rel = messages.relativize(f).toString().replace('\\', '/');
                String id = rel.substring(0, rel.length() - 4);
                Map<String, String> header = header(f);
                profiles.put(id, new Profile(id, nz(header.get("MsgType")), nz(header.get("EventType")),
                        nz(header.get("MsgStructID")), nz(header.get("EventDesc")), nz(header.get("HL7Version")), f, null));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Rattache chaque message de nist/Profile.xml a un profil 2B : par son identifiant d'abord
     * (le chemin du profil, avec ou sans prefixe), par son triplet MSH-9 ensuite quand il est
     * unique. Un message sans profil 2B devient un profil NIST seul.
     */
    private void attachNistMessages(Path nist) {
        List<Map<String, String>> pending = new ArrayList<>();
        for (Map<String, String> m : elements(nist, "Message", false)) {
            String mid = nz(m.get("ID"));
            String id = stripPrefix(mid);
            Profile p = profiles.get(id);
            if (p != null && p.nistMessageId() == null) {
                profiles.put(id, p.withNist(mid));
            } else {
                pending.add(m);
            }
        }
        for (Map<String, String> m : pending) {
            String mid = nz(m.get("ID"));
            String key = nz(m.get("Type")) + "^" + nz(m.get("Event")) + "^" + nz(m.get("StructID"));
            List<Profile> same = profiles.values().stream()
                    .filter(p -> p.nistMessageId() == null && p.key().equals(key)).toList();
            if (same.size() == 1) {
                profiles.put(same.get(0).id(), same.get(0).withNist(mid));
            } else {
                String id = stripPrefix(mid);
                if (!profiles.containsKey(id)) {
                    profiles.put(id, new Profile(id, nz(m.get("Type")), nz(m.get("Event")), nz(m.get("StructID")),
                            nz(m.get("Description")), "", null, mid));
                }
            }
        }
    }

    private static String stripPrefix(String nistId) {
        for (String prefix : List.of(HAPI_MESSAGES + "/", "IHE_FR/")) {
            if (nistId.startsWith(prefix)) {
                return nistId.substring(prefix.length());
            }
        }
        return nistId;
    }

    /** Lecture des petits documents YAML du jeu, sans construction de types Java arbitraires. */
    public Map<?, ?> yaml(String relative) {
        var file = file(relative);
        if (file.isEmpty()) return Map.of();
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream in = Files.newInputStream(file.get())) {
            Object value = new Yaml(new SafeConstructor(options)).load(in);
            if (value == null) return Map.of();
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException(relative + " doit etre un objet YAML");
            }
            return map;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void readResolution() {
        Object values = yaml(RESOLUTION).get("ambiguous");
        if (values instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                String id = java.util.Objects.toString(value, "").replace('\\', '/');
                if (profiles.containsKey(id)) arbitration.put(key.toString(), id);
            });
        }
    }

    private static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (Exception e) {
            return "";
        }
    }

    /** Attributs du HL7v2xConformanceProfile et du premier HL7v2xStaticDef, sans parser le profil entier. */
    private static Map<String, String> header(Path f) {
        Map<String, String> out = new LinkedHashMap<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        try (InputStream in = Files.newInputStream(f)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            while (r.hasNext()) {
                if (r.next() != XMLStreamConstants.START_ELEMENT) {
                    continue;
                }
                if (r.getLocalName().equals("HL7v2xConformanceProfile")) {
                    out.put("HL7Version", nz(r.getAttributeValue(null, "HL7Version")));
                } else if (r.getLocalName().equals("HL7v2xStaticDef")) {
                    for (int i = 0; i < r.getAttributeCount(); i++) {
                        out.put(r.getAttributeLocalName(i), r.getAttributeValue(i));
                    }
                    break;
                }
            }
            r.close();
        } catch (Exception e) {
            // Un fichier qui n'est pas un profil reste indexe, sans metadonnees : c'est son parse
            // a la premiere validation qui echoue, pas le chargement du jeu.
            log.warn("profil {} : en-tete illisible : {}", f, e.toString());
        }
        return out;
    }

    private static List<Map<String, String>> elements(Path f, String tag, boolean first) {
        List<Map<String, String>> out = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        try (InputStream in = Files.newInputStream(f)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && r.getLocalName().equals(tag)) {
                    Map<String, String> a = new LinkedHashMap<>();
                    for (int i = 0; i < r.getAttributeCount(); i++) {
                        a.put(r.getAttributeLocalName(i), r.getAttributeValue(i));
                    }
                    out.add(a);
                    if (first) {
                        break;
                    }
                }
            }
            r.close();
        } catch (Exception e) {
            throw new IllegalArgumentException("illisible : " + f + " (" + e.getMessage() + ")", e);
        }
        return out;
    }

    private static boolean isXml(Path p) {
        return p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

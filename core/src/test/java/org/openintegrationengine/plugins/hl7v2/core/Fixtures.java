package org.openintegrationengine.plugins.hl7v2.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class Fixtures {
    static final String PAM = "ihe-pam-fr-2.11.2";
    static final Path PAM_ZIP = Path.of("core/src/test/resources/profiles", PAM + ".zip");
    static final String A01 = "PES/ITI-31/ADT_A01";

    static String example(String file) throws IOException {
        return Files.readString(Path.of("examples/ihe-pam-fr-2.11.2", file));
    }

    static Path profile(Path set, String id, String type, String event, String structure) throws IOException {
        Path file = set.resolve("hapi/messages/" + id + ".xml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                <HL7v2xConformanceProfile HL7Version="2.5">
                  <HL7v2xStaticDef MsgType="%s" EventType="%s" MsgStructID="%s"/>
                </HL7v2xConformanceProfile>
                """.formatted(type, event, structure));
        return file;
    }

    static void copyProduct(ProductSet source, Path target, String relative) throws IOException {
        Path file = target.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, source.bytes(relative).orElseThrow());
    }
}

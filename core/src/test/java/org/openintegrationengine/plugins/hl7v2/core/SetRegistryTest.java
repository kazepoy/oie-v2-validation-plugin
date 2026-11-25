package org.openintegrationengine.plugins.hl7v2.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class SetRegistryTest {
    @TempDir Path root;

    @Test
    void reloadOfAnOpenArchiveIsAtomicAndFailureKeepsTheOldSet() throws Exception {
        Files.copy(Fixtures.PAM_ZIP, root.resolve(Fixtures.PAM + ".zip"));
        try (SetRegistry registry = new SetRegistry(root)) {
            assertEquals(List.of(Fixtures.PAM), registry.names());
            registry.reload();
            assertEquals(Boolean.TRUE, registry.withSet(Fixtures.PAM, set -> set.source(Fixtures.A01).isPresent()));
            Files.writeString(root.resolve("broken.zip"), "not a zip");
            assertEquals(ErrorCode.INVALID_SET, assertThrows(Refusal.class, registry::reload).code());
            assertEquals(Boolean.TRUE, registry.withSet(Fixtures.PAM, set -> set.source(Fixtures.A01).isPresent()));
        }
    }

    @Test
    @Timeout(15)
    void installWaitsForReadersAndInvalidArchiveDoesNotReplaceTheCurrentFile() throws Exception {
        Path target = root.resolve("published.zip");
        Files.copy(Fixtures.PAM_ZIP, target);
        Path upload = Files.createTempFile(root, ".upload-", ".zip");
        Files.copy(Fixtures.PAM_ZIP, upload, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        var threads = Executors.newFixedThreadPool(2);
        try (SetRegistry registry = new SetRegistry(root)) {
            CountDownLatch reading = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var reader = threads.submit(() -> registry.withSet("published", set -> {
                reading.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return set.source(Fixtures.A01).isPresent();
            }));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            var writer = threads.submit(() -> registry.install("published", upload));
            try { assertThrows(TimeoutException.class, () -> writer.get(100, TimeUnit.MILLISECONDS)); }
            finally { release.countDown(); }
            assertTrue(reader.get()); writer.get();
            assertEquals(Boolean.TRUE, registry.withSet("published", set -> set.source(Fixtures.A01).isPresent()));
            String hash = registry.withSet("published", ProductSet::hash);
            Path invalid = Files.createTempFile(root, ".invalid-", ".zip");
            Files.writeString(invalid, "not a zip");
            assertThrows(IllegalArgumentException.class, () -> registry.install("published", invalid));
            assertEquals(hash, registry.withSet("published", ProductSet::hash));
            try (ProductSet reopened = ProductSet.open(target)) { assertTrue(reopened.source(Fixtures.A01).isPresent()); }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void refusesMissingInvalidUnknownAndDuplicateNames() throws Exception {
        Fixtures.profile(root.resolve("pam"), "A01", "ADT", "A01", "ADT_A01");
        try (SetRegistry registry = new SetRegistry(root)) {
            assertEquals(ErrorCode.SET_REQUIRED, assertThrows(Refusal.class, () -> registry.withSet(null, s -> s.name())).code());
            assertEquals(ErrorCode.UNKNOWN_SET, assertThrows(Refusal.class, () -> registry.withSet("missing", s -> s.name())).code());
            assertEquals(ErrorCode.INVALID_PARAMETER, assertThrows(Refusal.class, () -> registry.withSet("../pam", s -> s.name())).code());
            Files.copy(Fixtures.PAM_ZIP, root.resolve("pam.zip"));
            assertEquals(ErrorCode.INVALID_SET, assertThrows(Refusal.class, registry::reload).code());
            assertEquals(List.of("pam"), registry.names());
            Files.delete(root.resolve("pam.zip"));
            Fixtures.profile(root.resolve("bad name"), "A01", "ADT", "A01", "ADT_A01");
            assertEquals(ErrorCode.INVALID_SET, assertThrows(Refusal.class, registry::reload).code());
            assertEquals(List.of("pam"), registry.names());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(15)
    void replacementAndClosingWaitForReadersAndThenReleaseTheArchive(boolean reload) throws Exception {
        Files.copy(Fixtures.PAM_ZIP, root.resolve(Fixtures.PAM + ".zip"));
        var threads = Executors.newFixedThreadPool(2);
        try (SetRegistry registry = new SetRegistry(root)) {
            CountDownLatch reading = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch closing = new CountDownLatch(1);
            var reader = threads.submit(() -> registry.withSet(Fixtures.PAM, set -> {
                reading.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return set.source(Fixtures.A01).isPresent();
            }));
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            var closer = threads.submit(() -> {
                closing.countDown();
                if (reload) {
                    registry.reload();
                } else {
                    registry.close();
                }
            });
            try {
                assertTrue(closing.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> closer.get(100, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            assertTrue(reader.get(5, TimeUnit.SECONDS));
            closer.get(5, TimeUnit.SECONDS);
            if (reload) {
                assertEquals(List.of(Fixtures.PAM), registry.names());
            } else {
                assertThrows(IllegalStateException.class, registry::names);
            }
            try (ProductSet reopened = ProductSet.open(root.resolve(Fixtures.PAM + ".zip"))) {
                assertTrue(reopened.source(Fixtures.A01).isPresent());
            }
        } finally {
            threads.shutdownNow();
        }
    }
}

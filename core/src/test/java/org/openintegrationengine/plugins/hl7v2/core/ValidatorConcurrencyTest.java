package org.openintegrationengine.plugins.hl7v2.core;

import ca.uhn.hl7v2.util.Terser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorConcurrencyTest {
    @ParameterizedTest
    @ValueSource(strings = {"HAPI", "NIST"})
    @Timeout(60)
    void eightThreadsShareAColdSetAndKeepTheSameReportsAndTheirOwnAcks(String engine) throws Exception {
        List<String> messages = new ArrayList<>();
        for (String file : List.of("adt-a01-admission-pam-fr.er7", "adt-a03-sortie-pam-fr.er7",
                "adt-a08-structure-adt-a01.er7", "ack-pec-conforme.er7")) {
            messages.add(Fixtures.example(file));
        }
        Validator validator = new Validator();
        var threads = Executors.newFixedThreadPool(8);
        try (ProductSet reference = ProductSet.open(Fixtures.PAM_ZIP);
             ProductSet shared = ProductSet.open(Fixtures.PAM_ZIP)) {
            List<Report> expected = messages.stream()
                    .map(m -> validator.validate(m, reference, null, engine).report()).toList();
            CountDownLatch ready = new CountDownLatch(8);
            CountDownLatch start = new CountDownLatch(1);
            java.util.Set<Object> contexts = java.util.concurrent.ConcurrentHashMap.newKeySet();
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                jobs.add(() -> {
                    contexts.add(HapiContexts.current());
                    ready.countDown();
                    start.await();
                    for (int round = 0; round < 8; round++) {
                        int index = round % messages.size();
                        var result = validator.validate(messages.get(index), shared, null, engine);
                        Report report = result.report();
                        Report baseline = expected.get(index);
                        assertEquals(baseline.valid(), report.valid());
                        assertEquals(baseline.profile(), report.profile());
                        assertEquals(baseline.counts(), report.counts());
                        assertEquals(frequencies(baseline.issues()), frequencies(report.issues()));
                        Terser ack = new Terser(HapiContexts.current().getPipeParser().parse(result.ack()));
                        assertEquals(report.messageControlId(), ack.get("/MSA-2"));
                        assertEquals(report.acknowledgmentCode(), ack.get("/MSA-1"));
                    }
                    return null;
                });
            }
            var futures = jobs.stream().map(threads::submit).toList();
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(8, contexts.size(), "Chaque fil doit avoir son propre contexte HAPI");
            start.countDown();
            for (var future : futures) {
                future.get(40, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            threads.shutdownNow();
        }
    }

    private static java.util.Map<Report.Issue, Long> frequencies(List<Report.Issue> issues) {
        return issues.stream().collect(java.util.stream.Collectors.groupingBy(i -> i, java.util.stream.Collectors.counting()));
    }
}

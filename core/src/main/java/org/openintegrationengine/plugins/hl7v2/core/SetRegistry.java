package org.openintegrationengine.plugins.hl7v2.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.nio.file.StandardCopyOption;

/** Jeux ouverts et leur cycle de vie. Reload/close attendent la fin des validations en cours. */
public final class SetRegistry implements AutoCloseable {
    private final Path root;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private Map<String, ProductSet> sets = Map.of();
    private boolean closed;

    public SetRegistry(Path root) {
        this.root = root;
        reload();
    }

    /** Ne pas conserver le ProductSet apres le retour de l'action. */
    public <T> T withSet(String name, Function<ProductSet, T> action) {
        lock.readLock().lock();
        try {
            ensureOpen();
            if (name == null || name.isBlank()) {
                throw new Refusal(ErrorCode.SET_REQUIRED, "Le jeu de profils est obligatoire");
            }
            String wanted = name.trim();
            if (!ProductSet.NAME.matcher(wanted).matches()) {
                throw new Refusal(ErrorCode.INVALID_PARAMETER, "Nom de jeu invalide");
            }
            ProductSet set = sets.get(wanted);
            if (set == null) {
                throw new Refusal(ErrorCode.UNKNOWN_SET, "Jeu de profils inconnu : " + wanted);
            }
            return action.apply(set);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> names() {
        lock.readLock().lock();
        try {
            ensureOpen();
            return List.copyOf(sets.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    public <T> List<T> describe(Function<ProductSet, T> description) {
        lock.readLock().lock();
        try {
            ensureOpen();
            return sets.values().stream().map(description).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** L'archive a deja ete controlee ; disque et registre changent sous le meme verrou. */
    public void install(String name, Path archive) {
        lock.writeLock().lock();
        Path backup = null;
        Path target = root.resolve(name + ".zip");
        boolean replaced = false;
        boolean keepBackup = false;
        try {
            ensureOpen();
            checkArchiveName(name);
            ProductSet previous = sets.get(name);
            if (previous == null && Files.exists(target)) throw new Refusal(ErrorCode.CONFLICT, "Archive presente hors du registre : recharger avant de publier");
            if (previous != null) target = previous.location();
            try (ProductSet candidate = ProductSet.open(archive)) {
                if (previous != null && !previous.version().isEmpty() && previous.version().equals(candidate.version())) {
                    if (previous.hash().equals(candidate.hash())) return;
                    throw new Refusal(ErrorCode.CONFLICT, "Cette version existe avec un autre contenu : incrementer version");
                }
            }
            if (Files.exists(target)) {
                backup = Files.createTempFile(root, ".previous-", ".zip");
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(archive, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            replaced = true;
            ProductSet fresh = ProductSet.open(target);
            sets.put(name, fresh);
            if (previous != null) previous.close();
        } catch (IOException | RuntimeException e) {
            if (replaced) {
                try {
                    if (backup != null) Files.move(backup, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    else Files.deleteIfExists(target);
                } catch (IOException rollback) {
                    keepBackup = true;
                    e.addSuppressed(rollback);
                }
            }
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new java.io.UncheckedIOException((IOException) e);
        } finally {
            if (backup != null && !keepBackup) {
                try { Files.deleteIfExists(backup); } catch (IOException ignored) { }
            }
            lock.writeLock().unlock();
        }
    }

    public void delete(String name) {
        lock.writeLock().lock();
        try {
            ensureOpen();
            checkArchiveName(name);
            ProductSet previous = sets.get(name);
            if (previous == null) throw new Refusal(ErrorCode.UNKNOWN_SET, "Jeu inconnu : " + name);
            Files.delete(previous.location());
            sets.remove(name);
            previous.close();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void checkArchiveName(String name) {
        if (name == null || !ProductSet.NAME.matcher(name).matches()) throw new Refusal(ErrorCode.INVALID_PARAMETER, "Nom de jeu invalide");
        if (Files.isDirectory(root.resolve(name))) throw new Refusal(ErrorCode.CONFLICT, "Un jeu en repertoire se gere sur le volume partage");
    }

    /** Remplacement atomique ; un chargement rate laisse tous les anciens jeux utilisables. */
    public void reload() {
        lock.writeLock().lock();
        try {
            ensureOpen();
            Map<String, ProductSet> loaded = new LinkedHashMap<>();
            try (var paths = Files.list(root)) {
                for (Path path : paths.sorted().toList()) {
                    if (path.getFileName().toString().startsWith(".") || !ProductSet.looksLikeSet(path)) {
                        continue;
                    }
                    ProductSet set = ProductSet.open(path);
                    if (!ProductSet.NAME.matcher(set.name()).matches() || loaded.containsKey(set.name()) || set.profiles().isEmpty()) {
                        set.close();
                        throw new Refusal(ErrorCode.INVALID_SET, "Jeu vide, nom invalide ou en double : " + set.name());
                    }
                    loaded.put(set.name(), set);
                }
            } catch (IOException | RuntimeException e) {
                loaded.values().forEach(ProductSet::close);
                if (e instanceof Refusal refusal) {
                    throw refusal;
                }
                throw new Refusal(ErrorCode.INVALID_SET, "Chargement des jeux impossible", e);
            }
            Map<String, ProductSet> previous = sets;
            sets = loaded;
            previous.values().forEach(ProductSet::close);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            sets.values().forEach(ProductSet::close);
            sets = Map.of();
            closed = true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Registry is closed");
        }
    }
}

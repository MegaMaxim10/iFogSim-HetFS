package org.fog.hetfs.stateful;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Persistent state of a module instance (module D4).
 *
 * <p>iFogSim's {@code AppModule} keeps nothing between two tuples: its output depends
 * only on the input tuple and the selectivity model. A {@code ModuleState} is a
 * key-value store attached to one module instance on one device. Values must be
 * {@link Serializable}: a {@link #snapshot()} is a deep copy obtained by
 * serialisation, so that a checkpoint is unaffected by later updates and its size in
 * bytes is known.
 */
public final class ModuleState {

    private LinkedHashMap<String, Serializable> values = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T get(String key) {
        return (T) values.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T extends Serializable> T getOrDefault(String key, T defaultValue) {
        Serializable v = values.get(key);
        return v == null ? defaultValue : (T) v;
    }

    public void put(String key, Serializable value) {
        values.put(key, value);
    }

    public void remove(String key) {
        values.remove(key);
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    /** Increments a {@code long} counter and returns its new value. */
    public long increment(String key) {
        long v = getOrDefault(key, 0L) + 1;
        values.put(key, v);
        return v;
    }

    public void clear() {
        values.clear();
    }

    public Map<String, Serializable> asMap() {
        return Collections.unmodifiableMap(values);
    }

    /** Serialised copy of the current state. */
    public byte[] snapshot() {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(values);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("module state is not serialisable", e);
        }
    }

    /** Replaces the current state by a snapshot taken earlier. */
    @SuppressWarnings("unchecked")
    public void restore(byte[] snapshot) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(snapshot))) {
            values = (LinkedHashMap<String, Serializable>) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("corrupted module snapshot", e);
        }
    }
}

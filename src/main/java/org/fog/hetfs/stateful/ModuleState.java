package org.fog.hetfs.stateful;

import java.io.*;
import java.util.*;

/**
 * A serializable key-value store for maintaining state in stateful application modules.
 * Supports checkpoint/restore semantics for state persistence and recovery.
 */
public class ModuleState implements Serializable {
    private static final long serialVersionUID = 1L;

    private Map<String, Object> state;

    /**
     * Creates an empty module state.
     */
    public ModuleState() {
        this.state = new HashMap<>();
    }

    /**
     * Creates a module state initialized with the given map.
     *
     * @param initialState the initial state map
     */
    public ModuleState(Map<String, Object> initialState) {
        this.state = new HashMap<>(initialState);
    }

    /**
     * Gets a value from the state store.
     *
     * @param key the state key
     * @return the value associated with the key, or null if not found
     */
    public Object get(String key) {
        return state.get(key);
    }

    /**
     * Gets a value from the state store with a default if not found.
     *
     * @param key the state key
     * @param defaultValue the value to return if key is not found
     * @return the value associated with the key, or defaultValue if not found
     */
    public Object getOrDefault(String key, Object defaultValue) {
        return state.getOrDefault(key, defaultValue);
    }

    /**
     * Stores a value in the state store.
     *
     * @param key the state key
     * @param value the value to store
     */
    public void put(String key, Object value) {
        state.put(key, value);
    }

    /**
     * Removes a value from the state store.
     *
     * @param key the state key
     * @return the removed value, or null if not found
     */
    public Object remove(String key) {
        return state.remove(key);
    }

    /**
     * Checks if the state store contains a key.
     *
     * @param key the state key
     * @return true if the key exists in state
     */
    public boolean containsKey(String key) {
        return state.containsKey(key);
    }

    /**
     * Gets all keys in the state store.
     *
     * @return a set of all state keys
     */
    public Set<String> keySet() {
        return new HashSet<>(state.keySet());
    }

    /**
     * Gets the number of entries in the state store.
     *
     * @return the state size
     */
    public int size() {
        return state.size();
    }

    /**
     * Clears all entries from the state store.
     */
    public void clear() {
        state.clear();
    }

    /**
     * Creates a checkpoint (serialized copy) of the current state.
     *
     * @return a byte array containing the serialized state
     * @throws IOException if serialization fails
     */
    public byte[] checkpoint() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(state);
        oos.close();
        return baos.toByteArray();
    }

    /**
     * Restores state from a checkpoint (serialized copy).
     *
     * @param checkpoint the byte array from a previous checkpoint() call
     * @throws IOException if deserialization fails
     * @throws ClassNotFoundException if a class in the state cannot be found
     */
    @SuppressWarnings("unchecked")
    public void restore(byte[] checkpoint) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(checkpoint);
        ObjectInputStream ois = new ObjectInputStream(bais);
        this.state = (Map<String, Object>) ois.readObject();
        ois.close();
    }

    /**
     * Creates a deep copy of this state.
     *
     * @return a new ModuleState with the same entries
     */
    public ModuleState copy() {
        return new ModuleState(new HashMap<>(state));
    }

    @Override
    public String toString() {
        return String.format("ModuleState(entries=%d, keys=%s)", state.size(), state.keySet());
    }
}

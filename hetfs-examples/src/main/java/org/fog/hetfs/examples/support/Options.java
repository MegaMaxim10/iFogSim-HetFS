package org.fog.hetfs.examples.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Command-line options of the form {@code --name value} or {@code --flag}.
 *
 * <p>Only the names passed to {@link #parse} are accepted, so that a misspelt option
 * fails instead of being silently ignored. A flag given without a value has the value
 * {@code "true"}.
 */
public final class Options {

    private final Map<String, String> values;

    private Options(Map<String, String> values) {
        this.values = values;
    }

    /**
     * Parses {@code args}.
     *
     * @param known the accepted option names, without the leading {@code --}
     * @throws IllegalArgumentException on a positional argument or an unknown option
     */
    public static Options parse(String[] args, String... known) {
        Set<String> accepted = new LinkedHashSet<>(Arrays.asList(known));
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + args[i]);
            }
            String name = args[i].substring(2);
            if (!accepted.contains(name)) {
                throw new IllegalArgumentException("unknown option --" + name + "; accepted: " + accepted);
            }
            boolean hasValue = i + 1 < args.length && !args[i + 1].startsWith("--");
            values.put(name, hasValue ? args[++i] : "true");
        }
        return new Options(values);
    }

    /** Whether the option was given. */
    public boolean has(String name) {
        return values.containsKey(name);
    }

    /** Value of the option, or {@code defaultValue} if it was not given. */
    public String get(String name, String defaultValue) {
        return values.getOrDefault(name, defaultValue);
    }

    /** Value of a mandatory option. */
    public String require(String name) {
        String v = values.get(name);
        if (v == null) {
            throw new IllegalArgumentException("missing option --" + name);
        }
        return v;
    }

    /** Integer value of the option, or {@code defaultValue} if it was not given. */
    public int getInt(String name, int defaultValue) {
        return has(name) ? Integer.parseInt(values.get(name)) : defaultValue;
    }

    /** Long value of the option, or {@code defaultValue} if it was not given. */
    public long getLong(String name, long defaultValue) {
        return has(name) ? Long.parseLong(values.get(name)) : defaultValue;
    }

    /** Double value of the option, or {@code defaultValue} if it was not given. */
    public double getDouble(String name, double defaultValue) {
        return has(name) ? Double.parseDouble(values.get(name)) : defaultValue;
    }

    /** The options given, in command-line order. */
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(values);
    }
}

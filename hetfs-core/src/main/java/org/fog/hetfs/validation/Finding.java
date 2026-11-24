package org.fog.hetfs.validation;

/** One problem found by {@link ApplicationValidator}. */
public final class Finding {

    /** Severity of a finding. */
    public enum Severity { ERROR, WARNING }

    private final Severity severity;
    private final String code;
    private final String message;

    Finding(Severity severity, String code, String message) {
        this.severity = severity;
        this.code = code;
        this.message = message;
    }

    public Severity getSeverity() {
        return severity;
    }

    /** Stable identifier of the check (e.g. {@code SHARED_SENSOR_TUPLE_TYPE}). */
    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return severity + " " + code + ": " + message;
    }
}

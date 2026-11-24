package org.fog.hetfs.core;

/** Why a {@link HetFSFogDevice} discarded a tuple. */
public enum DropReason {

    /** The device was failed when the tuple arrived, or when the tuple finished executing (D5). */
    DEVICE_FAILED("device failed"),

    /** No device hosts the destination module of the tuple (D1). */
    UNPLACED_DESTINATION("destination module not placed on any device"),

    /** Hosts of the destination exist, but none can be reached over usable links (D1). */
    UNREACHABLE_DESTINATION("no usable path to destination");

    private final String description;

    DropReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}

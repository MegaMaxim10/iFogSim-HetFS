package org.fog.hetfs.core;

import org.cloudbus.cloudsim.core.CloudSimTags;

/**
 * Event tags introduced by iFogSim-HetFS. CloudSim 7 identifies event tags by enum
 * constant rather than by integer, so these tags cannot collide with those of
 * CloudSim ({@code CloudActionTags}) or iFogSim ({@code FogEvents}).
 */
public enum HetFSEvents implements CloudSimTags {
    /** A {@code PhysicalLink} has finished serialising a tuple (D1). */
    LINK_FREE,
    /** Periodic checkpoint of the stateful modules of a device (D4). */
    CHECKPOINT,
    /** A scheduled change of the physical topology (D5). */
    TOPOLOGY_EVENT
}

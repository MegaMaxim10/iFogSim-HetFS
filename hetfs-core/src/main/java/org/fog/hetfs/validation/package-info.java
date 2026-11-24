/**
 * Static checks of an iFogSim application, its sensors and its placement before the
 * simulation starts: sensor tuple types shared by several modules, modules missing
 * from a mapping, downward fan-outs that edge-ward placement cannot handle, loops
 * that share their start module, and unnamed sensors.
 */
package org.fog.hetfs.validation;

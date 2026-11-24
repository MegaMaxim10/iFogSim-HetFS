package org.fog.hetfs.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.hetfs.core.HetFSSensor;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;

/**
 * Static checks for iFogSim configurations that fail silently (challenge C8 of the
 * companion paper). Run it after building the application, the sensors and the
 * placement, before starting the simulation.
 *
 * <ul>
 *   <li>{@code SHARED_SENSOR_TUPLE_TYPE} (error): several edges leave the same sensor
 *       tuple type for different modules and a plain {@code Sensor} emits that type.
 *       {@code Sensor#transmit()} keeps the last matching edge, so every such sensor
 *       feeds the same module.</li>
 *   <li>{@code UNMAPPED_MODULE} (error): with {@code ModulePlacementMapping}, a module
 *       that is the destination of an edge is mapped to no device. It is never
 *       instantiated; its tuples are forwarded instead of executed and every loop
 *       through it breaks, without any error.</li>
 *   <li>{@code EDGEWARDS_DOWNWARD_FANOUT} (warning): with
 *       {@code ModulePlacementEdgewards}, a module sends DOWN tuples to several
 *       modules; edge-ward placement fails with a {@code NullPointerException} in
 *       {@code placeModulesInPath} for such fan-outs.</li>
 *   <li>{@code LOOP_SHARES_START_MODULE} (warning): a loop contains, at an interior
 *       position, the start module of another loop. Crossing that module overwrites
 *       the tuple's timing id, so the first loop is never credited.</li>
 *   <li>{@code UNNAMED_SENSOR} (warning): a plain {@code Sensor} built with one of the
 *       two long constructors, which leave the sensor name {@code null} (they assign
 *       the field to itself); the source module of its tuples is then {@code null} and
 *       loops starting at the sensor are never tracked.</li>
 * </ul>
 */
public final class ApplicationValidator {

    private ApplicationValidator() {
    }

    /** Placement policy family, for the checks that depend on it. */
    public enum Placement { MAPPING, EDGEWARDS, OTHER }

    public static List<Finding> validate(Application app, List<? extends Sensor> sensors, ModulePlacement placement,
                                         ModuleMapping mapping) {
        Placement kind = placement instanceof ModulePlacementMapping ? Placement.MAPPING
                : placement instanceof ModulePlacementEdgewards ? Placement.EDGEWARDS : Placement.OTHER;
        return validate(app, sensors, kind, mapping);
    }

    public static List<Finding> validate(Application app, List<? extends Sensor> sensors, Placement placement,
                                         ModuleMapping mapping) {
        List<Finding> out = new ArrayList<>();
        checkSensorTupleTypes(app, sensors, out);
        if (placement == Placement.MAPPING && mapping != null) {
            checkUnmappedModules(app, mapping, out);
        }
        if (placement == Placement.EDGEWARDS) {
            checkDownwardFanOut(app, out);
        }
        checkLoops(app, out);
        checkSensorNames(sensors, out);
        return out;
    }

    /** Throws if any error is found; returns the warnings. */
    public static List<Finding> validateOrThrow(Application app, List<? extends Sensor> sensors,
                                                ModulePlacement placement, ModuleMapping mapping) {
        List<Finding> all = validate(app, sensors, placement, mapping);
        return split(all);
    }

    private static List<Finding> split(List<Finding> all) {
        List<Finding> warnings = new ArrayList<>();
        StringBuilder errors = new StringBuilder();
        for (Finding f : all) {
            if (f.getSeverity() == Finding.Severity.ERROR) {
                errors.append("\n  ").append(f);
            } else {
                warnings.add(f);
            }
        }
        if (errors.length() > 0) {
            throw new IllegalStateException("invalid iFogSim configuration:" + errors);
        }
        return warnings;
    }

    private static void checkSensorTupleTypes(Application app, List<? extends Sensor> sensors, List<Finding> out) {
        Map<String, Set<String>> destinations = new LinkedHashMap<>();
        for (AppEdge e : app.getEdges()) {
            if (e.getEdgeType() == AppEdge.SENSOR) {
                destinations.computeIfAbsent(e.getSource(), k -> new LinkedHashSet<>()).add(e.getDestination());
            }
        }
        for (Sensor s : sensors) {
            Set<String> d = destinations.get(s.getTupleType());
            boolean disambiguated = s instanceof HetFSSensor && s.getDestModuleName() != null;
            if (d != null && d.size() > 1 && !disambiguated) {
                out.add(new Finding(Finding.Severity.ERROR, "SHARED_SENSOR_TUPLE_TYPE", "sensor " + s.getName()
                        + " emits " + s.getTupleType() + ", which feeds " + d
                        + "; Sensor#transmit() always uses the last of these edges"));
            }
        }
    }

    private static void checkUnmappedModules(Application app, ModuleMapping mapping, List<Finding> out) {
        Set<String> mapped = new LinkedHashSet<>();
        for (List<String> modules : mapping.getModuleMapping().values()) {
            mapped.addAll(modules);
        }
        Set<String> moduleNames = new LinkedHashSet<>();
        for (AppModule m : app.getModules()) {
            moduleNames.add(m.getName());
        }
        for (String m : moduleNames) {
            if (!mapped.contains(m)) {
                out.add(new Finding(Finding.Severity.ERROR, "UNMAPPED_MODULE", "module " + m
                        + " is mapped to no device: it will never be instantiated and tuples sent to it"
                        + " will be forwarded, not executed"));
            }
        }
    }

    private static void checkDownwardFanOut(Application app, List<Finding> out) {
        Map<String, Integer> down = new HashMap<>();
        for (AppEdge e : app.getEdges()) {
            if (e.getEdgeType() == AppEdge.MODULE && e.getDirection() == Tuple.DOWN) {
                down.merge(e.getSource(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : down.entrySet()) {
            if (e.getValue() > 1) {
                out.add(new Finding(Finding.Severity.WARNING, "EDGEWARDS_DOWNWARD_FANOUT", "module " + e.getKey()
                        + " sends DOWN tuples to " + e.getValue() + " modules; ModulePlacementEdgewards"
                        + " fails on such fan-outs, use ModulePlacementMapping"));
            }
        }
    }

    private static void checkLoops(Application app, List<Finding> out) {
        if (app.getLoops() == null) {
            return;
        }
        for (AppLoop a : app.getLoops()) {
            List<String> m = a.getModules();
            for (AppLoop b : app.getLoops()) {
                if (a == b) {
                    continue;
                }
                int i = m.indexOf(b.getStartModule());
                if (i > 0 && i < m.size() - 1) {
                    out.add(new Finding(Finding.Severity.WARNING, "LOOP_SHARES_START_MODULE", "loop " + m
                            + " crosses " + b.getStartModule() + ", where loop " + b.getModules()
                            + " starts; the timing id is overwritten there and " + m + " is never credited"));
                }
            }
        }
    }

    private static void checkSensorNames(List<? extends Sensor> sensors, List<Finding> out) {
        for (Sensor s : sensors) {
            if (!(s instanceof HetFSSensor) && s.getSensorName() == null) {
                out.add(new Finding(Finding.Severity.WARNING, "UNNAMED_SENSOR", "sensor " + s.getName()
                        + " has a null sensor name (long Sensor constructors); loops starting at it are not tracked"));
            }
        }
    }
}

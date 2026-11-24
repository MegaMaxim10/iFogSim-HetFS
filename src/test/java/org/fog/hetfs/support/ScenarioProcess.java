package org.fog.hetfs.support;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs a simulation main class in a fresh JVM (CloudSim and iFogSim keep global static
 * state, so two simulations cannot share a process) and returns the CSV row it wrote.
 */
public final class ScenarioProcess {

    private ScenarioProcess() {
    }

    public static Map<String, String> run(String mainClass, String... args) throws Exception {
        File out = File.createTempFile("hetfs-test", ".csv");
        out.delete();
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("--add-opens");
        cmd.add("java.base/java.lang=ALL-UNNAMED");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(mainClass);
        for (String a : args) {
            cmd.add(a);
        }
        cmd.add("--out");
        cmd.add(out.getAbsolutePath());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] log = p.getInputStream().readAllBytes();
        if (!p.waitFor(120, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IOException("simulation failed:\n" + new String(log, StandardCharsets.UTF_8));
        }
        List<String> lines = Files.readAllLines(out.toPath(), StandardCharsets.UTF_8);
        out.delete();
        String[] h = lines.get(0).split(",", -1);
        String[] v = lines.get(1).split(",", -1);
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < h.length; i++) {
            row.put(h[i], v[i]);
        }
        return row;
    }

    public static double num(Map<String, String> row, String column) {
        return Double.parseDouble(row.get(column));
    }
}

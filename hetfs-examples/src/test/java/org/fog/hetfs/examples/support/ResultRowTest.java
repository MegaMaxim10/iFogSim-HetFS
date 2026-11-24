package org.fog.hetfs.examples.support;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public class ResultRowTest {

    @Test
    public void formatsValuesInColumnOrder() {
        ResultRow row = new ResultRow().put("label", "").put("n", 3).put("x", 0.5).put("y", null).put("ok", true);
        assertEquals("label,n,x,y,ok", row.header());
        assertEquals(",3,0.500000,,true", row.values());
    }

    @Test
    public void putKeepsTheFirstPosition() {
        ResultRow row = new ResultRow().put("a", 1).put("b", 2).put("a", 3);
        assertEquals("a,b", row.header());
        assertEquals("3,2", row.values());
    }

    @Test(expected = IllegalArgumentException.class)
    public void separatorInValueIsRejected() {
        new ResultRow().put("zones", "z1,z2").values();
    }

    @Test
    public void appendWritesTheHeaderOnce() throws Exception {
        Path file = Files.createTempFile("result-row", ".csv");
        Files.delete(file);
        try {
            new ResultRow().put("a", 1).appendTo(file);
            new ResultRow().put("a", 2).appendTo(file);
            assertEquals(List.of("a", "1", "2"), Files.readAllLines(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void appendRejectsADifferentHeader() throws Exception {
        Path file = Files.createTempFile("result-row", ".csv");
        Files.delete(file);
        try {
            new ResultRow().put("a", 1).appendTo(file);
            new ResultRow().put("b", 1).appendTo(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}

package org.fog.hetfs.stateful;

import org.fog.application.AppTuple;

import java.util.*;

/**
 * A reference implementation of a stateful module: an LRU cache that stores
 * distance maps keyed on zone ID. Tracks cache hits and misses, with configurable
 * MI costs for hit and miss operations.
 */
public class CacheModule extends StatefulAppModule {
    private static final String CACHE_KEY = "lruCache";
    private static final String HIT_COUNT_KEY = "hitCount";
    private static final String MISS_COUNT_KEY = "missCount";
    private static final String ZONE_DATA_KEY_PREFIX = "zone_";

    private int cacheSize;
    private long hitMI;
    private long missMI;
    private LinkedHashMap<Integer, double[]> lruCache;
    private long cacheHits;
    private long cacheMisses;

    /**
     * Creates a new cache module with the specified configuration.
     *
     * @param name the module name
     * @param cacheSize the maximum number of zones to cache
     * @param hitMI the MI cost when a cache hit occurs
     * @param missMI the MI cost when a cache miss occurs
     */
    public CacheModule(String name, int cacheSize, long hitMI, long missMI) {
        super(name);
        this.cacheSize = cacheSize;
        this.hitMI = hitMI;
        this.missMI = missMI;
        this.cacheHits = 0;
        this.cacheMisses = 0;

        this.lruCache = new LinkedHashMap<Integer, double[]>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > CacheModule.this.cacheSize;
            }
        };
    }

    @Override
    public void onInit(ModuleState initialState) {
        if (initialState != null && initialState.containsKey(CACHE_KEY)) {
            lruCache = (LinkedHashMap<Integer, double[]>) initialState.get(CACHE_KEY);
        }
        if (initialState != null && initialState.containsKey(HIT_COUNT_KEY)) {
            cacheHits = (Long) initialState.get(HIT_COUNT_KEY);
        }
        if (initialState != null && initialState.containsKey(MISS_COUNT_KEY)) {
            cacheMisses = (Long) initialState.get(MISS_COUNT_KEY);
        }
    }

    @Override
    public void onTupleArrival(AppTuple tuple, ModuleState currentState) {
        if (tuple == null || tuple.getValue() == null) {
            return;
        }

        Object valueObj = tuple.getValue();
        if (valueObj instanceof Integer) {
            int zoneId = (Integer) valueObj;
            accessZone(zoneId);
        } else if (valueObj instanceof int[]) {
            int[] zoneIds = (int[]) valueObj;
            for (int zoneId : zoneIds) {
                accessZone(zoneId);
            }
        }
    }

    /**
     * Accesses a zone in the cache, performing a lookup or insertion if needed.
     *
     * @param zoneId the zone identifier
     */
    private void accessZone(int zoneId) {
        if (lruCache.containsKey(zoneId)) {
            cacheHits++;
            lruCache.get(zoneId);
        } else {
            cacheMisses++;
            double[] distanceMap = generateDistanceMap(zoneId);
            lruCache.put(zoneId, distanceMap);
        }
        state.put(CACHE_KEY, lruCache);
        state.put(HIT_COUNT_KEY, cacheHits);
        state.put(MISS_COUNT_KEY, cacheMisses);
    }

    /**
     * Generates a synthetic distance map for a zone.
     *
     * @param zoneId the zone identifier
     * @return a double array representing distances
     */
    private double[] generateDistanceMap(int zoneId) {
        double[] map = new double[10];
        for (int i = 0; i < map.length; i++) {
            map[i] = Math.random() * 100.0 + zoneId;
        }
        return map;
    }

    @Override
    public ModuleState onCheckpoint() {
        ModuleState snap = new ModuleState();
        snap.put(CACHE_KEY, new LinkedHashMap<>(lruCache));
        snap.put(HIT_COUNT_KEY, cacheHits);
        snap.put(MISS_COUNT_KEY, cacheMisses);
        return snap;
    }

    @Override
    public void onRestore(ModuleState recoveredState) {
        this.state = recoveredState;
        if (recoveredState.containsKey(CACHE_KEY)) {
            lruCache = (LinkedHashMap<Integer, double[]>) recoveredState.get(CACHE_KEY);
        }
        if (recoveredState.containsKey(HIT_COUNT_KEY)) {
            cacheHits = (Long) recoveredState.get(HIT_COUNT_KEY);
        }
        if (recoveredState.containsKey(MISS_COUNT_KEY)) {
            cacheMisses = (Long) recoveredState.get(MISS_COUNT_KEY);
        }
    }

    /**
     * Gets the cache hit rate as a fraction.
     *
     * @return the hit rate between 0 and 1
     */
    public double getHitRate() {
        long total = cacheHits + cacheMisses;
        return total > 0 ? (double) cacheHits / total : 0.0;
    }

    /**
     * Gets the total number of cache hits.
     *
     * @return the hit count
     */
    public long getCacheHits() {
        return cacheHits;
    }

    /**
     * Gets the total number of cache misses.
     *
     * @return the miss count
     */
    public long getCacheMisses() {
        return cacheMisses;
    }

    /**
     * Gets the number of zones currently cached.
     *
     * @return the cache size
     */
    public int getCachedZoneCount() {
        return lruCache.size();
    }

    /**
     * Gets the MI cost for a cache access.
     *
     * @param isHit true if this is a cache hit, false for miss
     * @return the MI cost
     */
    public long getMICost(boolean isHit) {
        return isHit ? hitMI : missMI;
    }

    @Override
    public String toString() {
        return String.format("CacheModule(name=%s, cached=%d/%d, hits=%d, misses=%d, hitRate=%.2f%%)",
                getName(), lruCache.size(), cacheSize, cacheHits, cacheMisses, getHitRate() * 100);
    }
}

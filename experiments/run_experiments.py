#!/usr/bin/env python3
"""Runs the iFogSim-HetFS evaluation and writes one CSV per experiment in results/.

Usage: experiments/run_experiments.py [experiment ...]   (default: all)
Each simulation runs in its own JVM (iFogSim keeps global static state). The
overhead experiment runs its simulations one at a time; the others use two
worker processes.
"""
import csv
import itertools
import os
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(HERE, "results")
LIB = os.path.join(ROOT, "lib")
CP = ":".join([os.path.join(ROOT, "hetfs-core", "target", "classes"),
               os.path.join(ROOT, "hetfs-examples", "target", "classes")]
              + sorted(os.path.join(LIB, f) for f in os.listdir(LIB) if f.endswith(".jar")))
EMERGENCY = "org.fog.hetfs.examples.emergency.EmergencyScenario"
REFERENCE = "org.fog.hetfs.examples.reference.ReferenceScenarios"
SEEDS = [1, 2, 3, 4, 5]
ZONES = ["z1", "z1,z2", "z1,z2,z3", "z1,z2,z3,z4", "z1,z2,z3,z4,z5"]
FINE = ["--min-time-between-events", "0.000001"]


def java(main, args, out):
    cmd = ["java", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-Xmx2g", "-cp", CP, main] \
        + [str(a) for a in args] + ["--out", out]
    r = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if r.returncode != 0:
        raise RuntimeError(" ".join(cmd) + "\n" + r.stdout[-2000:])


def batch(name, main, arglists, workers=2):
    out = os.path.join(RES, name + ".csv")
    if os.path.exists(out):
        os.remove(out)
    print(f"{name}: {len(arglists)} runs", flush=True)
    # one file per run, merged at the end
    def one(i_a):
        i, a = i_a
        p = f"{out}.part{i:05d}"
        java(main, a, p)
        return p
    with ThreadPoolExecutor(workers) as ex:
        parts = list(ex.map(one, enumerate(arglists)))
    # runs of one experiment may have different columns (e.g. vanilla vs HetFS
    # devices): merge on the union of the columns, in order of first appearance
    rows, header = [], []
    for p in parts:
        with open(p, newline="") as f:
            for row in csv.DictReader(f):
                rows.append(row)
                header += [k for k in row if k not in header]
        os.remove(p)
    with open(out, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=header, restval="")
        w.writeheader()
        w.writerows(rows)


# ----------------------------------------------------------------------
def companion_reproduction():
    runs = []
    for zones, seed, dev in itertools.product(ZONES[:4], SEEDS, ["vanilla", "hetfs"]):
        runs.append(["--zones", zones, "--seed", seed, "--devices", dev, "--label", "sens_conc", "--run", seed])
    for zone, dev in itertools.product(["z1", "z2", "z3", "z4", "z5"], ["vanilla", "hetfs"]):
        runs.append(["--zones", zone, "--seed", 42, "--devices", dev, "--label", "exp2", "--run", zone])
    batch("companion_reproduction", EMERGENCY, runs)


def gupta_reproduction():
    runs = []
    for app, pl, g, seed, mode in itertools.product(["vrgame", "dcns"], ["edgeward", "cloud"], [1, 2, 4, 8, 16],
                                                    SEEDS, ["vanilla", "hetfs", "exact"]):
        a = ["--app", app, "--placement", pl, "--groups", g, "--per-group", 4, "--seed", seed, "--run", seed]
        if mode == "vanilla":
            a += ["--devices", "vanilla"]
        else:
            a += ["--devices", "hetfs"] + (["--exact-completion"] if mode == "exact" else [])
        runs.append(a + ["--label", mode])
    batch("gupta_reproduction", REFERENCE, runs)


def gupta_scheduling():
    runs = []
    for app, pl, g, si in itertools.product(["vrgame", "dcns"], ["edgeward", "cloud"], [1, 4], [10, 1, 0.1]):
        runs.append(["--app", app, "--placement", pl, "--groups", g, "--per-group", 4, "--seed", 1,
                     "--devices", "vanilla", "--scheduling-interval", si, "--label", f"si={si}"])
    batch("gupta_scheduling", REFERENCE, runs)


def concurrency():
    runs = []
    for zones, seed, exact in itertools.product(ZONES, SEEDS, [False, True]):
        a = ["--zones", zones, "--seed", seed, "--trace", "--monitor", "--run", seed]
        runs.append(a + (["--exact-completion", "--label", "exact"] if exact else ["--label", "default"]))
    batch("concurrency", EMERGENCY, runs)


def scheduling_interval():
    runs = []
    for si, zones in itertools.product([10, 5, 1, 0.1, 0.01], ZONES[:4]):
        runs.append(["--zones", zones, "--seed", 1, "--devices", "vanilla", "--scheduling-interval", si,
                     "--label", f"si={si}"])
    batch("scheduling_interval", EMERGENCY, runs)


def routing_kernel():
    runs = []
    base = ["--routing-kernel", "--exact-completion", "--monitor", "--trace"]
    for nodes, accel, zones in itertools.product([25, 50, 75, 100, 200, 400], ["none", "fpga:10"], [ZONES[0], ZONES[4]]):
        a = base + FINE + ["--road-nodes", nodes, "--zones", zones, "--label", "size"]
        runs.append(a + ([] if accel == "none" else ["--accel", accel]))
    for p, f, o in itertools.product([2, 5, 10, 20, 50], [1.0, 0.9], [0.0, 0.02]):
        runs.append(base + FINE + ["--road-nodes", 100, "--zones", ZONES[4], "--accel", f"fpga:{p}:{f}:{o}",
                                   "--label", "fpga"])
    for res, accel, zones in itertools.product(["default", "fine"], ["none", "fpga:10"], [ZONES[0], ZONES[4]]):
        a = base + ["--road-nodes", 25, "--zones", zones, "--label", f"resolution-{res}"]
        a += FINE if res == "fine" else []
        runs.append(a + ([] if accel == "none" else ["--accel", accel]))
    batch("routing_kernel", EMERGENCY, runs)


def path_cache():
    runs = []
    base = ["--routing-kernel", "--exact-completion", "--monitor", "--trace", "--road-nodes", 100] + FINE
    for cap in ["0", "1", "2", "3", "5", "5:8:500"]:
        a = base + ["--zones", ZONES[4], "--label", f"cache={cap}"]
        runs.append(a + ([] if cap == "0" else ["--cache", cap]))
    for ck in ["none", "100"]:
        a = base + ["--zones", ZONES[4], "--cache", "5", "--fail-device", "esn@500-800", "--label", f"outage-ckpt={ck}"]
        runs.append(a + ([] if ck == "none" else ["--checkpoint-interval", ck]))
    batch("path_cache", EMERGENCY, runs)


def faults():
    runs = []
    for graph, seed in itertools.product(["tree", "mesh"], SEEDS):
        for fail in [None, "cad-z1~esn@500-1500"]:
            a = ["--graph", graph, "--zones", "z1,z2", "--seed", seed, "--exact-completion", "--trace", "--run", seed]
            a += ["--label", "baseline"] if fail is None else ["--fail-link", fail, "--label", "uplink-failure"]
            runs.append(a)
    batch("faults", EMERGENCY, runs)


def validation():
    runs = []
    for sensors in ["plain", "hetfs"]:
        a = ["--shared-incident-type", "--zones", "z1,z2", "--focal-zone", "z1", "--validate", "--monitor",
             "--label", sensors]
        runs.append(a + (["--hetfs-sensors"] if sensors == "hetfs" else []))
    batch("validation", EMERGENCY, runs)


def battery():
    runs = []
    # 1 J cell, 10 mJ per alert, 0.5 W idle; alerts every 10, 20 or 50 ms
    for interval in [10, 20, 50]:
        runs.append(["--trace", "--exact-completion", "--battery", "1:0.01:0.5", "--sensor-interval", interval,
                     "--label", f"interval={interval}"])
    batch("battery", EMERGENCY, runs)


def overhead():
    runs = []
    configs = {"vanilla": ["--devices", "vanilla"], "hetfs": ["--devices", "hetfs"],
               "hetfs-graph": ["--devices", "hetfs", "--graph"],
               "hetfs-full": ["--devices", "hetfs", "--graph", "--exact-completion", "--monitor", "--trace"]}
    for g, rep, (name, extra) in itertools.product([1, 2, 4, 8, 16, 32], [1, 2, 3, 4, 5], configs.items()):
        runs.append(["--app", "vrgame", "--groups", g, "--per-group", 4, "--seed", rep, "--run", rep,
                     "--label", name] + extra)
    batch("overhead", REFERENCE, runs, workers=1)


ALL = {f.__name__: f for f in [companion_reproduction, gupta_reproduction, gupta_scheduling, concurrency, scheduling_interval,
                               routing_kernel, path_cache, faults, validation, battery, overhead]}

if __name__ == "__main__":
    os.makedirs(RES, exist_ok=True)
    for name in sys.argv[1:] or list(ALL):
        ALL[name]()

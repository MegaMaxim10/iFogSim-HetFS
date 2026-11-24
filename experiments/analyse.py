#!/usr/bin/env python3
"""Summarises the CSV files of results/ into tables/ (LaTeX fragments) and prints
the figures quoted in the paper. Usage: experiments/analyse.py"""
import os

import numpy as np
import pandas as pd

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "results")
REF = os.path.join(HERE, "reference", "companion")
TAB = os.path.join(HERE, "tables")
T975 = {1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571, 6: 2.447, 7: 2.365, 8: 2.306, 9: 2.262}


def load(name):
    return pd.read_csv(os.path.join(RES, name + ".csv"))


def ci(x):
    x = np.asarray(x, dtype=float)
    n = len(x)
    return T975.get(n - 1, 1.96) * x.std(ddof=1) / np.sqrt(n) if n > 1 else float("nan")


def nzones(df):
    return df.active_zones.astype(str).str.count(";") + 1


def tex(name, header, rows, caption, label, align=None):
    os.makedirs(TAB, exist_ok=True)
    align = align or "l" + "r" * (len(header) - 1)
    lines = [r"\begin{table}[htbp]", r"\centering", r"\caption{" + caption + "}", r"\label{" + label + "}",
             r"\begin{adjustbox}{max width=\textwidth}", r"\begin{tabular}{" + align + "}",
             r"\toprule", " & ".join(header) + r" \\", r"\midrule"]
    lines += [" & ".join(str(c) for c in r) + r" \\" for r in rows]
    lines += [r"\bottomrule", r"\end{tabular}", r"\end{adjustbox}", r"\end{table}"]
    with open(os.path.join(TAB, name + ".tex"), "w") as f:
        f.write("\n".join(lines) + "\n")


def f(x, d=2):
    return "" if pd.isna(x) else f"{x:.{d}f}"


# ----------------------------------------------------------------------
def companion_reproduction():
    d = load("companion_reproduction")
    ref = pd.concat([pd.read_csv(os.path.join(REF, "sensitivity_concurrency.csv")).assign(label="sens_conc"),
                     pd.read_csv(os.path.join(REF, "exp2_alert_dispatch.csv")).assign(label="exp2")])
    ref["key"] = ref.label + "|" + ref.active_zones + "|" + ref.seed.astype(str)
    d["key"] = d.label + "|" + d.active_zones + "|" + d.seed.astype(str)
    out = {}
    for dev in ["vanilla", "hetfs"]:
        m = d[d.devices == dev].merge(ref, on="key", suffixes=("", "_ref"))
        diff = np.maximum(abs(m.alert_latency_ms - m.alert_latency_ms_ref),
                          abs(m.dispatch_latency_ms - m.dispatch_latency_ms_ref))
        out[dev] = (len(m), int((diff < 5e-7).sum()), diff.max())
    print("companion reproduction (runs, identical to 1e-6 ms, max diff):", out)
    return out


def gupta():
    d = load("gupta_reproduction")
    key = ["app", "placement", "groups", "seed"]
    v = d[d.label == "vanilla"].set_index(key)
    h = d[d.label == "hetfs"].set_index(key)
    e = d[d.label == "exact"].set_index(key)
    cols = [c for c in ["loop_eeg_display_ms", "loop_motion_tracker_ms", "loop_tracker_ptz_ms", "energy_cloud_j",
                        "energy_edge_j", "energy_end_j", "network_usage", "cloud_cost"] if c in d.columns]
    same = 0
    for idx in v.index:
        same += all((pd.isna(v.loc[idx, c]) and pd.isna(h.loc[idx, c])) or v.loc[idx, c] == h.loc[idx, c] for c in cols)
    print(f"gupta: {same}/{len(v)} configurations identical between FogDevice and HetFSFogDevice")
    rows = []
    for app, loop in [("vrgame", "loop_eeg_display_ms"), ("dcns", "loop_motion_tracker_ms")]:
        for g in sorted(d.groups.unique()):
            r = [app if g == 1 else "", g * 4]
            for pl in ["cloud", "edgeward"]:
                vv = v.xs((app, pl, g), level=(0, 1, 2))
                ee = e.xs((app, pl, g), level=(0, 1, 2))
                r += [f(vv[loop].mean(), 1), f(ee[loop].mean(), 1), f(vv.network_usage.mean() / 1000, 1)]
            rows.append(r)
    tex("gupta", ["Case", "End dev.", "Cloud: iFogSim", "Cloud: exact", "Cloud: net. (k)",
                  "Edge: iFogSim", "Edge: exact", "Edge: net. (k)"], rows,
        "Original iFogSim case studies (EEG game and camera network): mean control-loop latency (ms) under "
        "iFogSim's completion detection and with exact completion events (D3), and network usage, "
        "5 seeded runs per configuration.", "tab:gupta")
    return v, e


def gupta_scheduling():
    d = load("gupta_scheduling")
    d["si"] = d.label.str.replace("si=", "").astype(float)
    rows = []
    for app, loop in [("vrgame", "loop_eeg_display_ms"), ("dcns", "loop_motion_tracker_ms")]:
        for pl in ["cloud", "edgeward"]:
            for g in [1, 4]:
                x = d[(d.app == app) & (d.placement == pl) & (d.groups == g)].set_index("si")[loop]
                rows.append([app if (pl == "cloud" and g == 1) else "", pl if g == 1 else "", g * 4]
                            + [f(x[si], 1) for si in [10, 1, 0.1]])
    tex("gupta_scheduling", ["Case", "Placement", "End dev.", "10 ms", "1 ms", "0.1 ms"], rows,
        "Control-loop latency (ms) of the original iFogSim case studies with plain iFogSim devices, as a "
        "function of the PowerDatacenter scheduling interval (seed 1).", "tab:gupta-scheduling", "lllrrr")


def concurrency():
    d = load("concurrency")
    d["n"] = nzones(d)
    rows = []
    for n in range(1, 6):
        r = [n]
        for lab in ["default", "exact"]:
            x = d[(d.label == lab) & (d.n == n)]
            r += [f(x.alert_latency_ms.mean()), f(x.dispatch_latency_ms.mean()), f(x.coordination_latency_ms.mean()),
                  f(x.e2e_mean_ms.mean())]
        rows.append(r)
    tex("concurrency", ["Zones", "Alert", "Dispatch", "Sum", "End to end", "Alert", "Dispatch", "Sum", "End to end"],
        rows, "Case study under concurrent zones: TimeKeeper subtotals, their sum, and the end-to-end latency "
              "measured by the lineage tracer (D6), with iFogSim's completion detection (left) and exact "
              "completion events (D3, right); means of 5 runs, ms.", "tab:concurrency")
    print(d.groupby(["label", "n"])[["coordination_latency_ms", "e2e_mean_ms", "trace_esn_stage_ms",
                                     "classifier_max_excess_ms"]].mean().round(3))


def scheduling_interval():
    d = load("scheduling_interval")
    d["n"] = nzones(d)
    d["si"] = d.label.str.replace("si=", "").astype(float)
    rows = []
    for si in sorted(d.si.unique(), reverse=True):
        x = d[d.si == si].set_index("n").dispatch_latency_ms
        rows.append([f"{si:g}"] + [f(x[n]) for n in range(1, 5)])
    tex("scheduling", ["Interval (ms)", "1 zone", "2 zones", "3 zones", "4 zones"], rows,
        "Dispatch latency (ms) of the companion case study with plain iFogSim devices, as a function of the "
        "PowerDatacenter scheduling interval and of the number of simultaneous zones (seed 1).", "tab:scheduling")


def calibration():
    d = load("calibration")
    X = np.vstack([np.ones(len(d)), d.operations]).T
    y = d.median_ms * 10000
    b, *_ = np.linalg.lstsq(X, y, rcond=None)
    pred = X @ b
    r2 = 1 - ((y - pred) ** 2).sum() / ((y - y.mean()) ** 2).sum()
    g = d.groupby("nodes").agg(edges=("edges", "first"), ops=("operations", "first"), med=("median_ms", "median"),
                               lo=("median_ms", "min"), hi=("median_ms", "max"))
    rows = [[n, int(r.edges), f(r.ops, 0), f(r.med * 1000, 2), f(r.lo * 1000, 2) + " to " + f(r.hi * 1000, 2),
             f((b[0] + b[1] * r.ops) / 10, 2)] for n, r in g.iterrows()]
    tex("calibration", ["Nodes", "Roads", "Ops", "Median ($\\mu$s)", "Range of medians ($\\mu$s)", "Model ($\\mu$s)"], rows,
        f"Dijkstra on the road network: median of 5000 timed runs after 50000 warm-up runs, 5 JVMs per size; "
        f"fitted workload $L = {b[0]:.2f} + {b[1]:.4f}\\,(V+E)\\log_2 V$ MI on the 10\\,000-MIPS ESN "
        f"($R^2 = {r2:.2f}$).", "tab:calibration")
    print(f"calibration: a={b[0]:.3f} MI, c={b[1]:.5f} MI/op, R2={r2:.3f}")


def routing_kernel():
    d = load("routing_kernel")
    d["n"] = nzones(d)
    s = d[d.label == "size"]
    rows = []
    for nodes in sorted(s.road_nodes.unique()):
        r = [nodes]
        for accel in ["none", "fpga:10"]:
            for n in [1, 5]:
                x = s[(s.road_nodes == nodes) & (s.accel == accel) & (s.n == n)].iloc[0]
                r += [f(x.router_sojourn_ms * 1000, 1)]
        x5 = s[(s.road_nodes == nodes) & (s.accel == "none") & (s.n == 5)].iloc[0]
        r += [f(x5.e2e_mean_ms, 2)]
        rows.append(r)
    tex("kernel", ["Nodes", "CPU, 1 zone", "CPU, 5 zones", "FPGA, 1 zone", "FPGA, 5 zones", "E2E, CPU, 5 zones"],
        rows, "Routing kernel executed inside the engine (D2): mean sojourn of a request on \\texttt{esn-router} ($\\mu$s), "
              "CPU or FPGA ($p=10$), one or five simultaneous zones, and end-to-end latency (ms).", "tab:kernel")
    fp = d[d.label == "fpga"].copy()
    fp[["p", "fr", "o"]] = fp.accel.str.split(":", expand=True).iloc[:, 1:4].astype(float)
    rows = []
    for p in sorted(fp.p.unique()):
        r = [f"{p:g}"]
        for fr, o in [(1.0, 0.0), (0.9, 0.0), (1.0, 0.02), (0.9, 0.02)]:
            x = fp[(fp.p == p) & (fp.fr == fr) & (fp.o == o)].iloc[0]
            r += [f(x.router_sojourn_ms * 1000, 1)]
        rows.append(r)
    tex("fpga", ["$p$", "$f=1$, $o=0$", "$f=0.9$, $o=0$", "$f=1$, $o=20\\,\\mu$s", "$f=0.9$, $o=20\\,\\mu$s"], rows,
        "Accelerator sensitivity on the 100-node network with five simultaneous zones: mean sojourn of a "
        "request on \\texttt{esn-router} ($\\mu$s). The CPU value is %s\\,$\\mu$s." % f(
            s[(s.road_nodes == 100) & (s.accel == "none") & (s.n == 5)].iloc[0].router_sojourn_ms * 1000, 1),
        "tab:fpga")
    res = d[d.label.str.startswith("resolution")]
    print(res[["label", "n", "accel", "router_sojourn_ms", "router_nominal_ms", "e2e_mean_ms"]].to_string())


def path_cache():
    d = load("path_cache")
    rows = []
    for _, x in d.iterrows():
        lab = x.label
        names = {"cache=0": "no cache", "cache=1": "1 slot", "cache=2": "2 slots", "cache=3": "3 slots",
                 "cache=5": "5 slots", "cache=5:8:500": "5 slots, TTL 500 ms",
                 "outage-ckpt=none": "5 slots, outage, no checkpoint",
                 "outage-ckpt=100": "5 slots, outage, checkpoints"}
        rows.append([names.get(lab, lab),
                     "" if pd.isna(x.cache_hit_ratio) else f(x.cache_hit_ratio, 3), f(x.router_sojourn_ms * 1000, 1),
                     f(x.e2e_mean_ms, 3), int(x.tuples_dropped)])
    tex("cache", ["Configuration", "Hit ratio", "Router sojourn ($\\mu$s)", "E2E (ms)", "Dropped"], rows,
        "Path cache on \\texttt{esn-router} (D4), 100-node network, five zones: capacity (zones), time to live, and an "
        "ESN outage between 500 and 800 ms with and without 100-ms checkpoints.", "tab:cache")
    print(d[["label", "cache_hits", "cache_misses", "checkpoints", "checkpoint_bytes"]])


def faults():
    d = load("faults")
    g = d.groupby(["graph", "label"]).agg(alert=("alert_latency_ms", "mean"), e2e=("e2e_mean_ms", "mean"),
                                         emitted=("trace_emitted", "mean"), delivered=("trace_delivered", "mean"),
                                         dropped=("tuples_dropped", "mean"))
    rows = [[gr, lab.replace("-", " "), f(r.alert), f(r.e2e), int(r.emitted), int(r.delivered), int(r.dropped)]
            for (gr, lab), r in g.iterrows()]
    tex("faults", ["Topology", "Scenario", "Alert (ms)", "E2E (ms)", "Emitted", "Delivered", "Dropped"], rows,
        "Failure of the uplink of zone z1 between 500 and 1500 ms (D5), tree versus mesh with lateral CAD "
        "links (D1); zones z1 and z2 active, exact completion events, means of 5 runs.", "tab:faults")


def battery():
    d = load("battery")
    rows = []
    for _, x in d.iterrows():
        interval = float(x.label.split("=")[1])
        # analytical: emission i at t_i = 50 + P (i + 1) ms (iFogSim's 50-ms start delay),
        # 1 J, 10 mJ per alert, 0.5 W idle; alert i fails when idle + transmit energy >= 1 J
        k = 0
        while 0.5 * (50 + interval * (k + 1)) / 1000 + 0.01 * (k + 1) < 1 - 1e-9:
            k += 1
        exhaust = 50 + interval * (k + 1)
        rows.append([f"{interval:g}", int(x.battery_tx), k, f(x.battery_exhausted_ms, 1), f(exhaust, 1),
                     int(x.trace_delivered)])
    tex("battery", ["Period (ms)", "Alerts sent", "Expected", "Exhausted at (ms)", "Expected (ms)", "Delivered"], rows,
        "Battery of the SAS of z1 (D7): 1 J, 10 mJ per alert, 0.5 W idle; simulated against analytical values.",
        "tab:battery")


def overhead():
    p = os.path.join(RES, "overhead.csv")
    if not os.path.exists(p):
        return
    d = pd.read_csv(p)
    g = d.groupby(["groups", "label"]).wall_clock_ms.median().unstack()
    n = d.groupby("groups").device_count.first()
    rows = []
    for gr in g.index:
        base = g.loc[gr, "vanilla"]
        rows.append([n[gr], f(base, 0)] + [f(g.loc[gr, c], 0) + f" ({100 * (g.loc[gr, c] / base - 1):+.0f}\\%)"
                                           for c in ["hetfs", "hetfs-graph", "hetfs-full"]])
    tex("overhead", ["Devices", "FogDevice", "HetFS, no component", "HetFS + D1", "HetFS + D1, D3, D6"], rows,
        "Wall-clock time of the EEG game (edge-ward, 2 s simulated) in ms, median of 5 runs, and overhead "
        "relative to plain iFogSim devices.", "tab:overhead")
    print(g.round(0))


if __name__ == "__main__":
    for fn in [companion_reproduction, gupta, gupta_scheduling, concurrency, scheduling_interval, calibration, routing_kernel,
               path_cache, faults, battery, overhead]:
        try:
            fn()
        except FileNotFoundError as e:
            print("skipped", fn.__name__, e)

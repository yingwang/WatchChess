"""Exercise the idle watchresult protocol on a host engine or an explicit adb target.

Chase fixtures come from Fairy-Stockfish 2e591089 test.py (AXF rules).
Does not install or launch the app. Writes its report under the project build/.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("engine")
parser.add_argument("--serial")
args = parser.parse_args()
cases = []

def add(name, position, moves, expected):
    cases.append((name, position + (" moves " + " ".join(moves) if moves else ""), expected))

def upstream(name, fen, moves, expected):
    # Upstream Python API uses ranks 1..10; WatchChess uses cyclone ranks 0..9.
    moves = [re.sub(r"\d+", lambda m: str(int(m[0]) - 1), move) for move in moves]
    add(name, "fen " + fen, moves, expected)

add("initial", "startpos", [], "ongoing")
add("one-cycle-not-terminal", "startpos", ["b0c2", "b9c7", "c2b0", "c7b9"], "ongoing")
add("neutral-threefold", "startpos", ["b0c2", "b9c7", "c2b0", "c7b9"] * 2, "draw")
add("undo-restores-ongoing", "startpos", ["b0c2", "b9c7", "c2b0", "c7b9"], "ongoing")
add("red-perpetual-check-loses", "fen 5k3/4R4/9/9/9/9/9/9/9/3K5 w - - 0 1",
    ["e8f8", "f9e9", "f8e8", "e9f9"] * 2, "black")
add("black-perpetual-check-loses", "fen 3k5/9/9/9/9/9/9/9/4r4/5K3 b - - 0 1",
    ["e1f1", "f0e0", "f1e1", "e0f0"] * 2, "red")
chase_fen = "2bakabnr/9/r1n1c4/2p1p1p1p/PP7/9/4P1P1P/2C3NC1/9/1NBAKAB1R w - - 0 1"
chase_moves = "c3a3 a8b8 a3b3 b8a8 b3a3 a8b8 a3b3 b8a8 b3a3".split()
upstream("cannon-chase-loses", chase_fen, chase_moves, "black")
upstream("chase-side-to-move-still-loses", chase_fen, chase_moves + "a8b8 a3b3 b8a8".split(), "black")
upstream("soldier-chase-exempt", "2bakabr1/9/9/r1p1p1p2/p7R/P8/9/9/9/1C1AKA3 w - - 0 1",
    "a5a6 a7b7 a6b6 b7a7 b6a6 a7b7 a6b6 b7a7 b6a6".split(), "draw")
upstream("mutual-check-draw", "9/4c4/3k5/3r5/9/9/4C4/9/4K4/3R5 w - - 0 1",
    "e4d4 d7e7 d4e4 e7d7".split() * 2, "draw")
add("red-stalemate-loses", "fen 3k5/9/9/9/9/9/9/9/3r1r3/4K4 w - - 0 1", [], "black")
add("black-stalemate-loses", "fen 4k4/3R1R3/9/9/9/9/9/9/9/3K5 b - - 0 1", [], "red")

commands = ["ucicyclone", "uci", "setoption name Use NNUE value false",
            "setoption name UCI_Variant value xiangqi", "setoption name Threads value 1",
            "setoption name Hash value 4", "isready"]
for _, position, _ in cases:
    commands += ["position " + position, "watchresult"]
commands += ["quit"]
command = (["adb", "-s", args.serial, "shell",
            "SCUDO_OPTIONS=release_to_os_interval_ms=0:may_return_null=true", args.engine]
           if args.serial else [args.engine])
result = subprocess.run(command, input="\n".join(commands) + "\n", text=True,
                        capture_output=True, timeout=60, check=True)
actual = [line.strip().split()[-1] for line in result.stdout.splitlines()
          if line.startswith("watchresult ")]
report = [{"name": name, "expected": expected,
           "actual": actual[i] if i < len(actual) else None}
          for i, (name, _, expected) in enumerate(cases)]
root = Path(__file__).resolve().parent.parent / "build" / "adjudication"
root.mkdir(parents=True, exist_ok=True)
(root / "report.json").write_text(json.dumps(report, indent=2) + "\n")
(root / "engine.log").write_text(result.stdout + result.stderr)
for case in report:
    print(("PASS" if case["actual"] == case["expected"] else "FAIL"), case)
assert len(actual) == len(cases) and all(c["actual"] == c["expected"] for c in report)

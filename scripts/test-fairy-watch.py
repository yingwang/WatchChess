"""Bounded standalone-engine test; never installs or launches the WatchChess app."""
import json
import queue
import re
import subprocess
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build' / 'fairy-watch-test'
OUT.mkdir(parents=True, exist_ok=True)
serial = subprocess.check_output(['bash', str(ROOT / 'scripts/watch-adb.sh')], text=True, timeout=50).strip()
adb = ['adb', '-s', serial]
model = subprocess.check_output(adb + ['shell', 'getprop', 'ro.product.model'], text=True, timeout=10).strip()
assert model == 'Pixel Watch 4', f'Wrong target: {model}'

def memory(pid=None):
    files = '/proc/meminfo' if pid is None else f'/proc/{pid}/status /proc/meminfo'
    text = subprocess.check_output(adb + ['shell', f'cat {files}'], text=True, timeout=10)
    return {k: int(v) for k, v in re.findall(r'^(VmRSS|VmHWM|MemAvailable):\s+(\d+) kB', text, re.M)}

assert memory()['MemAvailable'] >= 200_000, 'Not enough spare memory for a safe test'
remote = '/data/local/tmp/watchchess-fsf-final'
subprocess.run(adb + ['push', str(ROOT / 'app/src/main/jniLibs/armeabi-v7a/libfairystockfish.so'), remote], check=True, timeout=30)
subprocess.run(adb + ['shell', f'chmod 700 {remote}'], check=True, timeout=10)
p = subprocess.Popen(adb + ['shell', '-T',
    f'echo ENGINE_PID=$$; exec env SCUDO_OPTIONS=release_to_os_interval_ms=0:may_return_null=true {remote}'],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
lines = queue.Queue()
transcript = []
def pump():
    for line in p.stdout:
        transcript.append(line)
        lines.put(line.strip())
    lines.put(None)
threading.Thread(target=pump, daemon=True).start()
def send(command):
    p.stdin.write(command + '\n')
    p.stdin.flush()
def until(predicate, seconds=20):
    end = time.monotonic() + seconds
    result = []
    while time.monotonic() < end:
        line = lines.get(timeout=max(.01, end-time.monotonic()))
        assert line is not None, 'Unexpected engine EOF'
        result.append(line)
        if predicate(line): return result
    raise TimeoutError('Engine timeout')

report = {'model': model, 'plies': [], 'status': 'running'}
try:
    pid = int(until(lambda s: s.startswith('ENGINE_PID='))[0].split('=')[1])
    started = time.monotonic()
    send('ucicyclone'); send('uci')
    options = until(lambda s: s == 'uciok')
    report['handshake_seconds'] = round(time.monotonic() - started, 3)
    if any('option name NumaPolicy ' in line for line in options):
        send('setoption name NumaPolicy value none')
    for option in ['Use NNUE value false', 'UCI_Variant value xiangqi', 'Threads value 1', 'Hash value 4']:
        send('setoption name ' + option)
    send('isready'); until(lambda s: s == 'readyok')
    send('position startpos'); send('go perft 1')
    perft = until(lambda s: s.startswith('Nodes searched:'))
    legal = {s.split(':')[0] for s in perft if re.match(r'^[a-i][0-9][a-i][0-9]:', s)}
    assert len(legal) == 44 and 'b0c2' in legal, 'Wrong xiangqi coordinates or rules'
    report['opening_moves'] = sorted(legal)
    send('position startpos moves b0c2'); send('d'); send('isready')
    display = until(lambda s: s == 'readyok')
    report['horse_move_fen'] = next(s for s in display if s.startswith('Fen:'))
    assert '1CN4C1/9/R1BAKABNR b' in report['horse_move_fen']
    history = []
    for ply in range(24):
        mem = memory(pid)
        assert mem.get('VmRSS', 0) < 100_000 and mem['MemAvailable'] > 180_000, f'Memory safety stop: {mem}'
        send('position startpos' + (' moves ' + ' '.join(history) if history else ''))
        send('go perft 1')
        perft = until(lambda s: s.startswith('Nodes searched:'))
        legal = {s.split(':')[0] for s in perft if re.match(r'^[a-i][0-9][a-i][0-9]:', s)}
        send('go nodes 500000 movetime ' + ('3000' if ply == 0 else '600'))
        result = until(lambda s: s.startswith('bestmove '), seconds=12)
        move = result[-1].split()[1]
        assert move in legal, f'Illegal engine output: {move}'
        info = next((s for s in reversed(result) if s.startswith('info depth ')), '')
        row = {'ply': ply + 1, 'move': move, 'info': info, **memory(pid)}
        report['plies'].append(row)
        print(json.dumps(row), flush=True)
        history.append(move)
    report['status'] = 'passed'
finally:
    try:
        send('quit')
        p.wait(timeout=5)
    except Exception:
        p.kill()
    (OUT / 'report.json').write_text(json.dumps(report, indent=2))
    (OUT / 'engine.log').write_text(''.join(transcript))

#!/usr/bin/env python3
"""
Generates the launcher's interface sounds (original, synthesized; no samples are used):

  ui_nav_h.wav   focus moved left/right  (B5, soft glassy tick + short airy reverb)
  ui_nav_v.wav   focus moved up/down     (A5, same timbre, a touch lower)
  ui_select.wav  OK / open               (E5 -> B5 two-note "pop", longer tail)

Usage (needs numpy):
    python3 tools/make_sounds.py                 # writes into app/src/main/res/raw/
    python3 tools/make_sounds.py some/other/dir

Tweak the frequencies in tick(...) / tone(...) calls, the decay times, or the reverb
parameters, run it again, then rebuild the app.
"""
import numpy as np, wave, sys, os
SR = 44100
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")
os.makedirs(out, exist_ok=True)
rng = np.random.default_rng(7)

def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.clip(t / attack, 0, 1)
    return a * np.exp(-t / decay)

def tone(freqs, dur, attack, decay, amps=None):
    n = int(SR * dur); t = np.arange(n) / SR
    amps = amps or [1.0] * len(freqs)
    s = sum(a * np.sin(2 * np.pi * f * t) for f, a in zip(freqs, amps))
    return s * env(n, attack, decay)

def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR); y = np.zeros_like(x); p = 0.0
    for i, v in enumerate(x):
        p = (1 - a) * v + a * p; y[i] = p
    return y

def reverb(x, tail=0.55, decay=0.22, mix=0.35, bright=4500):
    n = int(SR * tail); t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t / decay)
    ir = lowpass(ir, bright); ir[: int(SR * 0.012)] *= np.linspace(0, 1, int(SR * 0.012))  # pre-delay feel
    ir /= np.sqrt(np.sum(ir ** 2))
    wet = np.convolve(x, ir)[: len(x) + n]
    wet = np.pad(wet, (0, len(x) + n - len(wet)))
    dry = np.concatenate([x, np.zeros(n)])
    return (1 - mix) * dry + mix * wet

def finish(x, peak=0.55):
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    fade = int(SR * 0.03); x[-fade:] *= np.linspace(1, 0, fade)
    return x

def save(name, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with wave.open(os.path.join(out, name), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    print(name, f"{len(x)/SR:.2f}s")

# Focus move: a soft glassy tick (high sine + octave partial), very short body, airy tail.
def tick(base):
    # Warmer: weaker overtones, a soft sub-octave for body, slightly longer decay.
    body = tone([base, base * 2.01, base * 0.5], 0.11, 0.002, 0.024, [1.0, 0.12, 0.28])
    click = lowpass(rng.standard_normal(int(SR * 0.006)), 2500) * 0.08
    click = np.concatenate([click * np.linspace(1, 0, len(click)), np.zeros(len(body) - len(click))])
    return finish(lowpass(reverb(body + click, tail=0.45, decay=0.16, mix=0.32, bright=2600), 3200), peak=0.45)

save("ui_nav_h.wav", tick(988))    # B5 — left / right (was G6: too sharp)
save("ui_nav_v.wav", tick(880))    # A5 — up / down (a touch lower)

# Select: two quick rising partials ("pop-ding"), rounder and a bit longer.
a = tone([659, 1318, 330], 0.2, 0.003, 0.06, [1.0, 0.1, 0.25])   # E5
b = tone([988, 1976, 494], 0.24, 0.003, 0.08, [1.0, 0.08, 0.2])   # B5
sel = np.zeros(int(SR * 0.3)); sel[: len(a)] += a; sel[int(SR * 0.035): int(SR * 0.035) + len(b)] += b * 0.8
save("ui_select.wav", finish(lowpass(reverb(sel, tail=0.7, decay=0.25, mix=0.38, bright=2600), 3200), peak=0.5))

"""Generate short original UI cues; no external audio or runtime dependencies."""
import math
import pathlib
import struct
import wave

out = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/res/raw'
out.mkdir(exist_ok=True)
for name, frequency in [('soft', 650), ('drop', 880), ('warm', 440)]:
    rate, duration = 44100, 0.075
    samples = []
    for i in range(round(rate * duration)):
        t = i / rate
        envelope = math.sin(math.pi * t / duration) ** 2 * math.exp(-t * 20)
        tone = math.sin(2 * math.pi * frequency * t)
        samples.append(round(32767 * 0.18 * envelope * tone))
    with wave.open(str(out / f'nav_{name}.wav'), 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(rate)
        wav.writeframes(struct.pack('<' + 'h' * len(samples), *samples))
print('Generated 3 original 75ms PCM cues')

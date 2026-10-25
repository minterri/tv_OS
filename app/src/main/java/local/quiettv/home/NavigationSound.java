package local.quiettv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Short, preloaded UI cues. Never synthesize or read files on the focus path. */
final class NavigationSound {
    private final SharedPreferences prefs;
    private final SoundPool pool;
    private final int[] samples = new int[3];
    private final Set<Integer> ready = Collections.synchronizedSet(new HashSet<>());
    private long lastPlayed;

    NavigationSound(Context context, SharedPreferences preferences) {
        prefs = preferences;
        pool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
        pool.setOnLoadCompleteListener((soundPool, id, status) -> { if (status == 0) ready.add(id); });
        samples[0] = pool.load(context, R.raw.nav_soft, 1);
        samples[1] = pool.load(context, R.raw.nav_drop, 1);
        samples[2] = pool.load(context, R.raw.nav_warm, 1);
    }
    void play() {
        if (!prefs.getBoolean("sound-enabled", true)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastPlayed < 90) return;
        int tone = Math.max(0, Math.min(2, prefs.getInt("sound-tone", 0)));
        int sample = samples[tone];
        if (!ready.contains(sample)) return;
        float volume = Math.max(0, Math.min(100, prefs.getInt("sound-volume", 20))) / 100f;
        if (volume == 0) return;
        lastPlayed = now;
        pool.play(sample, volume, volume, 1, 0, 1f);
    }
    void release() { pool.release(); ready.clear(); }
}

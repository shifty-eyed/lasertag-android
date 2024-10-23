package net.lasertag;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

public class SoundManager {

    private SoundPool soundPool;

    private int gunShotSound;
    private int gotHitSound;
    private int youHitSomeoneSound;
    private int noBulletsSound;
    private int reloadSound;

    public SoundManager(Context context) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(audioAttributes)
                .build();

        gunShotSound = soundPool.load(context, R.raw.fire, 1);
        gotHitSound = soundPool.load(context, R.raw.hitby, 1);
        youHitSomeoneSound = soundPool.load(context, R.raw.hit, 1);
        noBulletsSound = soundPool.load(context, R.raw.noammo, 1);
        reloadSound = soundPool.load(context, R.raw.reload, 1);
    }

    public void playGunShot() {
        soundPool.play(gunShotSound, 1, 1, 0, 0, 1);
    }

    public void playGotHit() {
        soundPool.play(gotHitSound, 1, 1, 0, 0, 1);
    }

    public void playYouHitSomeone() {
        soundPool.play(youHitSomeoneSound, 1, 1, 0, 0, 1);
    }

    public void playNoBullets() {
        soundPool.play(noBulletsSound, 1, 1, 0, 0, 1);
    }

    public void playReload() {
        soundPool.play(reloadSound, 1, 1, 0, 0, 1);
    }

    public void release() {
        soundPool.release();
    }
}

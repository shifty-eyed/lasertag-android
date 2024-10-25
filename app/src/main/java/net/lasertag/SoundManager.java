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
    private int youKilledSound;
    private int respawnSound;

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
        youKilledSound = soundPool.load(context, R.raw.death, 1);
        respawnSound = soundPool.load(context, R.raw.respawn, 1);

    }

    public void release() {
        soundPool.release();
    }

    private void play(int soundId) {
        soundPool.play(soundId, 1, 1, 0, 0, 1);
    }

    public void playGunShot() {
        play(gunShotSound);
    }

    public void playGotHit() {
        play(gotHitSound);
    }

    public void playYouHitSomeone() {
        play(youHitSomeoneSound);
    }

    public void playNoBullets() {
        play(noBulletsSound);
    }

    public void playReload() {
        play(reloadSound);
    }

    public void playYouKilled() {
        play(youKilledSound);
    }

    public void playRespawn() {
        play(respawnSound);
    }

    public void playGameOver() {
    }

    public void playGameStart() {
    }

    public void playYouScored() {
    }
}

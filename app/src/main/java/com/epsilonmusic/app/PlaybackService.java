package com.epsilonmusic.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.lang.ref.WeakReference;

public class PlaybackService extends Service {

    private static final String CHANNEL_ID = "epsilon_music_playback";
    private static final int NOTIFICATION_ID = 4101;

    private static WeakReference<WebView> webViewRef = new WeakReference<>(null);
    private static PlaybackService instance;

    private MediaSessionCompat mediaSession;
    private String title = "Epsilon Music";
    private String artist = "";
    private String album = "";
    private String artwork = "";
    private long durationMs = 0;
    private long positionMs = 0;
    private boolean playing = false;

    public static void setWebView(WebView view) {
        webViewRef = new WeakReference<>(view);
    }

    public static void updateMetadata(
            Context context,
            String title,
            String artist,
            String album,
            String artwork,
            double duration,
            double position,
            boolean playing
    ) {
        Intent i = new Intent(context, PlaybackService.class)
                .setAction("UPDATE")
                .putExtra("title", title)
                .putExtra("artist", artist)
                .putExtra("album", album)
                .putExtra("artwork", artwork)
                .putExtra("duration", duration)
                .putExtra("position", position)
                .putExtra("playing", playing);

        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        else context.startService(i);
    }

    public static void stopPlayback(Context context) {
        Intent i = new Intent(context, PlaybackService.class).setAction("STOP_PLAYBACK");
        context.startService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createChannel();

        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        mediaSession = new MediaSessionCompat(this, "EpsilonMusic");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                callWeb("play");
            }

            @Override
            public void onPause() {
                callWeb("pause");
            }

            @Override
            public void onSkipToNext() {
                callWeb("next");
            }

            @Override
            public void onSkipToPrevious() {
                callWeb("previous");
            }

            @Override
            public void onSeekTo(long pos) {
                callWeb("seek", pos / 1000.0);
            }

            @Override
            public void onStop() {
                callWeb("stop");
                playing = false;
                updatePlaybackState();
                stopForeground(false);
            }
        });
        mediaSession.setActive(true);
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        startForeground(NOTIFICATION_ID, buildNotification(contentIntent));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        String action = intent.getAction();

        if ("UPDATE".equals(action)) {
            String newTitle = intent.getStringExtra("title");
            String newArtist = intent.getStringExtra("artist");
            String newAlbum = intent.getStringExtra("album");
            String newArtwork = intent.getStringExtra("artwork");
            boolean newPlaying = intent.getBooleanExtra("playing", false);

            boolean visualChanged =
                    !java.util.Objects.equals(title, newTitle) ||
                    !java.util.Objects.equals(artist, newArtist) ||
                    !java.util.Objects.equals(album, newAlbum) ||
                    !java.util.Objects.equals(artwork, newArtwork) ||
                    playing != newPlaying;

            title = newTitle;
            artist = newArtist;
            album = newAlbum;
            artwork = newArtwork;
            durationMs = (long) (intent.getDoubleExtra("duration", 0) * 1000);
            positionMs = (long) (intent.getDoubleExtra("position", 0) * 1000);
            playing = newPlaying;

            updateMetadata();
            updatePlaybackState();
            if (visualChanged) refreshNotification();

        } else if ("STOP_PLAYBACK".equals(action)) {
            callWeb("stop");
            playing = false;
            updatePlaybackState();
            stopForeground(false);
            refreshNotification();

        } else if ("PLAY".equals(action)) {
            callWeb("play");

        } else if ("PAUSE".equals(action)) {
            callWeb("pause");

        } else if ("NEXT".equals(action)) {
            callWeb("next");

        } else if ("PREVIOUS".equals(action)) {
            callWeb("previous");
        }

        return START_STICKY;
    }


    private void refreshNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 1, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0)
        );
        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(contentIntent));
    }

    private void callWeb(String action) {
        callWeb(action, null);
    }

    private void callWeb(String action, Double value) {
        WebView w = webViewRef.get();
        if (w == null) return;

        String js;
        if ("seek".equals(action)) {
            js = "window.__epsilonNativeMediaCommand && " +
                    "window.__epsilonNativeMediaCommand('seek'," + value + ");";
        } else {
            js = "window.__epsilonNativeMediaCommand && " +
                    "window.__epsilonNativeMediaCommand('" + action + "');";
        }
        w.post(() -> w.evaluateJavascript(js, null));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Epsilon Music Playback",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Epsilon Music media controls");
            channel.setShowBadge(false);
            NotificationManager nm =
                    (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

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
import android.webkit.WebView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class PlaybackService extends Service {

    private static final String CHANNEL_ID = "epsilon_music_playback";
    private static final int NOTIFICATION_ID = 4101;

    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_STOP = "STOP_PLAYBACK";
    private static final String ACTION_PLAY = "PLAY";
    private static final String ACTION_PAUSE = "PAUSE";
    private static final String ACTION_NEXT = "NEXT";
    private static final String ACTION_PREVIOUS = "PREVIOUS";

    private static WeakReference<WebView> webViewRef =
            new WeakReference<>(null);

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

    /**
     * Called by MainActivity/WebView JavaScript bridge whenever
     * the website's player state changes.
     */
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
        Intent intent = new Intent(context, PlaybackService.class)
                .setAction(ACTION_UPDATE)
                .putExtra("title", title)
                .putExtra("artist", artist)
                .putExtra("album", album)
                .putExtra("artwork", artwork)
                .putExtra("duration", duration)
                .putExtra("position", position)
                .putExtra("playing", playing);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopPlayback(Context context) {
        Intent intent = new Intent(context, PlaybackService.class)
                .setAction(ACTION_STOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        createChannel();

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                100,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23
                                ? PendingIntent.FLAG_IMMUTABLE
                                : 0)
        );

        mediaSession = new MediaSessionCompat(
                this,
                "EpsilonMusic"
        );

        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        );

        mediaSession.setCallback(new MediaSessionCompat.Callback() {

            @Override
            public void onPlay() {
                playing = true;
                callWeb("play");
                updatePlaybackState();
                refreshNotification();
            }

            @Override
            public void onPause() {
                playing = false;
                callWeb("pause");
                updatePlaybackState();
                refreshNotification();
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
                positionMs = pos;
                callWeb("seek", pos / 1000.0);
                updatePlaybackState();
            }

            @Override
            public void onStop() {
                callWeb("stop");

                playing = false;
                positionMs = 0;

                updatePlaybackState();
                refreshNotification();
            }
        });

        mediaSession.setActive(true);

        // Android requires a foreground notification for reliable
        // background media playback.
        startForeground(
                NOTIFICATION_ID,
                buildNotification(contentIntent)
        );

        updatePlaybackState();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId
    ) {

        if (intent == null) {
            return START_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_UPDATE.equals(action)) {

            String newTitle =
                    intent.getStringExtra("title");

            String newArtist =
                    intent.getStringExtra("artist");

            String newAlbum =
                    intent.getStringExtra("album");

            String newArtwork =
                    intent.getStringExtra("artwork");

            boolean newPlaying =
                    intent.getBooleanExtra("playing", false);

            boolean visualChanged =
                    !Objects.equals(title, newTitle) ||
                    !Objects.equals(artist, newArtist) ||
                    !Objects.equals(album, newAlbum) ||
                    !Objects.equals(artwork, newArtwork) ||
                    playing != newPlaying;

            title = newTitle != null
                    ? newTitle
                    : "Epsilon Music";

            artist = newArtist != null
                    ? newArtist
                    : "";

            album = newAlbum != null
                    ? newAlbum
                    : "";

            artwork = newArtwork != null
                    ? newArtwork
                    : "";

            durationMs =
                    (long) (
                            intent.getDoubleExtra(
                                    "duration",
                                    0
                            ) * 1000
                    );

            positionMs =
                    (long) (
                            intent.getDoubleExtra(
                                    "position",
                                    0
                            ) * 1000
                    );

            playing = newPlaying;

            updateMetadata();

            updatePlaybackState();

            if (visualChanged) {
                refreshNotification();
            }

        } else if (ACTION_STOP.equals(action)) {

            callWeb("stop");

            playing = false;
            positionMs = 0;

            updatePlaybackState();
            refreshNotification();

        } else if (ACTION_PLAY.equals(action)) {

            playing = true;
            callWeb("play");
            updatePlaybackState();
            refreshNotification();

        } else if (ACTION_PAUSE.equals(action)) {

            playing = false;
            callWeb("pause");
            updatePlaybackState();
            refreshNotification();

        } else if (ACTION_NEXT.equals(action)) {

            callWeb("next");

        } else if (ACTION_PREVIOUS.equals(action)) {

            callWeb("previous");
        }

        return START_STICKY;
    }

    /**
     * Updates Android's MediaSession metadata.
     *
     * This is what allows Android to know which song is playing.
     */
    private void updateMetadata() {

        if (mediaSession == null) {
            return;
        }

        MediaMetadataCompat metadata =
                new MediaMetadataCompat.Builder()
                        .putString(
                                MediaMetadataCompat.METADATA_KEY_TITLE,
                                title
                        )
                        .putString(
                                MediaMetadataCompat.METADATA_KEY_ARTIST,
                                artist
                        )
                        .putString(
                                MediaMetadataCompat.METADATA_KEY_ALBUM,
                                album
                        )
                        .putLong(
                                MediaMetadataCompat.METADATA_KEY_DURATION,
                                durationMs
                        )
                        .build();

        mediaSession.setMetadata(metadata);
    }

    /**
     * Updates Android's playback state.
     *
     * This controls the play/pause/seek state shown by the
     * lock screen, notification and Bluetooth/headset controls.
     */
    private void updatePlaybackState() {

        if (mediaSession == null) {
            return;
        }

        long actions =
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_PLAY_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO |
                PlaybackStateCompat.ACTION_STOP;

        int state = playing
                ? PlaybackStateCompat.STATE_PLAYING
                : PlaybackStateCompat.STATE_PAUSED;

        PlaybackStateCompat playbackState =
                new PlaybackStateCompat.Builder()
                        .setActions(actions)
                        .setState(
                                state,
                                positionMs,
                                playing ? 1.0f : 0.0f
                        )
                        .build();

        mediaSession.setPlaybackState(playbackState);
    }

    /**
     * Builds the Android media notification.
     */
    private Notification buildNotification(
            PendingIntent contentIntent
    ) {

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(
                        this,
                        CHANNEL_ID
                )
                        .setSmallIcon(
                                android.R.drawable.ic_media_play
                        )
                        .setContentTitle(
                                title == null || title.isEmpty()
                                        ? "Epsilon Music"
                                        : title
                        )
                        .setContentText(
                                artist == null
                                        ? ""
                                        : artist
                        )
                        .setContentIntent(contentIntent)
                        .setVisibility(
                                NotificationCompat.VISIBILITY_PUBLIC
                        )
                        .setOnlyAlertOnce(true)
                        .setOngoing(playing)
                        .setShowWhen(false)
                        .setPriority(
                                NotificationCompat.PRIORITY_LOW
                        );

        MediaStyle mediaStyle =
                new MediaStyle()
                        .setMediaSession(
                                mediaSession.getSessionToken()
                        )
                        .setShowActionsInCompactView(
                                0,
                                1,
                                2
                        );

        builder.setStyle(mediaStyle);

        builder.addAction(
                new NotificationCompat.Action(
                        android.R.drawable.ic_media_previous,
                        "Previous",
                        createActionIntent(ACTION_PREVIOUS)
                )
        );

        if (playing) {
            builder.addAction(
                    new NotificationCompat.Action(
                            android.R.drawable.ic_media_pause,
                            "Pause",
                            createActionIntent(ACTION_PAUSE)
                    )
            );
        } else {
            builder.addAction(
                    new NotificationCompat.Action(
                            android.R.drawable.ic_media_play,
                            "Play",
                            createActionIntent(ACTION_PLAY)
                    )
            );
        }

        builder.addAction(
                new NotificationCompat.Action(
                        android.R.drawable.ic_media_next,
                        "Next",
                        createActionIntent(ACTION_NEXT)
                )
        );

        return builder.build();
    }

    private PendingIntent createActionIntent(
            String action
    ) {

        Intent intent =
                new Intent(this, PlaybackService.class)
                        .setAction(action);

        return PendingIntent.getService(
                this,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23
                                ? PendingIntent.FLAG_IMMUTABLE
                                : 0)
        );
    }

    /**
     * Refreshes the notification after the song/state changes.
     */
    private void refreshNotification() {

        Intent openIntent =
                new Intent(this, MainActivity.class);

        openIntent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        PendingIntent contentIntent =
                PendingIntent.getActivity(
                        this,
                        100,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                (Build.VERSION.SDK_INT >= 23
                                        ? PendingIntent.FLAG_IMMUTABLE
                                        : 0)
                );

        NotificationManager manager =
                (NotificationManager)
                        getSystemService(
                                NOTIFICATION_SERVICE
                        );

        manager.notify(
                NOTIFICATION_ID,
                buildNotification(contentIntent)
        );
    }

    /**
     * Sends commands from Android's media controls to the website.
     */
    private void callWeb(String action) {
        callWeb(action, null);
    }

    private void callWeb(
            String action,
            Double value
    ) {

        WebView webView =
                webViewRef.get();

        if (webView == null) {
            return;
        }

        String js;

        if ("seek".equals(action)) {

            js =
                    "window.__epsilonNativeMediaCommand && " +
                    "window.__epsilonNativeMediaCommand('seek'," +
                    value +
                    ");";

        } else {

            js =
                    "window.__epsilonNativeMediaCommand && " +
                    "window.__epsilonNativeMediaCommand('" +
                    action +
                    "');";
        }

        webView.post(() ->
                webView.evaluateJavascript(
                        js,
                        null
                )
        );
    }

    private void createChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Epsilon Music Playback",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Epsilon Music media controls"
            );

            channel.setShowBadge(false);

            NotificationManager manager =
                    (NotificationManager)
                            getSystemService(
                                    NOTIFICATION_SERVICE
                            );

            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {

        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
            mediaSession = null;
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

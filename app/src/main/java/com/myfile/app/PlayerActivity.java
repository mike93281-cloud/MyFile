package com.myfile.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

/** Reproductor integrado de vídeo y música (MediaPlayer del sistema, sin librerías). */
public class PlayerActivity extends Activity {

    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_AUDIO = "audio";
    private static final String STATE_POS = "pos";

    public static Intent intent(Context ctx, Uri uri, String name, boolean audio) {
        return new Intent(ctx, PlayerActivity.class).setData(uri)
                .putExtra(EXTRA_NAME, name).putExtra(EXTRA_AUDIO, audio);
    }

    private VideoView video;
    private MediaController controller;
    private int resumeAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Uri uri = getIntent().getData();
        if (uri == null) { finish(); return; }
        boolean audio = getIntent().getBooleanExtra(EXTRA_AUDIO, false);
        if (savedInstanceState != null) resumeAt = savedInstanceState.getInt(STATE_POS);

        ((TextView) findViewById(R.id.tv_title)).setText(getIntent().getStringExtra(EXTRA_NAME));
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.audio_art).setVisibility(audio ? View.VISIBLE : View.GONE);

        video = findViewById(R.id.video);
        controller = new MediaController(this);
        controller.setAnchorView(findViewById(R.id.player_root));
        video.setMediaController(controller);
        video.setOnPreparedListener(mp -> {
            if (resumeAt > 0) video.seekTo(resumeAt);
            video.start();
            controller.show(audio ? 0 : 3000);
        });
        video.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(this, R.string.cannot_play, Toast.LENGTH_LONG).show();
            finish();
            return true;
        });
        video.setVideoURI(uri);
        findViewById(R.id.player_root).setOnClickListener(v -> controller.show());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (video != null) {
            resumeAt = video.getCurrentPosition();
            video.pause();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_POS, resumeAt);
    }

    @Override
    protected void onDestroy() {
        if (video != null) video.stopPlayback();
        super.onDestroy();
    }
}

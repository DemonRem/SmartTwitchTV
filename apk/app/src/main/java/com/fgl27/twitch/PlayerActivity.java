package com.fgl27.twitch;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Util;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

public class PlayerActivity extends Activity {
    public final String TAG = PlayerActivity.class.getName();

    //public static final String PageUrl = "file:///android_asset/index.html";
    public final String PageUrl = "https://fgl27.github.io/SmartTwitchTV/release/index.min.html";

    public final int PlayerAccount = 4;
    public final int PlayerAccountPlus = PlayerAccount + 1;

    public final int[] keys = {//same order as Main_initClickDoc /smartTwitchTV/app/specific/Main.js
            KeyEvent.KEYCODE_DPAD_UP,//0
            KeyEvent.KEYCODE_DPAD_DOWN,//1
            KeyEvent.KEYCODE_DPAD_LEFT,//2
            KeyEvent.KEYCODE_DPAD_RIGHT,//3
            KeyEvent.KEYCODE_ENTER,//4
            KeyEvent.KEYCODE_F2,//KEYCODE_F2 is mapped as back key //5
            KeyEvent.KEYCODE_PAGE_UP,//6
            KeyEvent.KEYCODE_PAGE_DOWN,//7
            KeyEvent.KEYCODE_3//8
    };

    public final int[] keysAction = {
            KeyEvent.ACTION_DOWN,//0
            KeyEvent.ACTION_UP//1
    };

    public final int[] idsurface = {
            R.id.player_view,//0
            R.id.player_view2,//1
            R.id.player_view3,//2
            R.id.player_view4,//3
            R.id.player_view_e//4
    };

    public final int[] idtexture = {
            R.id.player_view_texture_view,//0
            R.id.player_view2_texture_view,//1
            R.id.player_view3_texture_view,//2
            R.id.player_view4_texture_view,//3
            R.id.player_view_e_texture_view//4
    };

    public int[] BUFFER_SIZE = {4000, 4000, 4000, 4000};//Default, live, vod, clips
    public String[] BLACKLISTEDCODECS = null;
    public PlayerView[] PlayerView = new PlayerView[PlayerAccountPlus];
    public SimpleExoPlayer[] player = new SimpleExoPlayer[PlayerAccountPlus];
    public DefaultRenderersFactory renderersFactory;
    public DefaultTrackSelector[] trackSelector = new DefaultTrackSelector[PlayerAccountPlus];
    public DefaultTrackSelector.Parameters trackSelectorParameters;
    public DefaultTrackSelector.Parameters trackSelectorParametersPP;
    public DefaultTrackSelector.Parameters trackSelectorParametersExtraSmall;
    public TrackGroupArray lastSeenTrackGroupArray;
    public int mainPlayerBandwidth = Integer.MAX_VALUE;
    public int PP_PlayerBandwidth = 3000000;
    public final int ExtraSmallPlayerBandwidth = 4000000;
    public long mResumePosition;
    public int mWho_Called = 1;
    public MediaSource[] mediaSources = new MediaSource[PlayerAccountPlus];
    public String userAgent;
    public String PreviewsResult;
    public WebView mWebView;
    public boolean PicturePicture;
    public boolean deviceIsTV;
    public boolean MultiStreamEnable;
    public boolean isFullScreen = true;
    public int mainPlayer = 0;
    public int MultiMainPlayer = 0;
    public int PicturePicturePosition = 0;
    public int PicturePictureSize = 1;//sizes are 0 , 1 , 2
    public int AudioSource = 1;
    public int AudioMulti = 0;//window 0
    public Handler MainThreadHandler;
    public Handler CurrentPositionHandler;
    public Handler ExtraPlayerHandler;
    public String[][] ExtraPlayerHandlerResult = new String[10][100];
    public HandlerThread ExtraPlayerHandlerThread;
    public HandlerThread SaveBackupJsonThread;
    public HandlerThread PreviewsThread;
    public Handler SaveBackupJsonHandler;
    public Handler PreviewsHandler;
    public HandlerThread RuntimeThread;
    public Handler RuntimeHandler;
    public Runtime runtime;
    public float PingValue = 0f;
    public float PingValueAVG = 0f;
    public long PingCounter = 0L;
    public long PlayerCurrentPosition = 0L;
    public boolean[] PlayerIsPlaying = new boolean[PlayerAccountPlus];
    public Handler[] PlayerCheckHandler = new Handler[PlayerAccountPlus];
    public int[] PlayerCheckCounter = new int[PlayerAccountPlus];
    public int droppedFrames = 0;
    public float conSpeed = 0f;
    public float netActivity = 0f;
    public long DroppedFramesTotal = 0L;
    public int DeviceRam = 0;
    public String VideoQualityResult = null;
    public String getVideoStatusResult = null;
    public float conSpeedAVG = 0f;
    public float NetActivityAVG = 0f;
    public long NetCounter = 0L;
    public long SpeedCounter = 0L;
    public boolean mLowLatency = false;
    public boolean UseFullBandwidth = false;
    public boolean AlreadyStarted;
    public boolean onCreateReady;
    public boolean IsStopped;
    public boolean IsInAutoMode = true;
    public LoadControl[] loadControl = new LoadControl[PlayerAccount];
    //The default size for all loading dialog
    private FrameLayout.LayoutParams DefaultLoadingLayout;
    //the default size for the main player 100% width x height
    private FrameLayout.LayoutParams PlayerViewDefaultSize;
    //the default size for the main player when on side by side plus chat 75% width x height
    private FrameLayout.LayoutParams PlayerViewSideBySideSize;
    //the default size for the other player when on PP mode, some positions are also used when on side by side for both players
    private FrameLayout.LayoutParams[][] PlayerViewSmallSize = new FrameLayout.LayoutParams[8][3];
    //the default size for the extra player used by side panel and live player feed
    private FrameLayout.LayoutParams[] PlayerViewExtraLayout = new FrameLayout.LayoutParams[6];
    //the default size for the players of multistream 4 player two modes
    private FrameLayout.LayoutParams[] MultiStreamPlayerViewLayout = new FrameLayout.LayoutParams[8];
    private FrameLayout VideoHolder;
    private ProgressBar[] loadingView = new ProgressBar[PlayerAccount + 3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //On create is called onResume so prevent it if already set
        if (!onCreateReady) {
            setContentView(R.layout.activity_player);
            SetDefaultLoadingLayout();

            IsStopped = false;
            onCreateReady = true;

            MainThreadHandler = new Handler(Looper.getMainLooper());
            CurrentPositionHandler = new Handler(Looper.getMainLooper());

            ExtraPlayerHandlerThread = new HandlerThread("ExtraPlayerHandlerThread");
            ExtraPlayerHandlerThread.start();
            ExtraPlayerHandler = new Handler(ExtraPlayerHandlerThread.getLooper());

            SaveBackupJsonThread = new HandlerThread("SaveBackupJsonThread");
            SaveBackupJsonThread.start();
            SaveBackupJsonHandler = new Handler(SaveBackupJsonThread.getLooper());

            PreviewsThread = new HandlerThread("PreviewsThread");
            PreviewsThread.start();
            PreviewsHandler = new Handler(PreviewsThread.getLooper());

            RuntimeThread = new HandlerThread("RuntimeThread");
            RuntimeThread.start();
            RuntimeHandler = new Handler(RuntimeThread.getLooper());
            runtime = Runtime.getRuntime();
            GetPing();

            for (int i = 0; i < PlayerAccountPlus; i++) {
                PlayerCheckHandler[i] = new Handler(Looper.getMainLooper());
            }

            userAgent = Util.getUserAgent(this, getString(R.string.app_name));
            trackSelectorParameters = DefaultTrackSelector.Parameters.getDefaults(this);

            // Prevent small window causing lag to the device
            // Bitrates bigger then 8Mbs on two simultaneous video playback side by side can slowdown some devices
            // even though that device can play a 2160p60 at 30+Mbs on a single playback without problem
            trackSelectorParametersPP = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(PP_PlayerBandwidth)
                    .build();

            trackSelectorParameters = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(mainPlayerBandwidth)
                    .build();

            trackSelectorParametersExtraSmall = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(ExtraSmallPlayerBandwidth)
                    .build();

            VideoHolder = findViewById(R.id.videoholder);
            setPlayerSurface(true);

            deviceIsTV = Tools.deviceIsTV(this);

            DeviceRam = Tools.DeviceRam(this);
            //Ram too big.bigger then max int value... use 500MB
            if (DeviceRam < 0) DeviceRam = 500000000;

            initializeWebview();
        }
    }

    private void SetDefaultLoadingLayout() {
        Point ScreenSize = Tools.ScreenSize(getWindowManager().getDefaultDisplay());
        float Density = this.getResources().getDisplayMetrics().density;

        float Scale = (float) ScreenSize.y / 1080.0f;
        float ScaleDensity = Density / 2.0f;

        int DefaultSize = Math.round(40 * Density * Scale / ScaleDensity);
        DefaultLoadingLayout = new FrameLayout.LayoutParams(DefaultSize, DefaultSize, Gravity.CENTER);

        loadingView[6] = findViewById(R.id.loading2);
        FrameLayout.LayoutParams DefaultLoadingLayoutBottom = new FrameLayout.LayoutParams(DefaultSize, DefaultSize, Gravity.CENTER | Gravity.BOTTOM);
        DefaultLoadingLayoutBottom.bottomMargin = (int) (ScreenSize.x / 40 * Density / ScaleDensity);
        loadingView[6].setLayoutParams(DefaultLoadingLayoutBottom);

        loadingView[5] = findViewById(R.id.loading);
        loadingView[5].setLayoutParams(DefaultLoadingLayout);
    }

    public void setPlayerSurface(boolean surface_view) {
        int[] idGone = idtexture, idVisible = idsurface;
        //Some old devices (old OS N or older) is need to use texture_view to have a proper working PP mode
        if (!surface_view) {
            idGone = idsurface;
            idVisible = idtexture;
        }

        for (int i = 0; i < PlayerAccountPlus; i++) {
            PlayerView[i] = findViewById(idGone[i]);
            PlayerView[i].setVisibility(View.GONE);
            PlayerView[i] = findViewById(idVisible[i]);
        }

        PlayerView[0].setVisibility(View.VISIBLE);
        for (int i = 1; i < PlayerAccountPlus; i++) {
            PlayerView[i].setVisibility(View.GONE);
        }

        for (int i = 0; i < PlayerAccountPlus; i++) {
            loadingView[i] = PlayerView[i].findViewById(R.id.exo_buffering);
            loadingView[i].setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));
            loadingView[i].setBackgroundResource(R.drawable.shadow);
            loadingView[i].setLayoutParams(DefaultLoadingLayout);
        }
    }

    private void PreInitializePlayer(int who_called, long ResumePosition, int position) {
        mWho_Called = who_called;
        mResumePosition = ResumePosition > 0 ? ResumePosition : 0;
        CurrentPositionHandler.removeCallbacksAndMessages(null);
        PlayerCurrentPosition = mResumePosition;
        lastSeenTrackGroupArray = null;
        initializePlayer(position);
    }

    // The main player initialization function
    private void initializePlayer(int position) {
        if (IsStopped) {
            monStop();
            return;
        }
        PlayerCheckHandler[position].removeCallbacksAndMessages(null);

        boolean isSmall = (mainPlayer != position);

        if (PlayerView[position].getVisibility() != View.VISIBLE)
            PlayerView[position].setVisibility(View.VISIBLE);

        if (player[position] == null) {
            trackSelector[position] = new DefaultTrackSelector(this);
            trackSelector[position].setParameters(isSmall ? trackSelectorParametersPP : trackSelectorParameters);

            if (BLACKLISTEDCODECS != null) {
                renderersFactory = new DefaultRenderersFactory(this);
                renderersFactory.setMediaCodecSelector(new BlackListMediaCodecSelector(BLACKLISTEDCODECS));

                player[position] = new SimpleExoPlayer.Builder(this, renderersFactory)
                        .setTrackSelector(trackSelector[position])
                        .setLoadControl(loadControl[mWho_Called])
                        .build();
            } else {
                player[position] = new SimpleExoPlayer.Builder(this)
                        .setTrackSelector(trackSelector[position])
                        .setLoadControl(loadControl[mWho_Called])
                        .build();
            }

            player[position].addListener(new PlayerEventListener(position));
            player[position].addAnalyticsListener(new AnalyticsEventListener());

            PlayerView[position].setPlayer(player[position]);
        }

        PlayerView[position].setPlaybackPreparer(null);
        player[position].setPlayWhenReady(true);
        player[position].setMediaSource(
                mediaSources[position],
                ((mResumePosition > 0) && (mWho_Called > 1)) ? mResumePosition : C.TIME_UNSET);

        player[position].prepare();

        hideLoading(5);
        SwitchPlayerAudio(AudioSource);

        if (!isSmall) {
            //Reset small player view so it shows after big one has started
            int tempPos = position ^ 1;
            if (player[tempPos] != null) {
                PlayerView[tempPos].setVisibility(View.GONE);
                PlayerView[tempPos].setVisibility(View.VISIBLE);
            }
        }

        KeepScreenOn(true);
        droppedFrames = 0;

        //Player can only be acceed from main thread so start a "position listener" to pass the value to webview
        if (mWho_Called > 1) GetCurrentPosition();
    }

    private void initializeSmallPlayer(MediaSource NewMediaSource) {
        if (IsStopped) {
            monStop();
            return;
        }
        PlayerCheckHandler[4].removeCallbacksAndMessages(null);

        if (player[4] == null) {
            trackSelector[4] = new DefaultTrackSelector(this);
            trackSelector[4].setParameters(
                    UseFullBandwidth ? trackSelectorParameters : trackSelectorParametersExtraSmall
            );

            if (BLACKLISTEDCODECS != null) {
                renderersFactory = new DefaultRenderersFactory(this);
                renderersFactory.setMediaCodecSelector(new BlackListMediaCodecSelector(BLACKLISTEDCODECS));

                player[4] = new SimpleExoPlayer.Builder(this, renderersFactory)
                        .setTrackSelector(trackSelector[4])
                        .setLoadControl(loadControl[0])
                        .build();
            } else {
                player[4] = new SimpleExoPlayer.Builder(this)
                        .setTrackSelector(trackSelector[4])
                        .setLoadControl(loadControl[0])
                        .build();
            }

            player[4].addListener(new PlayerEventListenerSmall());
            player[4].addAnalyticsListener(new AnalyticsEventListenerSmall());

            PlayerView[4].setPlayer(player[4]);
        }

        PlayerView[4].setPlaybackPreparer(null);
        player[4].setPlayWhenReady(true);

        player[4].setMediaSource(
                NewMediaSource,
                C.TIME_UNSET);

        player[4].prepare();

        mediaSources[4] = NewMediaSource;

        KeepScreenOn(true);

        if (PlayerView[4].getVisibility() != View.VISIBLE)
            PlayerView[4].setVisibility(View.VISIBLE);
    }

    private void ClearSmallPlayer() {
        PlayerCheckHandler[4].removeCallbacksAndMessages(null);
        PlayerView[4].setVisibility(View.GONE);

        if (player[4] != null) {
            player[4].setPlayWhenReady(false);
            releasePlayer(4);
        }

        PlayerCheckCounter[4] = 0;
        UseFullBandwidth = false;
        if (player[0] == null && player[1] == null && player[2] == null && player[3] == null) {
            KeepScreenOn(false);
        }
    }

    private void initializePlayerMulti(int position, MediaSource NewMediaSource) {
        if (IsStopped) {
            monStop();
            return;
        }

        PlayerCheckHandler[position].removeCallbacksAndMessages(null);

        if (PlayerView[position].getVisibility() != View.VISIBLE)
            PlayerView[position].setVisibility(View.VISIBLE);

        if (player[position] == null) {
            trackSelector[position] = new DefaultTrackSelector(this);
            trackSelector[position].setParameters(trackSelectorParametersPP);

            if (BLACKLISTEDCODECS != null) {
                renderersFactory = new DefaultRenderersFactory(this);
                renderersFactory.setMediaCodecSelector(new BlackListMediaCodecSelector(BLACKLISTEDCODECS));

                player[position] = new SimpleExoPlayer.Builder(this, renderersFactory)
                        .setTrackSelector(trackSelector[position])
                        .setLoadControl(loadControl[0])
                        .build();
            } else {
                player[position] = new SimpleExoPlayer.Builder(this)
                        .setTrackSelector(trackSelector[position])
                        .setLoadControl(loadControl[0])
                        .build();
            }

            player[position].addListener(new PlayerEventListener(position));
            player[position].addAnalyticsListener(new AnalyticsEventListener());

            PlayerView[position].setPlayer(player[position]);
        }

        PlayerView[position].setPlaybackPreparer(null);
        player[position].setPlayWhenReady(true);

        player[position].setMediaSource(
                NewMediaSource,
                C.TIME_UNSET
        );

        player[position].prepare();

        mediaSources[position] = NewMediaSource;
        hideLoading(5);

        if (AudioMulti == 4 || AudioMulti == position) player[position].setVolume(1f);
        else player[position].setVolume(0f);

        KeepScreenOn(true);
        droppedFrames = 0;
    }

    private void ClearPlayer(int position) {
        CurrentPositionHandler.removeCallbacksAndMessages(null);
        PlayerCheckHandler[position].removeCallbacksAndMessages(null);
        PlayerView[position].setVisibility(View.GONE);
        PlayerIsPlaying[position] = false;
        PlayerCurrentPosition = 0L;

        if (player[position] != null) {
            player[position].setPlayWhenReady(false);
            releasePlayer(position);
        }

        PlayerCheckCounter[position] = 0;

        //TODO revise this
        if (mainPlayer != position && !MultiStreamEnable) SwitchPlayerAudio(1);
        else if (AudioMulti != 4 && AudioMulti == position) {
            for (int i = 0; i < PlayerAccount; i++) {
                if (i != position && player[i] != null) {
                    AudioMulti = i;
                    player[i].setVolume(1f);
                    break;
                }
            }
        }

        //All players are close enable screen saver
        if (player[0] == null && player[1] == null && player[2] == null && player[3] == null) {
            KeepScreenOn(false);
        }
    }

    //Stop the player called from js, clear it all
    private void PreResetPlayer(int who_called, int position) {
        if (mainPlayer == 1) SwitchPlayer();

        PicturePicture = false;
        AudioSource = 1;

        for (int i = 0; i < PlayerAccountPlus; i++) {
            PlayerCheckHandler[i].removeCallbacksAndMessages(null);
            mediaSources[i] = null;
        }

        mWho_Called = who_called;
        mResumePosition = 0;

        ClearPlayer(position);
        clearResumePosition();
    }

    //Main release function
    private void releasePlayer(int position) {
        if (player[position] != null) {
            player[position].release();
            player[position] = null;
            trackSelector[position] = null;
        }
    }

    //Basic player position setting, for resume playback 
    private void clearResumePosition() {
        mResumePosition = C.TIME_UNSET;
    }

    private void updateResumePosition(int position) {
        if (player[position] == null) return;

        // If PlayerCheckCounter > 1 we alredy have restarted the player so the value of getCurrentPosition
        // is already gone and we alredy saved the correct mResumePosition
        if (PlayerCheckCounter[position] < 2) {
            mResumePosition = player[position].isCurrentWindowSeekable() ?
                    Math.max(0, player[position].getCurrentPosition()) : C.TIME_UNSET;
        }
    }

    public void KeepScreenOn(boolean keepOn) {
        //On android 6 this seems to cause a exception
        //Didn't manage to catch it on a device I own
        //after this try was added no more reports
        try {
            if (keepOn)
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            Log.w(TAG, "KeepScreenOn Exception ", e);
        }
    }

    private void showLoading() {
        loadingView[5].setVisibility(View.VISIBLE);
    }

    private void showLoadingBotton() {
        loadingView[6].setVisibility(View.VISIBLE);
    }

    private void hideLoading(int position) {
        loadingView[position].setVisibility(View.GONE);
    }

    private void SetDefaultLayouts() {
        int[] positions = {
                Gravity.RIGHT | Gravity.BOTTOM,//0
                Gravity.RIGHT | Gravity.CENTER,//1
                Gravity.RIGHT | Gravity.TOP,//2
                Gravity.CENTER | Gravity.TOP,//3
                Gravity.LEFT | Gravity.TOP,//4
                Gravity.LEFT | Gravity.CENTER,//5
                Gravity.LEFT | Gravity.BOTTOM,//6
                Gravity.CENTER | Gravity.BOTTOM//7
        };

        //Make it visible for calculation
        boolean isNvisible = PlayerView[0].getVisibility() != View.VISIBLE;
        if (isNvisible) PlayerView[0].setVisibility(View.VISIBLE);

        int heightDefault = PlayerView[0].getHeight();
        int mwidthDefault = PlayerView[0].getWidth();

        int heightChat = (int) (heightDefault * 0.75);
        int mwidthChat = (int) (mwidthDefault * 0.75);

        if (isNvisible) PlayerView[0].setVisibility(View.GONE);

        //Default players sizes
        PlayerViewDefaultSize = new FrameLayout.LayoutParams(mwidthDefault, heightDefault, Gravity.TOP);
        PlayerViewSideBySideSize = new FrameLayout.LayoutParams(mwidthChat, heightChat, Gravity.CENTER_VERTICAL);

        //Player extra positions
        Point ScreenSize = Tools.ScreenSize(getWindowManager().getDefaultDisplay());
        float Density = this.getResources().getDisplayMetrics().density;

        float ScaleDensity = Density / 2.0f;
        int margin = (int) (ScreenSize.y / 6.7 * Density / ScaleDensity);
        int ExtraWidth = (int) (mwidthDefault / 3.77);
        int ExtraHeight = (int) (heightDefault / 3.77);

        //Small player for live feed
        PlayerViewExtraLayout[0] = new FrameLayout.LayoutParams(ExtraWidth, ExtraHeight, Gravity.LEFT | Gravity.BOTTOM);
        PlayerViewExtraLayout[1] = new FrameLayout.LayoutParams(ExtraWidth, ExtraHeight, Gravity.LEFT | Gravity.BOTTOM);
        PlayerViewExtraLayout[2] = new FrameLayout.LayoutParams(ExtraWidth, ExtraHeight, Gravity.CENTER | Gravity.BOTTOM);
        PlayerViewExtraLayout[3] = new FrameLayout.LayoutParams(ExtraWidth, ExtraHeight, Gravity.RIGHT | Gravity.BOTTOM);
        PlayerViewExtraLayout[4] = new FrameLayout.LayoutParams(ExtraWidth, ExtraHeight, Gravity.RIGHT | Gravity.BOTTOM);

        //Big player for side panel
        PlayerViewExtraLayout[5] = new FrameLayout.LayoutParams((int) (mwidthDefault / 1.68),(int) (heightDefault / 1.68), Gravity.RIGHT | Gravity.BOTTOM);
        PlayerViewExtraLayout[5].bottomMargin = (int) (ScreenSize.x / 15.7 * Density / ScaleDensity);
        PlayerViewExtraLayout[5].rightMargin = (int) (ScreenSize.y / 13.55 * Density / ScaleDensity);

        for (int i = 0; i < (PlayerViewExtraLayout.length - 1); i++) {
            PlayerViewExtraLayout[i].bottomMargin = (int) (ScreenSize.x / 22 * Density / ScaleDensity);
        }

        PlayerViewExtraLayout[1].leftMargin = margin;
        PlayerViewExtraLayout[3].rightMargin = margin;

        //The side panel player
        PlayerView[4].setLayoutParams(PlayerViewExtraLayout[0]);

        //Small player sizes and positions
        for (int i = 0; i < PlayerViewSmallSize.length; i++) {
            for (int j = 0; j < PlayerViewSmallSize[i].length; j++) {
                PlayerViewSmallSize[i][j] = new FrameLayout.LayoutParams((mwidthDefault / (j + 2)), (heightDefault / (j + 2)), positions[i]);
            }
        }
        //The side PP player
        PlayerView[1].setLayoutParams(PlayerViewSmallSize[PicturePicturePosition][PicturePictureSize]);

        //MultiStream
        //4 way same size
        MultiStreamPlayerViewLayout[0] = new FrameLayout.LayoutParams(
                (mwidthDefault / 2),
                (heightDefault / 2),
                positions[4]
        );
        MultiStreamPlayerViewLayout[1] = new FrameLayout.LayoutParams(
                (mwidthDefault / 2),
                (heightDefault / 2),
                positions[2]
        );

        MultiStreamPlayerViewLayout[2] = new FrameLayout.LayoutParams(
                (mwidthDefault / 2),
                (heightDefault / 2),
                positions[6]
        );

        MultiStreamPlayerViewLayout[3] = new FrameLayout.LayoutParams(
                (mwidthDefault / 2),
                (heightDefault / 2),
                positions[0]
        );

        //4 way main big
        MultiStreamPlayerViewLayout[4] = new FrameLayout.LayoutParams(
                (mwidthDefault * 2 / 3),
                (heightDefault * 2 / 3),
                positions[4]
        );

        MultiStreamPlayerViewLayout[5] = new FrameLayout.LayoutParams(
                (mwidthDefault / 3),
                (heightDefault / 3),
                positions[6]
        );

        MultiStreamPlayerViewLayout[6] = new FrameLayout.LayoutParams(
                (mwidthDefault / 3),
                (heightDefault / 3),
                positions[7]
        );

        MultiStreamPlayerViewLayout[7] = new FrameLayout.LayoutParams(
                (mwidthDefault / 3),
                (heightDefault / 3),
                positions[0]
        );
    }

    //Used in side-by-side mode chat plus video
    private void updateVideSize(boolean FullScreen) {
        isFullScreen = FullScreen;
        if (FullScreen) PlayerView[mainPlayer].setLayoutParams(PlayerViewDefaultSize);//100% width x height
        else PlayerView[mainPlayer].setLayoutParams(PlayerViewSideBySideSize);//CENTER_VERTICAL 75% width x height
    }

    //Used in 50/50 mode two videos on the center plus two chat one on it side
    private void updateVideSizePP(boolean FullScreen) {
        isFullScreen = FullScreen;
        if (FullScreen) {
            PlayerView[mainPlayer].setLayoutParams(PlayerViewDefaultSize);
            UpdadeSizePosSmall(mainPlayer ^ 1);
        } else {
            PlayerView[mainPlayer].setLayoutParams(PlayerViewSmallSize[3][0]);//center top 50% width x height
            PlayerView[mainPlayer ^ 1].setLayoutParams(PlayerViewSmallSize[7][0]);//center bottom 50% width x height
        }
    }

    //SwitchPlayer with is the big and small player used by picture in picture mode
    private void SwitchPlayer() {
        int WillBeMain = mainPlayer ^ 1;//shift 0 to 1 and vice versa

        //Set new video sizes
        PlayerView[WillBeMain].setLayoutParams(PlayerViewDefaultSize);
        PlayerView[mainPlayer].setLayoutParams(PlayerViewSmallSize[PicturePicturePosition][PicturePictureSize]);

        VideoHolder.bringChildToFront(PlayerView[mainPlayer]);

        PlayerView[mainPlayer].setVisibility(View.GONE);
        PlayerView[mainPlayer].setVisibility(View.VISIBLE);

        //change trackSelector to limit video bandwidth
        if (trackSelector[WillBeMain] != null)
            trackSelector[WillBeMain].setParameters(trackSelectorParameters);
        if (trackSelector[mainPlayer] != null)
            trackSelector[mainPlayer].setParameters(trackSelectorParametersPP);

        mainPlayer = WillBeMain;

        //Set proper video volume, muted to small
        SwitchPlayerAudio(AudioSource);
    }

    public void SwitchPlayerAudio(int pos) {
        AudioSource = pos;
        if (pos >= 2) {//both
            AudioMulti = 4;
            SetAudio(0, 1f);
            SetAudio(1, 1f);
        } else if (pos == 1) {//Main
            AudioMulti = 0;
            SetAudio(mainPlayer, 1f);
            SetAudio(mainPlayer ^ 1, 0f);
        } else {//Small
            AudioMulti = 1;
            SetAudio(mainPlayer, 0f);
            SetAudio(mainPlayer ^ 1, 1f);
        }
    }

    public void SetAudio(int pos, float volume) {
        if (player[pos] != null) player[pos].setVolume(volume);
    }

    public void UpdadeSizePosSmall(int pos) {
        PlayerView[pos].setLayoutParams(PlayerViewSmallSize[PicturePicturePosition][PicturePictureSize]);
    }

    public void SetPlayerAudioMulti() {
        for (int i = 0; i < PlayerAccount; i++) {
            if (player[i] != null) {
                if (AudioMulti == 4 || AudioMulti == i) player[i].setVolume(1f);
                else player[i].setVolume(0f);
            }
        }
    }

    public void SetMultiStreamMainBig(int offset) {
        PlayerView[(mainPlayer + offset) % 4].setLayoutParams(MultiStreamPlayerViewLayout[4]);
        PlayerView[((mainPlayer ^ 1) + offset) % 4].setLayoutParams(MultiStreamPlayerViewLayout[5]);
        PlayerView[(2 + offset) % 4].setLayoutParams(MultiStreamPlayerViewLayout[6]);
        PlayerView[(3 + offset) % 4].setLayoutParams(MultiStreamPlayerViewLayout[7]);

        AudioMulti = (mainPlayer + offset) % 4;
        MultiMainPlayer = AudioMulti;
        SetPlayerAudioMulti();

        if (trackSelector[mainPlayer] != null)
            trackSelector[mainPlayer].setParameters(trackSelectorParametersPP);
    }

    public void SetMultiStream(int offset) {
        PlayerView[(mainPlayer + offset) % 4].setLayoutParams(MultiStreamPlayerViewLayout[0]);
        PlayerView[((mainPlayer ^ 1) + offset) % 4].setLayoutParams(MultiStreamPlayerViewLayout[1]);
        PlayerView[(2 + offset) % 4].setLayoutParams(MultiStreamPlayerViewLayout[2]);
        PlayerView[(3 + offset) % 4].setLayoutParams(MultiStreamPlayerViewLayout[3]);

        MultiMainPlayer = (mainPlayer + offset) % 4;

        if (trackSelector[mainPlayer] != null)
            trackSelector[mainPlayer].setParameters(trackSelectorParametersPP);
    }

    public void UnSetMultiStream() {
        ClearPlayer(2);
        ClearPlayer(3);

        if (PicturePicture) updateVideSizePP(isFullScreen);
        else {
            updateVideSize(isFullScreen);
            PlayerView[mainPlayer ^ 1].setLayoutParams(PlayerViewSmallSize[PicturePicturePosition][PicturePictureSize]);
        }

        if (!PicturePicture || player[mainPlayer ^ 1] == null || player[mainPlayer] == null) {
            PicturePicture = false;
            ClearPlayer(mainPlayer ^ 1);
            SwitchPlayerAudio(1);
        } else SwitchPlayerAudio(AudioSource);

        PlayerView[2].setVisibility(View.GONE);
        PlayerView[3].setVisibility(View.GONE);
        if (trackSelector[mainPlayer] != null)
            trackSelector[mainPlayer].setParameters(trackSelectorParameters);
    }

    private void GetCurrentPosition() {
        CurrentPositionHandler.removeCallbacksAndMessages(null);

        CurrentPositionHandler.postDelayed(() -> {
            if (player[mainPlayer] == null) {
                CurrentPositionHandler.removeCallbacksAndMessages(null);
                PlayerCurrentPosition = 0L;
            } else {
                PlayerCurrentPosition = player[mainPlayer].getCurrentPosition();
                GetCurrentPosition();
            }
        }, 500);
    }

    private void GetPing() {
        RuntimeHandler.removeCallbacksAndMessages(null);

        RuntimeHandler.postDelayed(() -> {
            String TempPing = Tools.GetPing(runtime);

            if (TempPing != null) {
                PingValue = Float.parseFloat(TempPing);
                PingValueAVG += PingValue;
                PingCounter++;

            }

            if (!IsStopped) GetPing();
        }, 5000);
    }

    @Override
    public void onResume() {
        super.onResume();
        IsStopped = false;

        if (mWebView != null && AlreadyStarted) {
            mWebView.loadUrl("javascript:smartTwitchTV.Main_CheckResume()");
            GetPing();
        }
        AlreadyStarted = true;
    }

    //This function is called when home key is pressed
    @Override
    public void onStop() {
        super.onStop();
        monStop();
    }

    private void monStop() {
        IsStopped = true;
        int temp_AudioMulti = AudioMulti;

        for (int i = 0; i < PlayerAccountPlus; i++) {
            PlayerCheckHandler[i].removeCallbacksAndMessages(null);
            updateResumePosition(i);
            ClearPlayer(i);
        }

        //Prevent java timeout and related on background
        if (mWebView != null && AlreadyStarted) {
            mWebView.loadUrl("javascript:smartTwitchTV.Main_CheckStop()");
        }
        //ClearPlayer will reset audio position
        AudioMulti = temp_AudioMulti;

        //Reset status values
        PingValue = 0f;
        PingValueAVG = 0f;
        PingCounter = 0L;

        droppedFrames = 0;
        DroppedFramesTotal = 0L;

        conSpeed = 0f;
        conSpeedAVG = 0f;
        SpeedCounter = 0L;

        netActivity = 0f;
        NetActivityAVG = 0f;
        NetCounter = 0L;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < PlayerAccountPlus; i++) {
            ClearPlayer(i);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !deviceIsTV) {
            hideSystemUI();
        }
    }

    public void hideSystemUI() {
        // Enables regular immersive mode.
        // For "lean back" mode, remove SYSTEM_UI_FLAG_IMMERSIVE.
        // Or for "sticky immersive," replace it with SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        // Set the content to appear under the system bars so that the
                        // content doesn't resize when the system bars hide and show.
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // Hide the nav bar and status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    //Close the app
    private void closeThis() {
        finishAndRemoveTask();
    }

    //Minimize the app
    private void minimizeThis() {
        this.moveTaskToBack(true);
    }

    //https://android-developers.googleblog.com/2009/12/back-and-other-hard-keys-three-stories.html
    //Use back key to kill the app
    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            closeThis();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking() && !event.isCanceled()) {
            // if the call key is being released, AND we are tracking
            // it from an initial key down, AND it is not canceled,
            // then handle it.
            mWebView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F2));
            mWebView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F2));
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // this tells the framework to start tracking for
            // a long press and eventual key up.  it will only
            // do so if this is the first down (not a repeat).
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        //Override key play and pause as those get send to js as KEYCODE_MEDIA_PLAY_PAUSE
        if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY) {
            //Prevent send it up down and up
            if (event.getAction() == KeyEvent.ACTION_DOWN) return true;

            //P for play
            mWebView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_P));
            mWebView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_P));
            return true;
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            //Prevent send it up down and up
            if (event.getAction() == KeyEvent.ACTION_DOWN) return true;

            //S for stop/pause
            mWebView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_S));
            mWebView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_S));
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    public  void LoadUrlWebview(String LoadUrlString) {
        MainThreadHandler.post(() -> mWebView.loadUrl(LoadUrlString));
    }

    // A web app that loads all thumbnails content and interact with the player
    private void initializeWebview() {
        mWebView = findViewById(R.id.WebView);
        mWebView.setBackgroundColor(Color.TRANSPARENT);
        if (BuildConfig.DEBUG)
            WebView.setWebContentsDebuggingEnabled(true);

        WebSettings websettings = mWebView.getSettings();

        websettings.setJavaScriptEnabled(true);
        websettings.setDomStorageEnabled(true);
        websettings.setAllowFileAccess(true);
        websettings.setAllowContentAccess(true);
        websettings.setAllowFileAccessFromFileURLs(true);
        //Don't allow CORS from js this is neeed for the images redirec to work
        //when CORS affects a link that is needed to load one uses Tools.readUrl
        //websettings.setAllowUniversalAccessFromFileURLs(true);
        websettings.setUseWideViewPort(true);
        websettings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        mWebView.clearCache(true);
        mWebView.clearHistory();

        mWebView.addJavascriptInterface(new WebAppInterface(this), "Android");

        //When we request a full url change on autentication key request 
        //prevent open it on a external browser
        mWebView.setWebViewClient(new WebViewClient(){

            @SuppressWarnings({"deprecation", "RedundantSuppression"})
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @TargetApi(Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

        });

        mWebView.loadUrl(PageUrl);

        mWebView.requestFocus();
    }

    public class WebAppInterface {
        final Context mwebContext;

        /**
         * Instantiate the interface and set the context
         */
        WebAppInterface(Context context) {
            mwebContext = context;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String mPageUrl() {
            return PageUrl;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mloadUrl(String url) {
            LoadUrlWebview(url);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mshowLoading(boolean show) {
            MainThreadHandler.post(() -> {
                if (show) showLoading();
                else hideLoading(5);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mshowLoadingBottom(boolean show) {
            MainThreadHandler.post(() -> {
                if (show) showLoadingBotton();
                else hideLoading(6);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mclose(boolean close) {
            MainThreadHandler.post(() -> {
                if (close) closeThis();
                else minimizeThis();
            });
        }

        /**
         * Show a toast from the web page
         */
        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void showToast(String toast) {
            MainThreadHandler.post(() -> Toast.makeText(mwebContext, toast, Toast.LENGTH_SHORT).show());
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mupdatesize(boolean FullScreen) {
            MainThreadHandler.post(() -> updateVideSize(FullScreen));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mupdatesizePP(boolean FullScreen) {
            MainThreadHandler.post(() -> updateVideSizePP(FullScreen));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mseekTo(long position) {
            MainThreadHandler.post(() -> {
                if (player[mainPlayer] != null) {
                    long duration = player[mainPlayer].getDuration();
                    long jumpPosition = position > 0 ? position : 0;

                    if (jumpPosition >= duration)
                        jumpPosition = duration - 1000;

                    PlayerCurrentPosition = jumpPosition;
                    player[mainPlayer].seekTo(jumpPosition);
                    player[mainPlayer].setPlayWhenReady(true);
                }
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void startVideo(String videoAddress, int who_called) {
            MainThreadHandler.post(() -> {
                mediaSources[mainPlayer] = Tools.buildMediaSource(Uri.parse(videoAddress), mwebContext, who_called, mLowLatency, "", userAgent);
                PreInitializePlayer(who_called, -1, mainPlayer);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void startVideoOffset(String videoAddress, int who_called, long ResumePosition) {
            MainThreadHandler.post(() -> {
                mediaSources[mainPlayer] = Tools.buildMediaSource(Uri.parse(videoAddress), mwebContext, who_called, mLowLatency, "", userAgent);
                PreInitializePlayer(who_called, ResumePosition, mainPlayer);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void StartAuto(String uri, String masterPlaylistString, int who_called, long ResumePosition, int player) {
            MainThreadHandler.post(() -> {
                mediaSources[mainPlayer ^ player] = Tools.buildMediaSource(Uri.parse(uri), mwebContext, 1, mLowLatency, masterPlaylistString, userAgent);
                PreInitializePlayer(who_called, ResumePosition, mainPlayer ^ player);
                if (player == 1) {
                    PicturePicture = true;
                }
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void PrepareForMulti(String uri, String masterPlaylistString) {
            MainThreadHandler.post(() -> {
                PicturePicture = false;
                ClearPlayer(mainPlayer);
                mainPlayer = mainPlayer ^ 1;
                mediaSources[mainPlayer] = Tools.buildMediaSource(Uri.parse(uri), mwebContext, 1, mLowLatency, masterPlaylistString, userAgent);
                PreInitializePlayer(1, 0, mainPlayer);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void RestartPlayer(int who_called, long ResumePosition, int player) {
            MainThreadHandler.post(() -> {
                PreInitializePlayer(who_called, ResumePosition, mainPlayer ^ player);
                if (player == 1) {
                    PicturePicture = true;
                }
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void StartFeedPlayer(String uri, String masterPlaylistString, int position, boolean fullBandwidth) {
            MainThreadHandler.post(() -> {
                UseFullBandwidth = fullBandwidth;
                mediaSources[4] = Tools.buildMediaSource(Uri.parse(uri), mwebContext, 1, (mLowLatency && UseFullBandwidth), masterPlaylistString, userAgent);
                PlayerView[4].setLayoutParams(PlayerViewExtraLayout[position]);
                initializeSmallPlayer(mediaSources[4]);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void CheckIfIsLiveFeed(String Channel_name, int delayms, String fun, int x, int y) {
            ExtraPlayerHandler.removeCallbacksAndMessages(null);
            ExtraPlayerHandlerResult[x][y] = null;

            ExtraPlayerHandler.postDelayed(() -> {

                try {
                    ExtraPlayerHandlerResult[x][y] = Tools.getStreamData(Channel_name, true);
                } catch (UnsupportedEncodingException e) {
                    Log.w(TAG, "CheckIfIsLiveFeed UnsupportedEncodingException ", e);
                } catch (NullPointerException e) {
                    Log.w(TAG, "CheckIfIsLiveFeed NullPointerException ", e);
                }

                if (ExtraPlayerHandlerResult[x][y] != null)
                    LoadUrlWebview("javascript:smartTwitchTV." + fun + "(Android.GetCheckIfIsLiveFeed(" + x + "," + y + "), " + x + "," + y + ")");
            }, 50 + delayms);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String GetCheckIfIsLiveFeed(int x, int y) {
            return ExtraPlayerHandlerResult[x][y];
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetFeedPosition(int position) {
            MainThreadHandler.post(() -> PlayerView[4].setLayoutParams(PlayerViewExtraLayout[position]));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void ClearFeedPlayer() {
            ExtraPlayerHandler.removeCallbacksAndMessages(null);
            MainThreadHandler.post(PlayerActivity.this::ClearSmallPlayer);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSetlatency(boolean LowLatency) {
            mLowLatency = LowLatency;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void stopVideo(int who_called) {
            MainThreadHandler.post(() -> PreResetPlayer(who_called, mainPlayer));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mClearSmallPlayer() {
            MainThreadHandler.post(() -> {
                PicturePicture = false;
                ClearPlayer(mainPlayer ^ 1);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSwitchPlayer() {
            MainThreadHandler.post(PlayerActivity.this::SwitchPlayer);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSwitchPlayerPosition(int position) {
            PicturePicturePosition = position;
            MainThreadHandler.post(() -> UpdadeSizePosSmall(mainPlayer ^ 1));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSetPlayerPosition(int position) {
            PicturePicturePosition = position;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSwitchPlayerSize(int position) {
            PicturePictureSize = position;
            MainThreadHandler.post(() -> UpdadeSizePosSmall(mainPlayer ^ 1));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSetPlayerSize(int position) {
            PicturePictureSize = position;
            MainThreadHandler.post(PlayerActivity.this::SetDefaultLayouts);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSwitchPlayerAudio(int position) {
            MainThreadHandler.post(() -> SwitchPlayerAudio(position));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSetAudio(int position, float volume) {
            MainThreadHandler.post(() -> SetAudio(position, volume));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mSetPlayerAudioMulti(int position) {
            MainThreadHandler.post(() -> {
                int mposition = position;
                if (position == 0) mposition = mainPlayer;
                else if (position == 1) mposition = mainPlayer ^ 1;

                AudioMulti = mposition;
                SetPlayerAudioMulti();
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetMainPlayerBandwidth(int band) {
            mainPlayerBandwidth = band == 0 ? Integer.MAX_VALUE : band;
            trackSelectorParameters = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(mainPlayerBandwidth)
                    .build();
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetSmallPlayerBandwidth(int band) {
            PP_PlayerBandwidth = band == 0 ? Integer.MAX_VALUE : band;

            trackSelectorParametersPP = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(PP_PlayerBandwidth)
                    .build();

            trackSelectorParametersExtraSmall = trackSelectorParameters
                    .buildUpon()
                    .setMaxVideoBitrate(
                            Math.min(PP_PlayerBandwidth, ExtraSmallPlayerBandwidth)
                    ).build();
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public long getsavedtime() {
            return mResumePosition;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public long gettime() {
            return PlayerCurrentPosition;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public int getAndroid() {
            return 1;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String mreadUrl(String urlString, int timeout, int HeaderQuantity, String access_token) {
            return Tools.readUrl(urlString, timeout, HeaderQuantity, access_token);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String mMethodUrl(String urlString, int timeout, int HeaderQuantity, String access_token, String overwriteID, String postMessage, String Method) {
            return Tools.MethodUrl(urlString, timeout, HeaderQuantity, access_token, overwriteID, postMessage, Method);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getManufacturer() {
            return Build.MANUFACTURER;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getDevice() {
            return Build.MODEL;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public int getSDK() {
            return Build.VERSION.SDK_INT;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mKeepScreenOn(boolean keepOn) {
            MainThreadHandler.post(() -> KeepScreenOn(keepOn));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void PlayPauseChange() {
            MainThreadHandler.post(() -> {
                int playerPos = MultiStreamEnable ? MultiMainPlayer : mainPlayer;
                if (player[playerPos] != null) {

                    boolean state = !player[playerPos].isPlaying();

                    for (int i = 0; i < PlayerAccount; i++) {
                        if (player[i] != null) player[i].setPlayWhenReady(state);
                    }

                    KeepScreenOn(state);
                    mWebView.loadUrl("javascript:smartTwitchTV.Play_PlayPauseChange(" + state + "," + mWho_Called + ")");
                }
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void PlayPause(boolean state) {
            MainThreadHandler.post(() -> {
                for (int i = 0; i < PlayerAccount; i++) {
                    if (player[i] != null) player[i].setPlayWhenReady(state);
                }

                KeepScreenOn(state);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean getPlaybackState() {
            return PlayerIsPlaying[MultiStreamEnable ? MultiMainPlayer : mainPlayer];
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void setPlaybackSpeed(float speed) {
            MainThreadHandler.post(() -> {
                if (MultiStreamEnable) {
                    for (int i = 0; i < PlayerAccount; i++) {
                        if (player[i] != null)
                            player[i].setPlaybackSpeed(speed);
                    }
                } else if (player[mainPlayer] != null)
                    player[mainPlayer].setPlaybackSpeed(speed);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetBuffer(int who_called, int value) {
            BUFFER_SIZE[who_called] = Math.min(value, 15000);
            loadControl[who_called] = Tools.getLoadControl(BUFFER_SIZE[who_called], DeviceRam);

            //MUltiStream and small feed player
            loadControl[0] = Tools.getLoadControl(BUFFER_SIZE[1], DeviceRam / 2);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getversion() {
            return BuildConfig.VERSION_NAME;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void clearCookie() {
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean misCodecSupported() {
            return Tools.isCodecSupported("vp9");
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean misAVC52Supported() {
            return Tools.isAVC52Supported();
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getVideoQualityString() {
            return VideoQualityResult;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void getVideoQuality(int position) {
            VideoQualityResult = null;

            MainThreadHandler.post(() -> {
                int playerPos = MultiStreamEnable ? MultiMainPlayer : mainPlayer;

                if (player[playerPos] != null) VideoQualityResult = Tools.GetVideoQuality(player[playerPos].getVideoFormat());
                else VideoQualityResult = null;

                mWebView.loadUrl("javascript:smartTwitchTV.Play_ShowVideoQuality(" + position + ")");
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getVideoStatusString() {
            return getVideoStatusResult;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void getVideoStatus(boolean showLatency) {
            getVideoStatusResult = null;

            MainThreadHandler.post(() -> {
                ArrayList<String> ret = new ArrayList<>();

                ret.add(Tools.GetCounters(conSpeed, conSpeedAVG, SpeedCounter, "Mb"));//0
                ret.add(Tools.GetCounters(netActivity, NetActivityAVG, NetCounter, "Mb"));//1
                ret.add(String.valueOf(droppedFrames));//2
                ret.add(String.valueOf(DroppedFramesTotal));//3

                //Erase after read
                netActivity = 0L;

                int playerPos = MultiStreamEnable ? MultiMainPlayer : mainPlayer;
                long buffer = 0L;
                long LiveOffset = 0L;

                if (player[playerPos] != null) {
                    buffer = player[playerPos].getTotalBufferedDuration();
                    LiveOffset = player[playerPos].getCurrentLiveOffset();
                }
                ret.add(Tools.getTime(buffer));//4
                ret.add(Tools.getTime(LiveOffset));//5

                ret.add(Tools.GetCounters(PingValue, PingValueAVG, PingCounter, "ms"));//6
                ret.add(String.valueOf(Math.ceil(buffer / 1000.0)));//7

                getVideoStatusResult = new Gson().toJson(ret);

                mWebView.loadUrl("javascript:smartTwitchTV.Play_ShowVideoStatus(" + showLatency +
                        "," + mWho_Called + ")");
            });

        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean deviceIsTV() {
            return deviceIsTV;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void keyEvent(int key, int keyaction) {
            MainThreadHandler.post(() -> mWebView.dispatchKeyEvent(new KeyEvent(keysAction[keyaction], keys[key])));
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getcodecCapabilities(String CodecType) {
            return Tools.codecCapabilities(CodecType);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void setBlackListMediaCodec(String CodecType) {
            BLACKLISTEDCODECS = !CodecType.isEmpty() ? CodecType.split(",") : null;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void msetPlayer(boolean surface_view, boolean FullScreen) {
            MainThreadHandler.post(() -> {
                setPlayerSurface(surface_view);

                if (FullScreen) updateVideSizePP(true);
                else updateVideSize(false);

                UpdadeSizePosSmall(mainPlayer ^ 1);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void mhideSystemUI() {
            MainThreadHandler.post(PlayerActivity.this::hideSystemUI);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void BackupFile(String file, String file_content) {
            SaveBackupJsonHandler.removeCallbacksAndMessages(null);

            SaveBackupJsonHandler.postDelayed(() -> {
                if (Tools.WR_storage(mwebContext)) {
                    Tools.BackupJson(
                            mwebContext.getPackageName(),
                            file,
                            file_content
                    );
                }
            }, 5000);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void GetPreviews(String url) {
            PreviewsResult = null;

            PreviewsHandler.post(() -> {
                Tools.readUrlSimpleObj response;
                for (int i = 0; i < 3; i++) {
                    response = Tools.readUrlSimpleToken(url, 3000 + (500 * i));

                    if (response != null) {
                        if (response.getStatus() == 200) {
                            PreviewsResult = response.getResponseText();
                            LoadUrlWebview("javascript:smartTwitchTV.PlayVod_previews_success(Android.GetPreviewsResult())");
                        }
                        break;
                    }
                }
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String GetPreviewsResult() {
            return PreviewsResult;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean HasBackupFile(String file) {
            return Tools.HasBackupFile(file, mwebContext);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String RestoreBackupFile(String file) {
            if (Tools.WR_storage(mwebContext)) return Tools.RestoreBackupFile(file, mwebContext);

            return null;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void requestWr() {
            MainThreadHandler.post(PlayerActivity.this::check_writeexternalstorage);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean canBackupFile() {
            return Tools.WR_storage(mwebContext);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void EnableMultiStream(boolean MainBig, int offset) {
            MainThreadHandler.post(() -> {
                MultiStreamEnable = true;
                if (MainBig) SetMultiStreamMainBig(offset);
                else SetMultiStream(offset);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean IsMainNotMain() {
            return mainPlayer != 0;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void DisableMultiStream() {
            MainThreadHandler.post(() -> {
                MultiStreamEnable = false;
                UnSetMultiStream();
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void StartMultiStream(int position, String uri, String masterPlaylistString) {
            MainThreadHandler.post(() -> {
                int mposition = position;
                if (position == 0) mposition = mainPlayer;
                else if (position == 1) mposition = mainPlayer ^ 1;

                mediaSources[mposition] = Tools.buildMediaSource(Uri.parse(uri), mwebContext, 1, mLowLatency, masterPlaylistString, userAgent);
                initializePlayerMulti(mposition, mediaSources[mposition]);
            });
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getStreamData(String channel_name_vod_id, boolean islive) {
            try {
                return Tools.getStreamData(channel_name_vod_id, islive);
            } catch (UnsupportedEncodingException e) {
                Log.w(TAG, "getStreamData UnsupportedEncodingException ", e);
            } catch (NullPointerException e) {
                Log.w(TAG, "getStreamData NullPointerException ", e);
            }
            return null;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public boolean isAccessibilitySettingsOn() {
            try {
                return Settings.Secure.getInt(
                        mwebContext.getContentResolver(),
                        android.provider.Settings.Secure.ACCESSIBILITY_ENABLED
                ) == 1;

            } catch (Settings.SettingNotFoundException e) {
                Log.w(TAG, "isAccessibilitySettingsOn SettingNotFoundException "  + e);
            }

            return false;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getWebviewVersion() {
            PackageInfo pInfo = WebViewCompat.getCurrentWebViewPackage(mwebContext);

            return pInfo != null ? pInfo.versionName: null;
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public String getQualities() {
            return Tools.getQualities(trackSelector[mainPlayer]);
        }

        @SuppressWarnings("unused")//called by JS
        @JavascriptInterface
        public void SetQuality(int position) {
            mSetQuality(position);
        }

    }

    public void mSetQuality(int position) {

        if (trackSelector[mainPlayer] != null) {
            IsInAutoMode = position == -1;

            if(IsInAutoMode){
                trackSelector[mainPlayer].setParameters(trackSelectorParameters);
                return;
            }

            MappingTrackSelector.MappedTrackInfo mappedTrackInfo = trackSelector[mainPlayer].getCurrentMappedTrackInfo();

            if (mappedTrackInfo != null) {
                for (int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); rendererIndex++) {

                    if (mappedTrackInfo.getRendererType(rendererIndex) == C.TRACK_TYPE_VIDEO) {

                        DefaultTrackSelector.ParametersBuilder builder = trackSelector[mainPlayer].getParameters().buildUpon();
                        builder.clearSelectionOverrides(rendererIndex).setRendererDisabled(rendererIndex, false);

                        if(position < mappedTrackInfo.getTrackGroups(rendererIndex).get(/* groupIndex */ 0).length) {// else auto quality

                            builder.setSelectionOverride(
                                    rendererIndex,
                                    mappedTrackInfo.getTrackGroups(rendererIndex),
                                    new DefaultTrackSelector.SelectionOverride(/* groupIndex */ 0, position)//groupIndex = 0 as the length of trackGroups in trackGroupArray is always 1
                            );

                        }

                        trackSelector[mainPlayer].setParameters(builder);
                        break;
                    }

                }
            }
        }
    }

    public void RequestGetQualities() {
        if (!PicturePicture && !MultiStreamEnable && mWho_Called < 3) {
            mWebView.loadUrl("javascript:smartTwitchTV.Play_getQualities(" + mWho_Called +  ")");
        }
    }

    // Basic EventListener for exoplayer
    private class PlayerEventListener implements Player.EventListener {

        private final int position;
        private final int Delay_ms;

        private PlayerEventListener(int position) {
            this.position = position;
            this.Delay_ms = (BUFFER_SIZE[mWho_Called] * 2) + 5000 + (MultiStreamEnable ? 2000 : 0);
        }

        @Override
        @SuppressWarnings("ReferenceEquality")
        public void onTracksChanged(@NonNull TrackGroupArray trackGroups, @NonNull TrackSelectionArray trackSelections) {
            //onTracksChanged -> Called when the available or selected tracks change.
            //When the player is already prepare and one changes the Mediasource this will be called before the new Mediasource is prepare
            //So trackGroups.length will be 0 and getQualities = null, after 100ms or so this will be called again and all will be fine
            if (trackGroups != lastSeenTrackGroupArray && trackGroups.length > 0) {
                RequestGetQualities();
                lastSeenTrackGroupArray = trackGroups;
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            PlayerIsPlaying[position] = isPlaying;
        }

        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {

            if (player[position] == null || !player[position].getPlayWhenReady())
                return;

            if (playbackState == Player.STATE_ENDED) {
                PlayerCheckHandler[position].removeCallbacksAndMessages(null);
                player[position].setPlayWhenReady(false);

                PlayerEventListenerClear(position);
            } else if (playbackState == Player.STATE_BUFFERING) {
                //Use the player buffer as a player check state to prevent be buffering for ever
                //If buffer for as long as (BUFFER_SIZE * 2 + etc) do something because player is frozen
                PlayerCheckHandler[position].removeCallbacksAndMessages(null);
                PlayerCheckHandler[position].postDelayed(() -> {

                    //Check if Player was released or is on pause
                    if (player[position] == null || !player[position].isPlaying())
                        return;

                    PlayerEventListenerCheckCounter(position, false);
                }, Delay_ms);
            } else if (playbackState == Player.STATE_READY) {
                PlayerCheckHandler[position].removeCallbacksAndMessages(null);

                //Delay the counter reset to make sure the connection is fine now when not on a auto mode
                if (!IsInAutoMode || mWho_Called == 3)
                    PlayerCheckHandler[position].postDelayed(() -> PlayerCheckCounter[position] = 0, 10000);
                else
                    PlayerCheckCounter[position] = 0;

                //If other not playing just play it so they stay in sync
                if (MultiStreamEnable) {
                    for (int i = 0; i < PlayerAccount; i++) {
                        if (position != i && player[i] != null) player[i].setPlayWhenReady(true);
                    }
                } else {
                    int OtherPlayer = position ^ 1;
                    if (player[OtherPlayer] != null) {
                        if (!player[OtherPlayer].isPlaying())
                            player[OtherPlayer].setPlayWhenReady(true);
                    }
                }

                if (mWho_Called > 1) {
                    LoadUrlWebview("javascript:smartTwitchTV.Play_UpdateDuration(" +
                            player[position].getDuration() + ")");
                }
            }
        }

        @Override
        public void onPlayerError(@NonNull ExoPlaybackException e) {
            PlayerCheckHandler[position].removeCallbacksAndMessages(null);
            PlayerEventListenerCheckCounter(position, Tools.isBehindLiveWindow(e));
        }

    }

    public void PlayerEventListenerCheckCounter(int position, boolean mclearResumePosition) {
        PlayerCheckHandler[position].removeCallbacksAndMessages(null);

        //Pause to things run smother and prevent odd behavior during the checks
        if (player[position] != null) {
            player[position].setPlayWhenReady(false);
        }

        PlayerCheckCounter[position]++;

        if (PlayerCheckCounter[position] < 4 && PlayerCheckCounter[position] > 1 && mWho_Called < 3) {

            if (!IsInAutoMode && !MultiStreamEnable && !PicturePicture)//force go back to auto freeze for too long auto will resolve
                LoadUrlWebview("javascript:smartTwitchTV.Play_PlayerCheck(" + mWho_Called + ")");
            else//already on auto just restart the player
                PlayerEventListenerCheckCounterEnd(position, mclearResumePosition);

        } else if (PlayerCheckCounter[position] > 3) {

            // try == 3 Give up internet is probably down or something related
            PlayerEventListenerClear(position);

        } else if (PlayerCheckCounter[position] > 1) {//only for clips

            // Second check drop quality as it freezes too much
            LoadUrlWebview("javascript:smartTwitchTV.Play_PlayerCheck(" + mWho_Called + ")");

        } else PlayerEventListenerCheckCounterEnd(position, mclearResumePosition);//first check just reset

    }

    //First check only reset the player as it may be stuck
    public void PlayerEventListenerCheckCounterEnd(int position, boolean ClearResumePosition) {
        if (ClearResumePosition || mWho_Called == 1) clearResumePosition();
        else updateResumePosition(position);

        if (mWho_Called == 1) {

            if (MultiStreamEnable) initializePlayerMulti(position, mediaSources[position]);
            else initializePlayer(position);

        } else initializePlayer(position);

    }

    public void PlayerEventListenerClear(int position) {
        hideLoading(5);
        hideLoading(position);
        CurrentPositionHandler.removeCallbacksAndMessages(null);
        String WebViewLoad;
        if (MultiStreamEnable) {
            ClearPlayer(position);
            WebViewLoad = "javascript:smartTwitchTV.Play_MultiEnd(" + position + ")";
        } else if (PicturePicture) {
            boolean mswitch = (mainPlayer == position);

            PicturePicture = false;

            if (mswitch) SwitchPlayer();

            ClearPlayer(position);
            AudioSource = 1;

            WebViewLoad = "javascript:smartTwitchTV.PlayExtra_End(" + mswitch + ")";

        } else WebViewLoad =  "javascript:smartTwitchTV.Play_PannelEndStart(" + mWho_Called + ")";

        LoadUrlWebview(WebViewLoad);
    }

    private class PlayerEventListenerSmall implements Player.EventListener {

        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {

            if (player[4] == null || !player[4].getPlayWhenReady())
                return;

            if (playbackState == Player.STATE_ENDED) {
                PlayerCheckHandler[4].removeCallbacksAndMessages(null);
                player[4].setPlayWhenReady(false);

                ClearSmallPlayer();

                LoadUrlWebview("javascript:smartTwitchTV.Play_CheckIfIsLiveClean()");
            } else if (playbackState == Player.STATE_BUFFERING) {
                //Use the player buffer as a player check state to prevent be buffering for ever
                //If buffer for as long as BUFFER_SIZE * 2 + etc do something because player is frozen
                PlayerCheckHandler[4].removeCallbacksAndMessages(null);
                PlayerCheckHandler[4].postDelayed(() -> {

                    //Check if Player was released or is on pause
                    if (player[4] == null || !player[4].isPlaying())
                        return;

                    PlayerEventListenerCheckCounterSmall();

                }, (BUFFER_SIZE[mWho_Called] * 2) + 5000 + (MultiStreamEnable ? 2000 : 0));
            } else if (playbackState == Player.STATE_READY) {
                PlayerCheckHandler[4].removeCallbacksAndMessages(null);
                PlayerCheckCounter[4] = 0;
            }

        }

        @Override
        public void onPlayerError(@NonNull ExoPlaybackException e) {
            PlayerCheckHandler[4].removeCallbacksAndMessages(null);
            PlayerEventListenerCheckCounterSmall();
        }

    }

    public void PlayerEventListenerCheckCounterSmall() {
        PlayerCheckHandler[4].removeCallbacksAndMessages(null);
        //Pause so things run smother and prevent odd behavior during the checks
        if (player[4] != null) {
            player[4].setPlayWhenReady(false);
        }

        PlayerCheckCounter[4]++;
        if (PlayerCheckCounter[4] < 4)
            initializeSmallPlayer(mediaSources[4]);
        else {
            ClearSmallPlayer();
            LoadUrlWebview("javascript:smartTwitchTV.Play_CheckIfIsLiveClean()");
        }
    }

    private class AnalyticsEventListener implements AnalyticsListener {

        @Override
        public final void onDroppedVideoFrames(@NonNull EventTime eventTime, int count, long elapsedMs) {
            droppedFrames += count;
            DroppedFramesTotal += count;
        }

        @Override
        public void onBandwidthEstimate(@NonNull EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
            conSpeed = (float) bitrateEstimate / 1000000;
            if (conSpeed > 0) {
                SpeedCounter++;
                conSpeedAVG += conSpeed;
            }

            netActivity = (float) totalBytesLoaded * 8 / 1000000;
            if (netActivity > 0) {
                NetCounter++;
                NetActivityAVG += netActivity;
            }
        }
    }

    //For the small player only droppedFrames is need as this is a good mesure of lag
    private class AnalyticsEventListenerSmall implements AnalyticsListener {

        @Override
        public final void onDroppedVideoFrames(@NonNull EventTime eventTime, int count, long elapsedMs) {
            droppedFrames += count;
            DroppedFramesTotal += count;
        }

    }

    @TargetApi(23)
    private void check_writeexternalstorage() {
        if (!Tools.WR_storage(this)) {
            requestPermissions(new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    123);
        }
    }
}

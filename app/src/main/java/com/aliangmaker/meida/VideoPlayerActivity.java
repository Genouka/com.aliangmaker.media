package com.aliangmaker.meida;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import master.flame.danmaku.controller.DrawHandler;
import master.flame.danmaku.controller.IDanmakuView;
import master.flame.danmaku.danmaku.loader.ILoader;
import master.flame.danmaku.danmaku.loader.IllegalDataException;
import master.flame.danmaku.danmaku.loader.android.DanmakuLoaderFactory;
import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.DanmakuTimer;
import master.flame.danmaku.danmaku.model.IDisplayer;
import master.flame.danmaku.danmaku.model.android.DanmakuContext;
import master.flame.danmaku.danmaku.model.android.Danmakus;
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser;
import master.flame.danmaku.danmaku.parser.IDataSource;
import master.flame.danmaku.danmaku.parser.android.BiliDanmukuParser;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static java.security.AccessController.getContext;

public class VideoPlayerActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener {
    private boolean single_touch;
    Runnable resetLandScape = new Runnable(){
        @Override
        public void run() {
            int videoWidth = ijkMediaPlayer.getVideoWidth();
            int videoHeight = ijkMediaPlayer.getVideoHeight();
            float videoAspectRatio = (float) videoWidth / videoHeight;

            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;

            int screenHeight = displayMetrics.heightPixels;
            if (surface_choose) {
                if (videoWidth > videoHeight) {    // Landscape video
                    int surfaceHeight = (int) (screenWidth / videoAspectRatio);
                    ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    params.height = surfaceHeight;
                    surfaceView.setLayoutParams(params);
                } else {    // Portrait video
                    int surfaceWidth = (int) (screenHeight * videoAspectRatio);

                    ViewGroup.LayoutParams params = surfaceView.getLayoutParams();
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    params.width = surfaceWidth;
                    surfaceView.setLayoutParams(params);
                }
            }else {
                if (videoWidth > videoHeight) {    // Landscape video
                    int surfaceHeight = (int) (screenWidth / videoAspectRatio);
                    ViewGroup.LayoutParams params = textureView.getLayoutParams();
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    params.height = surfaceHeight;
                    textureView.setLayoutParams(params);
                } else {    // Portrait video
                    int surfaceWidth = (int) (screenHeight * videoAspectRatio);

                    ViewGroup.LayoutParams params = textureView.getLayoutParams();
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                    params.width = surfaceWidth;
                    textureView.setLayoutParams(params);
                }
            }
            handler.removeCallbacks(this);
        }

    };
    private IjkMediaPlayer ijkMediaPlayer;


    private <T> T getDanmakuSet(String SPId) {
        SharedPreferences sharedPreferences = getSharedPreferences("danmakuSet", MODE_PRIVATE);
        if (SPId.equals("style")) {
            return (T) (Boolean) sharedPreferences.getBoolean("style", true);
        }
        if (SPId.equals("fold")) {
            if (sharedPreferences.getBoolean("fold", false)) {
                HashMap<Integer, Boolean> overlappingEnablePair = new HashMap<Integer, Boolean>();
                overlappingEnablePair.put(BaseDanmaku.TYPE_SCROLL_LR, true);
                overlappingEnablePair.put(BaseDanmaku.TYPE_FIX_BOTTOM, true);
                return (T) (Map) overlappingEnablePair;
            } else return (T) (Map) null;
        }
        if (SPId.equals("same")) {
            return (T) (Boolean) sharedPreferences.getBoolean("merge", false);
        }
        if (SPId.equals("danmakuSize")) {
            int value = sharedPreferences.getInt(SPId, 50) + 50;
            return (T) (Float) ((float) value / 100.0f);
        } else if (SPId.equals("danmakuSpeed")) {
            int value = sharedPreferences.getInt(SPId, 1);
            if (value == 0) {
                return (T) (Float) 1.3f;
            } else if (value == 1) {
                return (T) (Float) 1f;
            } else if (value == 2) {
                return (T) (Float) 0.7f;
            }
        } else if (SPId.equals("danmakuLines")) {
            return (T) (Integer) sharedPreferences.getInt(SPId, 2);
        }
        throw new IllegalArgumentException("Invalid item: " + SPId);
    }
    float CurrentSpeed;
    private SurfaceView surfaceView;
    private ProgressBar progressBar;
    private FrameLayout  surfaceContainer;
    private String getFilePathInFolder(String folderPath, String fileName) {
        File directory = new File(folderPath);
        File pathFolder = new File(directory.getParent());
        File[] files = pathFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().equals(fileName)) {
                    return file.getAbsolutePath(); // 返回目标文件的路径
                }
            }
        }
        return null; // 文件不存在
    }
    private boolean danmakuTrue;
    ConstraintLayout videoLayout;
    private String danmakuInternetUrl;
    private BaseDanmakuParser mParser;//解析器对象
    private DanmakuContext mContext;
    private IDanmakuView mDanmakuView;
    private BaseDanmakuParser createParser(String stream) {

        if (stream == null) {
            return new BaseDanmakuParser() {

                @Override
                protected Danmakus parse() {
                    return new Danmakus();
                }
            };
        }

        ILoader loader = DanmakuLoaderFactory.create(DanmakuLoaderFactory.TAG_BILI);

        try {
            loader.load(stream);
        } catch (IllegalDataException e) {
            e.printStackTrace();
        }
        BaseDanmakuParser parser = new BiliDanmukuParser();
        IDataSource<?> dataSource = loader.getDataSource();
        parser.load(dataSource);
        return parser;

    }
    private void setDanmakuSpeed (float oldSpeedFactor,float newSpeedFactor){
        if(danmakuTrue){
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1).setDuration(500);

            animator.addUpdateListener(animation -> {
                float factor = (float) animation.getAnimatedValue();
                float currentSpeedFactor = oldSpeedFactor + (newSpeedFactor - oldSpeedFactor) * factor;
                mContext.setScrollSpeedFactor(currentSpeedFactor);
            });

            animator.start();
        }
    }

    // 在VideoPlayerActivity中定义一个变量
    private final Handler handler = new Handler();
    private final Runnable updateSpeedRunnable = new Runnable() {
        @Override
        public void run() {
            long tcpSpeed = ijkMediaPlayer.getTcpSpeed();
            double speedInKilobytesPerSecond = tcpSpeed / 1024.0; // 转换为千字节每秒
            @SuppressLint("DefaultLocale") String speedString = String.format("%.2f KB/s", speedInKilobytesPerSecond);

            textView.setVisibility(View.VISIBLE);
            textView.setText(speedString);

            handler.postDelayed(this, 500); // 每隔500毫秒刷新一次网速值
        }
    };
    Boolean surface_choose = true;
    TextView textView;
    ImageView danmaku,lock;
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);
        SharedPreferences sharedPreferences_play_set = getSharedPreferences("play_set", MODE_PRIVATE);
        if (sharedPreferences_play_set.getString("view","surface").equals("texture")) surface_choose = false;

        CurrentSpeed = getDanmakuSet("danmakuSpeed");
        CurrentIjkSpeed = 1.0f;
        danmakuTrue = getIntent().getBooleanExtra("activity", false);
        danmakuInternetUrl = getIntent().getStringExtra("danmakuInternetUrl");
        videoName = getIntent().getStringExtra("videoName");
        backright = getIntent().getBooleanExtra("backright", false);
        diplayland = getIntent().getBooleanExtra("displayland", false);
        single_touch = getIntent().getBooleanExtra("single_touch", false);
        currentProgress = getIntent().getIntExtra("getVideoProgress",0);
        if (diplayland) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            isLandscape = false;
        }
        SharedPreferences sharedPreferences_display = PreferenceManager.getDefaultSharedPreferences(VideoPlayerActivity.this);
        if(sharedPreferences_display.getBoolean("switch_state_display2", false)) {
            findViewById(R.id.dark).setVisibility(View.VISIBLE);
        }
        videoPath = getIntent().getStringExtra("getVideoPath");
        currentTimeTextView2 = findViewById(R.id.current_time_textview2);
        ijkMediaPlayer = new IjkMediaPlayer();
        textRun = findViewById(R.id.textRun);
        danmaku = findViewById(R.id.danmaku);
        seekBar = findViewById(R.id.seekbar);
        topOverlayView = findViewById(R.id.top_overlay_view);
        bottomOverlayView = findViewById(R.id.bottom_overlay_view);
        surfaceContainer = findViewById(R.id.surface_container);
        back = findViewById(R.id.back_button);
        play_pause = findViewById(R.id.play_pause_button);
        screen = findViewById(R.id.screen);
        currentTimeTextView = findViewById(R.id.current_time_textview);
        play_pause.setImageResource(R.drawable.play);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        screenWidth = displayMetrics.widthPixels;
        textRun.setVisibility(View.INVISIBLE);
        thumb = seekBar.getThumb();
        textRegain = findViewById(R.id.imageView13);
        tvPlaybackSpeed = findViewById(R.id.tvPlaybackSpeed);
        tvPlaybackSpeed.setText("倍速");
        lock = findViewById(R.id.lc);
        progressBar = findViewById(R.id.progressBar3);
        currentTimeTextView3 = findViewById(R.id.current_time38);
        textView = findViewById(R.id.wifiSpeed);
        textView.setVisibility(View.GONE);
        mDanmakuView = (IDanmakuView) findViewById(R.id.danmakuSurfaceView);
        videoLayout = findViewById(R.id.video_layout);
        choose_surface(surface_choose);
        lock.setOnClickListener(view -> {
            if (isLocked){
                isLocked = false;
                lock.setImageResource(R.drawable.unlock);
            }else{
                isLocked = true;
                handler.postDelayed(setVisibilityGONE,0);
                lock.setImageResource(R.drawable.lock);
            }
        });
        if (danmakuTrue) {
            danmaku.setOnClickListener(v -> {
                if (mDanmakuView.isShown()) {
                    mDanmakuView.hide();
                    danmaku.setImageResource(R.drawable.danmaku_gone_ic);
                    sharedPreferences_play_set.edit().putBoolean("danmaku_display", false).apply();
                } else {
                    mDanmakuView.show();
                    danmaku.setImageResource(R.drawable.danmakuic);
                    sharedPreferences_play_set.edit().putBoolean("danmaku_display", true).apply();
                }
            });
            danmaku.setOnLongClickListener(v -> {
                startActivity(new Intent(VideoPlayerActivity.this,DanmakuSetActivity.class));
                Toast.makeText(VideoPlayerActivity.this, "重启生效！", Toast.LENGTH_SHORT).show();
                finish();
                return true;
            });
            mContext = DanmakuContext.create();
            // 设置弹幕的最大显示行数
            HashMap<Integer, Integer> maxLinesPair = new HashMap<Integer, Integer>();
            int value = (Integer) getDanmakuSet("danmakuLines")+1;
            maxLinesPair.put(BaseDanmaku.TYPE_SCROLL_RL,value); // 滚动弹幕最大显示3行
            mDanmakuView.setCallback(new DrawHandler.Callback() {

                @Override
                public void prepared() {
                    mDanmakuView.start(ijkMediaPlayer.getCurrentPosition());

                }
                @Override
                public void updateTimer(DanmakuTimer timer) {
                    timer.update(ijkMediaPlayer.getCurrentPosition());
                }
                @Override
                public void danmakuShown(BaseDanmaku danmaku) {
                }
                @Override
                public void drawingFinished() {
                }
            });
            mContext.setDuplicateMergingEnabled(getDanmakuSet("same"))//弹幕重复
                    .setFTDanmakuVisibility(getDanmakuSet("style"))//居中弹幕
                    .setScrollSpeedFactor(getDanmakuSet("danmakuSpeed"))
                    .setScaleTextSize(getDanmakuSet("danmakuSize"))
                    .setMaximumLines(maxLinesPair) //设置最大显示行数
                    .setDanmakuStyle(IDisplayer.DANMAKU_STYLE_SHADOW,1)
                    .preventOverlapping(getDanmakuSet("fold")); //设置防弹幕重叠，null为允许重叠
            if (mDanmakuView != null) {
                if (danmakuInternetUrl != null) {
                    DownloadDanmakuTask task = new DownloadDanmakuTask(this, new DownloadDanmakuTask.DownloadDanmakuListener() {
                        @Override
                        public void onDanmakuDownloaded() {
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    // 这里执行暂停操作，可以根据需要执行UI操作或其他操作
                                    mParser = createParser("/storage/emulated/0/Android/data/com.aliangmaker.media/files/danmaku.xml");
                                    mDanmakuView.prepare(mParser, mContext);
                                    handler.removeCallbacks(this);
                                }
                            }, 800);
                        }
                    });
                    task.execute(danmakuInternetUrl,"danmaku.xml");
                    ijkMediaPlayer.setOption(ijkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", "Mozilla/5.0 BiliDroid/1.1.1 (bbcallen@gmail.com)");
                }else {
                    mParser = createParser(this.getFilePathInFolder(videoPath, "danmaku.xml")); //创建解析器对象，从raw资源目录下解析comments.xml文本
                    mDanmakuView.prepare(mParser, mContext);
                }
                mDanmakuView.showFPS(false); //是否显示FPS
                mDanmakuView.enableDanmakuDrawingCache(true);//弹幕缓存
                if (!sharedPreferences_play_set.getBoolean("danmaku_display", true)) {
                    mDanmakuView.hide();
                    danmaku.setImageResource(R.drawable.danmaku_gone_ic);
                }
            }
        } else danmaku.setVisibility(View.GONE);
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");
        if (sharedPreferences_play_set.getBoolean("jump_play", false)) ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 3);
        if (sharedPreferences_play_set.getBoolean("hard_play", true)) {
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1);
        }else ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "dns_cache_clear", 1); //dns 清理
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 1);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "fflags", "flush_packets");
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1);
        ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1);
        tvPlaybackSpeed.setOnClickListener(v -> showPlaybackSpeedMenu(v));
        textRegain.setOnClickListener(v -> {
            surfaceContainer.setScaleX(1f);
            surfaceContainer.setScaleY(1f);
            scaleFactor = 1f;
            handler.postDelayed(resetVideoViewPosition,0);
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent e) {
                if (ijkMediaPlayer.isPlaying() && !isMoving) {
                    textRun.setVisibility(View.VISIBLE);
                    ijkMediaPlayer.setSpeed(2.0f);
                    setDanmakuSpeed(CurrentSpeed,(Float)getDanmakuSet("danmakuSpeed")*0.5f);
                    12
                    super.onLongPress(e);
                }
                // 在这里处理长按事件
                speed = true;
            }
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if(!isLocked) {
                    if (ijkMediaPlayer.isPlaying() && !single_touch) {
                        ijkMediaPlayer.pause();
                        mDanmakuView.pause();
                        play_pause.setImageResource(R.drawable.pause);
                        handler.removeCallbacks(setVisibilityGONE);
                        handler.postDelayed(setVisibilityVISIBLE, 0);
                    } else {
                        ijkMediaPlayer.start();
                        mDanmakuView.resume();
                        play_pause.setImageResource(R.drawable.play);
                    }
                }
                return true;
            }
        });
        scrollText = findViewById(R.id.textView34);

        screen.setOnClickListener(v -> {
            if (!isLandscape) {
                // 切换为竖屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                isLandscape = true;
            } else {
                // 切换为横屏
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                isLandscape = false;
            }
            handler.postDelayed(resetLandScape, 100);
            handler.postDelayed(resetVideoViewPosition, 120);
        });

        play_pause.setOnClickListener(v -> {
            if (ijkMediaPlayer.isPlaying()) {
                ijkMediaPlayer.pause();
                mDanmakuView.pause();
                play_pause.setImageResource(R.drawable.pause);
                handler.removeCallbacks(setVisibilityGONE);
            } else {
                ijkMediaPlayer.start();
                mDanmakuView.resume();
                play_pause.setImageResource(R.drawable.play);
                handler.postDelayed(setVisibilityGONE, 3500);
            }
        });

        back.setOnClickListener(v -> {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            finish();
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean isPausing = false;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || isGone) {
                    return;
                }
                ijkMediaPlayer.seekTo(progress);
                if(danmakuTrue) {
                    mDanmakuView.seekTo((long) progress);
                    mDanmakuView.pause();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (isGone) {
                    return;
                }
                handler.removeCallbacks(setVisibilityGONE);
                if(ijkMediaPlayer.isPlaying()){
                    isPausing=false;
                } else {
                    isPausing=true;
                }
                ijkMediaPlayer.pause();
                mDanmakuView.pause();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isGone) {
                    return;
                }
                int progress = seekBar.getProgress();
                ijkMediaPlayer.seekTo(progress);
                if (!isPausing){
                    ijkMediaPlayer.start();
                    mDanmakuView.resume();
                }
                handler.postDelayed(setVisibilityGONE, 3500);
            }
        });
        if (videoPath != null) {
            try {
                String cookie = getIntent().getStringExtra("cookie");
                if (cookie != null) {
                    Map<String,String> headers = new HashMap<>();
                    headers.put("Connection","Keep-Alive");
                    headers.put("Referer","https://www.bilibili.com/");
                    headers.put("Origin","https://www.bilibili.com/");
                    headers.put("Cookie",cookie);
                    ijkMediaPlayer.setDataSource(videoPath, headers);
                }else ijkMediaPlayer.setDataSource(videoPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            ijkMediaPlayer.prepareAsync();
        } else {
            Toast.makeText(this, "未找到该视频！", Toast.LENGTH_SHORT).show();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            finish();
        }
        if (getIntent().getBooleanExtra("internet", false)) {
            // 设置缓冲回调监听器
            ijkMediaPlayer.setOnBufferingUpdateListener((iMediaPlayer, percent) -> {
                // 计算已缓冲的进度
                int videoDuration = (int) ijkMediaPlayer.getDuration();
                int bufferedProgress = percent * videoDuration / 100;
                seekBar.setSecondaryProgress(bufferedProgress); // 更新进度条的缓冲进度
            });
        }
        // 在onInfo回调中启动定时器
        if (getIntent().getBooleanExtra("internet", false)) {
            ijkMediaPlayer.setOnInfoListener((mp, what, extra) -> {
                // 判断缓冲状态
                if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    handler.postDelayed(updateSpeedRunnable, 500); // 启动定时器
                    // 显示ProgressBar
                    progressBar.setVisibility(View.VISIBLE);
                } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END ||
                        what == IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    // 隐藏ProgressBar
                    progressBar.setVisibility(View.GONE);
                    // 取消定时器
                    handler.removeCallbacks(updateSpeedRunnable);
                    textView.setVisibility(View.GONE);
                }
                return false;
            });
        }

        ijkMediaPlayer.setOnErrorListener((iMediaPlayer, i, i1) -> {
            Toast.makeText(VideoPlayerActivity.this, "未找到该视频！", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            finish();
            return true;
        });
        ijkMediaPlayer.setOnPreparedListener(iMediaPlayer -> {
            progressBar.setVisibility(View.GONE);
            seekBar.setMax(Math.toIntExact(ijkMediaPlayer.getDuration()) + seekBar.getPaddingEnd());
            handler.postDelayed(resetLandScape, 0);
            ijkMediaPlayer.seekTo(currentProgress);
            handler.postDelayed(resetVideoViewPosition, 400);

        });
        ijkMediaPlayer.setOnCompletionListener(iMediaPlayer -> {
            Toast.makeText(VideoPlayerActivity.this, "视频播放结束", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            if (getIntent().getBooleanExtra("set", false)) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                finish();
            }
            ijkMediaPlayer.pause();
            mDanmakuView.pause();
            play_pause.setImageResource(R.drawable.pause);
        });
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());
        try {
            @SuppressLint({"SoonBlockedPrivateApi", "DiscouragedPrivateApi"}) Field field = scaleGestureDetector.getClass().getDeclaredField("mMinSpan");
            field.setAccessible(true);
            field.set(scaleGestureDetector,1);
        } catch (Exception e) {
            e.printStackTrace();
        }

        scrollText.setText(videoName);
        videoLayout.setOnTouchListener(new View.OnTouchListener() {
            private float startX;
            private float startY;

            private boolean isScaling = false;
            private long lastClickTime = 0;
            private boolean isSwipeBack = false;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                float offsetX;
                float offsetY;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!isLocked) {
                            if (event.getX() < screenWidth * 0.1 && backright) {
                                isSwipeBack = true;
                                startX = (int) event.getX();
                            }
                            if (event.getPointerCount() == 1) {
                                startX = event.getX();
                                startY = event.getY();
                                isScaling = false;
                            }
                        }
                        break;

                    case MotionEvent.ACTION_CANCEL:
                        if(!isLocked)isSwipeBack = false;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (!isLocked) {
                            offsetX = event.getX() - startX; // X 轴上的移动距离
                            offsetY = event.getY() - startY; // Y 轴上的移动距离
                            float value = (float) Math.sqrt(offsetX  * offsetX +  offsetY*  offsetY);
                            if(value > 0.5){
                                handler.postDelayed(setVisibilityGONE, 0);
                                isMoving = true;
                            }
                            if (isSwipeBack && Math.abs(event.getX() - startX) > Math.abs(event.getY() - startY) && event.getX() - startX > 30 && backright && event.getPointerCount() == 1) {
                                finish();
                                return true;
                            }
                            if (event.getPointerCount() > 1) {
                                isScaling = true;
                            } else if (!isScaling) {
                                offsetX = (float) ((event.getX() - startX) * 1.5);
                                offsetY = (float) ((event.getY() - startY) * 1.5);
                                surfaceContainer.setTranslationX(surfaceContainer.getTranslationX() + offsetX);
                                surfaceContainer.setTranslationY(surfaceContainer.getTranslationY() + offsetY);
                                startX = event.getX();
                                startY = event.getY();
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        if (!isLocked) {
                            long currentClickTime = System.currentTimeMillis();
                            long clickDuration = currentClickTime - lastClickTime;
                            lastClickTime = currentClickTime;
                            if (!isMoving && clickDuration > 301 && ijkMediaPlayer.isPlaying() && !speed) {
                                if (scrollText.getVisibility() == View.VISIBLE) {
                                    handler.postDelayed(getPlaybackStatusRunnable, 301);
                                } else {
                                    videoLayout.invalidate();
                                    handler.postDelayed(setVisibilityVISIBLE, 301);
                                    handler.postDelayed(setVisibilityGONE, 3500);
                                }

                            } else if (!isMoving && clickDuration > 301 && !speed) {
                                if (scrollText.getVisibility() == View.VISIBLE) {
                                    handler.postDelayed(setVisibilityGONE, 301);
                                } else {
                                    videoLayout.invalidate();
                                    handler.postDelayed(setVisibilityVISIBLE, 301);
                                    handler.postDelayed(setVisibilityGONE, 3500);
                                }
                            }
                            if (speed && ijkMediaPlayer.isPlaying()) {
                                ijkMediaPlayer.setSpeed(CurrentIjkSpeed);
                                // 速度需要改变前的scrollSpeedFactor
                                setDanmakuSpeed((Float) getDanmakuSet("danmakuSpeed") * 0.5f, CurrentSpeed);
                                textRun.setVisibility(View.GONE);
                            }
                            isMoving = false;
                            speed = false;
                        }else{
                            if (lock.getVisibility()==View.GONE) {
                                lock.setVisibility(View.VISIBLE);
                            }else lock.setVisibility(View.GONE);
                        }
                        break;
                }
                if(!isLocked)scaleGestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }
    private boolean isMoving = false;
    @SuppressLint("UseCompatLoadingForDrawables")
    private void showPlaybackSpeedMenu(View anchorView) {
        String[] playbackSpeeds = {"0.5", "1.0", "1.5", "2.0"};
        ListPopupWindow listPopupWindow = new ListPopupWindow(this);
        listPopupWindow.setAdapter(new ArrayAdapter<>(this, R.layout.text_item, playbackSpeeds));
        listPopupWindow.setAnchorView(anchorView);
        listPopupWindow.setWidth(ListPopupWindow.WRAP_CONTENT);
        listPopupWindow.setHeight(200); // 设置高度
        listPopupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.popup_background));
        // 获取屏幕的宽度
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;

        // 计算弹出选择框的水平偏移量
        int offsetX = screenWidth - anchorView.getWidth() - listPopupWindow.getWidth();

        // 设置弹出选择框的水平偏移量和垂直偏移量
        listPopupWindow.setHorizontalOffset(offsetX);
        listPopupWindow.setVerticalOffset(0);
        listPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
            String selectedPlaybackSpeed = playbackSpeeds[position];
            float speed = Float.parseFloat(selectedPlaybackSpeed);
            ijkMediaPlayer.setSpeed(speed);
            if(danmakuTrue){
                if (speed == 0.5) {
                    setDanmakuSpeed(CurrentSpeed,(Float) getDanmakuSet("danmakuSpeed")*2f);
                    CurrentSpeed = (Float) getDanmakuSet("danmakuSpeed")*2f;
                } else if (speed == 1.0) {
                    setDanmakuSpeed(CurrentSpeed,getDanmakuSet("danmakuSpeed"));
                    CurrentSpeed = (Float) getDanmakuSet("danmakuSpeed");
                } else if (speed == 1.5) {
                    setDanmakuSpeed(CurrentSpeed,(Float) getDanmakuSet("danmakuSpeed")*0.667f);
                    CurrentSpeed = (Float) getDanmakuSet("danmakuSpeed")*0.667f;
                } else if (speed == 2.0) {
                    setDanmakuSpeed(CurrentSpeed,(Float) getDanmakuSet("danmakuSpeed")*0.5f);
                    CurrentSpeed = (Float) getDanmakuSet("danmakuSpeed")*0.5f;
                }
            }
            CurrentIjkSpeed = speed;
            if (speed == 1.0) tvPlaybackSpeed.setText("倍速");
            else tvPlaybackSpeed.setText(String.format("%.1fx", speed));
            listPopupWindow.dismiss();
        });
        listPopupWindow.show();

    }
    private TextView tvPlaybackSpeed;
    float CurrentIjkSpeed;
    private int screenWidth;
    Surface surface;
    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface2, int i, int i1) {
        this.surface = new Surface(surface2);
        ijkMediaPlayer.setSurface(surface);

    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

    }


    private void choose_surface (boolean surface){
        if (surface) {
            surfaceView = new SurfaceView(this);
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT
                    , FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER);
            surfaceView.setLayoutParams(layoutParams);
            surfaceContainer.addView(surfaceView);

            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    ijkMediaPlayer.setDisplay(holder);

                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    // 可以在需要时处理 surface 变化
                }

                @Override

                public void surfaceDestroyed(SurfaceHolder holder) {
                    // 在 surface 销毁时进行必要的操作
                }

            });
        }else {
            textureView = null;
            textureView = new TextureView(this);
            textureView.setSurfaceTextureListener((TextureView.SurfaceTextureListener) VideoPlayerActivity.this);
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            layoutParams.gravity = Gravity.CENTER;
            textureView.setLayoutParams(layoutParams);
            surfaceContainer.addView(textureView);
        }
    }
    TextureView textureView;
    private Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            int currentPosition = (int) ijkMediaPlayer.getCurrentPosition();
            seekBar.setProgress(currentPosition);

            // 获取当前时间并格式化
            int currentSeconds = currentPosition / 1000;
            int minutes = currentSeconds / 60;
            int seconds = currentSeconds % 60;
            String currentTime = String.format("%02d:%02d", minutes, seconds);

            // 获取视频总时间并格式化
            int totalSeconds = (int) (ijkMediaPlayer.getDuration() / 1000);
            int totalMinutes = totalSeconds / 60;
            int totalSecondsRemainder = totalSeconds % 60;
            String totalTime = String.format("%02d:%02d", totalMinutes, totalSecondsRemainder);

            // 在右上角显示当前时间和总时间
            currentTimeTextView.setText(currentTime);
            currentTimeTextView3.setText(totalTime);
            handler.postDelayed(this, 100); // 每500毫秒更新一次进度和时间
        }
    };
    int screenHeight;
    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    private GestureDetector gestureDetector;
    private SeekBar seekBar;
    private int currentProgress;
    private String videoName;
    private ImageView play_pause;
    String videoPath;
    private boolean diplayland;
    private boolean speed = false;
    private TextView currentTimeTextView2;
    private TextView currentTimeTextView3;
    private View topOverlayView;
    private TextView textRun;
    private TextView scrollText;
    private View bottomOverlayView;
    private ImageView back;
    private boolean backright ;
    private boolean isGone;
    private ImageView screen;
    private Drawable thumb;
    private boolean isLandscape = true,isLocked = false;
    private TextView currentTimeTextView;
    int videoWidth;
    private ImageView textRegain;
    int videoHeight;
    Runnable resetVideoViewPosition = new Runnable() {
        @Override
        public void run() {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            int screenHeight = displayMetrics.heightPixels;

            int videoViewWidth = surfaceContainer.getWidth();
            int videoViewHeight =  surfaceContainer.getHeight();

            // 计算将VideoView居中所需的位置
            int desiredX = (screenWidth - videoViewWidth) / 2;
            int desiredY = (screenHeight - videoViewHeight) / 2;
            surfaceContainer.setX(desiredX);
            surfaceContainer.setY(desiredY);
            handler.removeCallbacks(this);
        }
    };
    Runnable getPlaybackStatusRunnable = new Runnable() {
        @Override
        public void run() {
            // 获取视频的播放状态
            boolean isGoing = ijkMediaPlayer.isPlaying();
            if (isGoing) {
                handler.postDelayed(setVisibilityGONE, 0);
            }else {
                seekBar.setThumb(thumb);
                handler.removeCallbacks(setVisibilityGONE);
            }
            handler.removeCallbacks(this);
        }
    };
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    Runnable setVisibilityGONE = new Runnable() {
        @Override
        public void run() {
            isGone = true;
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams)seekBar.getLayoutParams();
            params.bottomMargin = -7;
            seekBar.setLayoutParams(params);
            seekBar.setThumb(ContextCompat.getDrawable(VideoPlayerActivity.this, android.R.color.transparent));
            tvPlaybackSpeed.setVisibility(View.GONE);
            textRegain.setVisibility(View.GONE);
            topOverlayView.setVisibility(View.GONE);
            bottomOverlayView.setVisibility(View.GONE);
            screen.setVisibility(View.GONE);
            currentTimeTextView2.setVisibility(View.GONE);
            danmaku.setVisibility(View.GONE);
            back.setVisibility(View.GONE);
            currentTimeTextView3.setVisibility(View.GONE);
            scrollText.setVisibility(View.GONE);
            play_pause.setVisibility(View.GONE);
            currentTimeTextView.setVisibility(View.GONE);
            videoLayout.requestLayout();

            if(!isLocked){
                lock.setVisibility(View.GONE);
            }
            handler.removeCallbacks(this);
        }
    };
    final Runnable setVisibilityVISIBLE = new Runnable() {
        @Override
        public void run() {
            isGone = false;
            seekBar.setThumb(thumb);
            tvPlaybackSpeed.setVisibility(View.VISIBLE);
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) seekBar.getLayoutParams();
            params.bottomMargin = dpToPx(36);
            seekBar.setLayoutParams(params);
            textRegain.setVisibility(View.VISIBLE);
            scrollText.setVisibility(View.VISIBLE);
            if (danmakuTrue) danmaku.setVisibility(View.VISIBLE);
            topOverlayView.setVisibility(View.VISIBLE);
            bottomOverlayView.setVisibility(View.VISIBLE);
            currentTimeTextView2.setVisibility(View.VISIBLE);
            currentTimeTextView3.setVisibility(View.VISIBLE);
            back.setVisibility(View.VISIBLE);
            play_pause.setVisibility(View.VISIBLE);
            currentTimeTextView.setVisibility(View.VISIBLE);
            screen.setVisibility(View.VISIBLE);
            if(!isLocked){
                lock.setVisibility(View.VISIBLE);
            }
            handler.removeCallbacks(this);
        }
    };
    Runnable updateSystemTimeRunnable = new Runnable() {
        @Override
        public void run() {
            // 获取当前系统时间
            String currentTime = getCurrentSystemTime();

            // 更新系统时间显示
            currentTimeTextView2.setText(currentTime);

            // 每秒钟更新一次系统时间
            handler.postDelayed(this, 1000);
        }
    };
    private String getCurrentSystemTime() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return dateFormat.format(new Date());
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDanmakuView != null) {
            // dont forget release!
            mDanmakuView.release();
            mDanmakuView = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ijkMediaPlayer.seekTo(currentProgress);
        mDanmakuView.seekTo((long) currentProgress);
        handler.postDelayed(updateSeekBar, 600);
        handler.postDelayed(setVisibilityGONE, 3500);
        handler.postDelayed(updateSystemTimeRunnable, 1000);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // 获取保存的数据并显示在 Toast 中（仅当有保存的进度时）
    }

    @Override
    protected void onPause() {
        super.onPause();
        mDanmakuView.pause();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        currentProgress = (int) ijkMediaPlayer.getCurrentPosition();
        int totalDuration = (int) ijkMediaPlayer.getDuration();

        Intent intent = new Intent(VideoPlayerActivity.this, SaveGetVideoProgressService.class);
        if (currentProgress >= totalDuration-3000) {
            intent.putExtra("saveProgress","0");
        } else
            intent.putExtra("saveProgress", currentProgress);
        intent.putExtra("play", "save");
        if (danmakuInternetUrl != null) {
            intent.putExtra("saveName", videoName);
        } else intent.putExtra("saveName", videoPath);
        startService(intent);
        handler.removeCallbacks(updateSeekBar);
        handler.removeCallbacks(setVisibilityGONE);
        ijkMediaPlayer.pause();
        play_pause.setImageResource(R.drawable.pause);
    }
    float minScale;

    private class ScaleListener extends SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {

            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 8.0f)); // 设置缩放范围 (0.1 to 10)

            videoWidth = surfaceContainer.getWidth();
            videoHeight = surfaceContainer.getHeight();

            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            screenWidth = displayMetrics.widthPixels;
            screenHeight = displayMetrics.heightPixels;

            minScale = Math.min((float) screenWidth / videoWidth, (float) screenHeight / videoHeight);
            scaleFactor = Math.max(scaleFactor, minScale);

            surfaceContainer.setScaleX(scaleFactor);
            surfaceContainer.setScaleY(scaleFactor);
            return true;
        }
    }
}

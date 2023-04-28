package app.payload.purpulse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class CameraActivity extends AppCompatActivity {
    private TextureView CameraView;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private static final int REQUEST_CAMERA_PERMISSION = 420;

    // Thread handler member variables
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    //Heart rate detector member variables
    public static int heart_rate_bpm;
    private final int captureRate = 35;
    // detectTime
    private final int setHeartDetectTime = 39;
    private int mCurrentRollingAverage;
    private int mLastRollingAverage;
    private int mLastLastRollingAverage;
    private long[] mTimeArray;
    //Threshold
    private int numCaptures = 0;
    private int mNumBeats = 0;
    private int prevNumBeats = 0;

    private TextView heartBeatCount;
    private TextView scv_text;
    private TextView time_countDown;
    private Button btn_restart;
    //chartDraw
    private WaveUtil waveUtil;
    private WaveShowView waveShowView;
    //IQR
    private FilterAndIQR filterAndIQR;
    private JsonUpload jsonUpload;
    //Wave
    List<Float> exampleWave = Arrays.asList(50f, 40f, 25f, 10f, 90f, 75f, 60f, 50f);
    private CountDownTimer countDownTimer;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mTimeArray = new long[captureRate];
        CameraView = findViewById(R.id.texture);
        btn_restart = findViewById(R.id.btn_restart);
        heartBeatCount = findViewById(R.id.heartBeatCount);
        scv_text = findViewById(R.id.scv_text);
        time_countDown = findViewById(R.id.time_countDown);
        CameraView.setSurfaceTextureListener(textureListener);

        waveShowView = findViewById(R.id.waveView);
        waveUtil = new WaveUtil();
        filterAndIQR = new FilterAndIQR();
        jsonUpload = new JsonUpload();
        initBarAndRestartBtn();
        waveShowView.resetCanavas();

    }

    public void initValue() {
        numCaptures = 0;
        mNumBeats = 0;
        prevNumBeats = 0;
    }

    public void initBarAndRestartBtn() {
        //關閉標題列
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        btn_restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                heartBeatCount.setText("量測準備中...");
                closeCamera();
                onResume();
                initValue();
                waveShowView.resetCanavas();
            }
        });
    }


    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            Bitmap bmp = CameraView.getBitmap();
            int width = bmp.getWidth();
            int height = bmp.getHeight();
            int[] pixels = new int[height * width];
            int[] pixelsFullScreen = new int[height * width];

            bmp.getPixels(pixelsFullScreen, 0, width, 0, 0, width, height);//get full screen and main to detect
            bmp.getPixels(pixels, 0, width, width / 2, height / 2, width / 10, height / 10);//get small screen

            int fullScreenRed = 0; //main to detect Full screen
            int redThreshold = 0;//red threshold

            for (int i = 0; i < height * width; i++) {
                int red = (pixels[i] >> 16) & 0xFF;
                int redFull = (pixelsFullScreen[i] >> 16) & 0xFF;
                fullScreenRed += redFull;
                redThreshold += red;
            }
            int averageThreshold = redThreshold / (height * width);//取閥值 0 1 2

            if (averageThreshold == 2) {

                // Waits 20 captures, to remove startup artifacts.  First average is the sum.
                if (numCaptures == setHeartDetectTime) {
                    mCurrentRollingAverage = fullScreenRed;
                }
                // Next 18 averages needs to incorporate the sum with the correct N multiplier
                // in rolling average.
                else if (numCaptures > setHeartDetectTime && numCaptures < 59) {
                    mCurrentRollingAverage = (mCurrentRollingAverage * (numCaptures - setHeartDetectTime) + fullScreenRed) / (numCaptures - (setHeartDetectTime - 1));
                }

                // From 49 on, the rolling average incorporates the last 30 rolling averages.
                else if (numCaptures >= 59) {
                    mCurrentRollingAverage = (mCurrentRollingAverage * 29 + fullScreenRed) / 30;
                    if (mLastRollingAverage > mCurrentRollingAverage && mLastRollingAverage > mLastLastRollingAverage && mNumBeats < captureRate) {
                        mTimeArray[mNumBeats] = System.currentTimeMillis();
                        mNumBeats++;
                        waveUtil.showWaveData(waveShowView, exampleWave);
                        heartBeatCount.setText("檢測到的心跳次數：" + mNumBeats);
                        if (mNumBeats == captureRate) {
                            cancelCountDownTimer();
                            calcBPM();
                            closeCamera();
                            waveUtil.stop();
                            waveShowView.resetCanavas();
                        }
                    }
                }
                // Another capture
                numCaptures++;
                // Save previous two values
                mLastLastRollingAverage = mLastRollingAverage;
                mLastRollingAverage = mCurrentRollingAverage;
            } else {
                heartBeatCount.setText("請把手指貼緊鏡頭");
            }
        }
    };

    public void cancelCountDownTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            if (cameraDevice != null)
                cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void calcBPM() {
        int med;
        long[] time_dist = new long[captureRate];

        for (int i = 5; i < time_dist.length - 1; i++) {
            time_dist[i] = mTimeArray[i + 1] - mTimeArray[i];
            scv_text.append("RRi：" + time_dist[i] + "\n");
//            Log.d("llll", "calcBPM: " + time_dist[i]);
        }
//        jsonUpload.jsonUploadToServer(time_dist);
        long[] preprocessRRI = filterAndIQR.IQR(time_dist);
        Log.d("why0", "calcBPM: "+ Arrays.toString(preprocessRRI));
        jsonUpload.jsonUploadToServer(preprocessRRI);

        double rmssd = filterAndIQR.calculateRMSSD(preprocessRRI);
        double sdnn = filterAndIQR.calculateSDNN(preprocessRRI);

        DecimalFormat df = new DecimalFormat("#.##");
        String RMSSD = df.format(rmssd);
        String SDNN = df.format(sdnn);

        Arrays.sort(time_dist);
        med = (int) time_dist[time_dist.length / 2];
        heart_rate_bpm = 60000 / med;
        heartBeatCount.setText("RMSSD：" + RMSSD + "\n" + "SDNN：" + SDNN + "\n" + "BPM：" + heart_rate_bpm);
        BottomSheetDialog dialog = new BottomSheetDialog().passData(heart_rate_bpm);
//        dialog.show(getSupportFragmentManager(), "tag?");
        onPause();
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = CameraView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == cameraDevice) {
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(CameraActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        System.out.println("is camera open");
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //check Permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(CameraActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        System.out.println("openCamera X");
    }

    //check camera and flash can be use
    protected void updatePreview() {
        if (null == cameraDevice) {
            System.out.println("updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    //check Permission
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(CameraActivity.this, "Please Grant Permissions", Toast.LENGTH_LONG).show();
                recreate();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if (CameraView.isAvailable()) {
            openCamera();
        } else {
            CameraView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        startActivity(new Intent(this, AboutActivity.class));
        return super.onOptionsItemSelected(item);
    }

    public static class WaveUtil {
        private Timer timer;
        private TimerTask timerTask;
        public void showWaveData(final WaveShowView waveShowView, List<Float> pulseDataList) {
            timer = new Timer();
            timerTask = new TimerTask() {
                @Override
                public void run() {
//                    waveShowView.showLine(50f);
                }
            };
            //500表示調用schedule方法後等待500ms後調用run方法，50表示以後調用run方法的時間間隔
            timer.schedule(timerTask, 50, 300);
        }
        public void stop() {
            if (timer != null) {
                timer.cancel();
                timer.purge();
                timer = null;
            }
            if (null != timerTask) {
                timerTask.cancel();
                timerTask = null;
            }
//            mWaveDataList.clear();
        }
    }





}











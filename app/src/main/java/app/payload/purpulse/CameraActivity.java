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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


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
    private Button btn_restart;
    //chartDraw
    private WaveUtil waveUtil;
    private WaveShowView waveShowView;
    //IQR
    private FilterAndIQR filterAndIQR;
    //Wave
    List<Float> exampleWave = Arrays.asList(40f, 25f, 10f, 90f, 75f, 60f, 50f);
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
        CameraView.setSurfaceTextureListener(textureListener);

        waveShowView = findViewById(R.id.waveView);
        waveUtil = new WaveUtil();
        filterAndIQR = new FilterAndIQR();
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

            int sum = 0; //main to detect Full screen
            int redThreshold = 0;//red threshold

            for (int i = 0; i < height * width; i++) {
                int red = (pixels[i] >> 16) & 0xFF;
                int redFull = (pixelsFullScreen[i] >> 16) & 0xFF;
                sum += redFull;
                redThreshold += red;
            }
            int averageThreshold = redThreshold / (height * width);//取閥值 0 1 2

            if (averageThreshold == 2) {
                // Waits 20 captures, to remove startup artifacts.  First average is the sum.
                if (numCaptures == setHeartDetectTime) {
                    mCurrentRollingAverage = sum;
                }
                // Next 18 averages needs to incorporate the sum with the correct N multiplier
                // in rolling average.
                else if (numCaptures > setHeartDetectTime && numCaptures < 59) {
                    mCurrentRollingAverage = (mCurrentRollingAverage * (numCaptures - setHeartDetectTime) + sum) / (numCaptures - (setHeartDetectTime - 1));
                }

                // From 49 on, the rolling average incorporates the last 30 rolling averages.
                else if (numCaptures >= 59) {
                    mCurrentRollingAverage = (mCurrentRollingAverage * 29 + sum) / 30;
                    if (mLastRollingAverage > mCurrentRollingAverage && mLastRollingAverage > mLastLastRollingAverage && mNumBeats < captureRate) {
                        mTimeArray[mNumBeats] = System.currentTimeMillis();
                        mNumBeats++;

                        if (mNumBeats > prevNumBeats) {
                            new HandlerThread("CountDownThread"){
                                protected void onLooperPrepared(){
                                    countDownTimer = new CountDownTimer(1000L * 30,1000L) {
                                        @Override
                                        public void onTick(long l) {

                                        }

                                        @Override
                                        public void onFinish() {
                                            calcBPM();
                                            mNumBeats = captureRate;
                                        }
                                    };
                                    countDownTimer.start();
                                }
                            }.start();
                            waveUtil.showWaveData(waveShowView, exampleWave);
                        }
                        prevNumBeats = mNumBeats;
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
            Log.d("llll", "calcBPM: " + time_dist[i]);
        }
        long[] preRRI = filterAndIQR.IQR(time_dist);

        DecimalFormat df = new DecimalFormat("#.##");
        String RMSSD = df.format(calculateRMSSD(preRRI));
        String SDNN = df.format(calculateSDNN(preRRI));

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

    // 計算SDNN
    public double calculateSDNN(long[] rrIntervals) {
        double mean = calculateMean(rrIntervals);
        double sumOfSquares = 0.0;
        for (int i = 0; i < rrIntervals.length; i++) {
            sumOfSquares += Math.pow(rrIntervals[i] - mean, 2);
        }
        double variance = sumOfSquares / (rrIntervals.length - 1);
        double sdnn = Math.sqrt(variance);
        return sdnn / 1000;
    }

    // 計算平均值
    public double calculateMean(long[] values) {
        double sum = 0.0;
        for (int i = 0; i < values.length; i++) {
            sum += (double) values[i];
        }
        double mean = sum / values.length;
        return mean;
    }

    // 計算RMSSD
    public double calculateRMSSD(long[] rrIntervals) {
        int n = rrIntervals.length;
        long[] rrIntervalsNoTail = Arrays.copyOfRange(rrIntervals, 0, n - 1);

        double sumOfDifferencesSquared = 0.0;
        for (int i = 1; i < rrIntervalsNoTail.length; i++) {
            double difference = rrIntervalsNoTail[i] - rrIntervalsNoTail[i - 1];
            Log.d("kkkk", "calculateRMSSD: " + difference);
            sumOfDifferencesSquared += difference * difference;
        }

        double rmssd = sumOfDifferencesSquared / (rrIntervals.length - 1);
        return Math.sqrt(rmssd);
    }

    public static class WaveUtil {
        private Timer timer;
        private TimerTask timerTask;
        private final List<Float> mWaveDataList = new ArrayList<>();

        public void showWaveData(final WaveShowView waveShowView, List<Float> pulseDataList) {
            final float normalData = 50f;
            mWaveDataList.addAll(pulseDataList);
            timer = new Timer();
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    float data;
                    if (!mWaveDataList.isEmpty()) {
                        data = mWaveDataList.get(0);
                        mWaveDataList.remove(0);
                    } else {
                        data = normalData;
                    }
                    try {
                        Thread.sleep(500);
                        waveShowView.showLine(data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            //500表示調用schedule方法後等待500ms後調用run方法，50表示以後調用run方法的時間間隔
            timer.schedule(timerTask, 50, 300);
        }

        /**
         * 停止绘制波形
         */
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











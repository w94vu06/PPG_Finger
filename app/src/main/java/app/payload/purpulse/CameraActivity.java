package app.payload.purpulse;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
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

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class CameraActivity extends AppCompatActivity {
    private TextureView textureView;
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
    private final int captureRate = 30;
    private int mCurrentRollingAverage;
    private int mLastRollingAverage;
    private int mLastLastRollingAverage;
    private long[] mTimeArray;
    private int numCaptures = 0;
    private int mNumBeats = 0;
    private int prevNumBeats = 0;
    private TextView heartBeatCount;
    private TextView scv_text;
    private Button btn_restart;
    //chartDraw
    private WaveUtil waveUtil;
    private WaveShowView waveShowView;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        mTimeArray = new long[captureRate];
        textureView = findViewById(R.id.texture);
        btn_restart = findViewById(R.id.btn_restart);
        heartBeatCount = findViewById(R.id.heartBeatCount);
        scv_text = findViewById(R.id.scv_text);
        textureView.setSurfaceTextureListener(textureListener);
        waveShowView = findViewById(R.id.waveView);
        waveUtil = new WaveUtil();
        initBarAndRestartBtn();
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
            Bitmap bmp = textureView.getBitmap();
            int width = bmp.getWidth();
            int height = bmp.getHeight();
            int[] pixels = new int[height * width];
            List<Float> pulseDataList = Arrays.asList(40f, 25f, 10f, 90f, 75f, 60f, 50f);
            // Get pixels from the bitmap, starting at (x,y) = (width/2,height/2) and totaling width/20 rows and height/20 columns
//            bmp.getPixels(pixels, 0, width, width / 2, height / 2, width / 20, height / 20);
            bmp.getPixels(pixels, 0, width, 0, 0, width / 20, height / 20);
            int sum = 0;
            for (int i = 0; i < height * width; i++) {
                int red = (pixels[i] >> 16) & 0xFF;
                sum = sum + red;
            }
//            waveUtil.showWaveData(waveShowView);
            // Waits 20 captures, to remove startup artifacts.  First average is the sum.
            if (numCaptures == 20) {
                mCurrentRollingAverage = sum;
            }

            // Next 18 averages needs to incorporate the sum with the correct N multiplier
            // in rolling average.
            else if (numCaptures > 20 && numCaptures < 49) {
                mCurrentRollingAverage = (mCurrentRollingAverage * (numCaptures - 20) + sum) / (numCaptures - 19);
            }

            // From 49 on, the rolling average incorporates the last 30 rolling averages.
            else if (numCaptures >= 49) {
                mCurrentRollingAverage = (mCurrentRollingAverage * 29 + sum) / 30;
                if (mLastRollingAverage > mCurrentRollingAverage && mLastRollingAverage > mLastLastRollingAverage && mNumBeats < captureRate) {
                    mTimeArray[mNumBeats] = System.currentTimeMillis();
                    mNumBeats++;
                    if (mNumBeats > prevNumBeats) {
                        waveUtil.showWaveData(waveShowView, pulseDataList);
                    }
                    prevNumBeats = mNumBeats;
                    heartBeatCount.setText("檢測到的心跳次數：" + mNumBeats);
                    if (mNumBeats == captureRate) {
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
        }
    };


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

        for (int i = 0; i < time_dist.length - 1; i++) {
            time_dist[i] = mTimeArray[i + 1] - mTimeArray[i];
            scv_text.append("RRi：" + time_dist[i] + "\n");
        }
        for (int i = 0; i < mTimeArray.length; i++) {
            Log.d("yyyy", "calcBPM: " + mTimeArray[i]);
        }

        DecimalFormat df = new DecimalFormat("#.##");
        String RMSSD = df.format(calculateRMSSD(time_dist));
        String SDNN = df.format(calculateSDNN(time_dist));
        heartBeatCount.setText("RMSSD：" + RMSSD + "\n" + "SDNN：" + SDNN);
        Log.d("eeee", "RMSSD:SDNN：" + RMSSD + "：" + SDNN);
        Arrays.sort(time_dist);

        med = (int) time_dist[time_dist.length / 2];
        heart_rate_bpm = 60000 / med;

        BottomSheetDialog dialog = new BottomSheetDialog().passData(heart_rate_bpm);
        dialog.show(getSupportFragmentManager(), "tag?");
        onPause();
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
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
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
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
        double sumOfDifferencesSquared = 0.0;
        for (int i = 0; i < rrIntervals.length - 1; i++) {
            double difference = rrIntervals[i] - rrIntervals[i + 1];
            sumOfDifferencesSquared += Math.pow(difference, 2);
        }
        double rmssd = Math.sqrt(sumOfDifferencesSquared / (rrIntervals.length - 1));
        return rmssd / 1000;
    }

    public class WaveUtil {
        private Timer timer;
        private TimerTask timerTask;
        private List<Float> mWaveDataList = new ArrayList<>();

        public void showWaveData(final WaveShowView waveShowView, List<Float> pulseDataList) {
            final float normalData = 50f;
            mWaveDataList.addAll(pulseDataList);
//            final float[] heartbeatData = new float[]{40f, 25f, 10f, 90f, 75f, 60f};
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
                        waveShowView.showLine(data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
            //500表示調用schedule方法後等待500ms後調用run方法，50表示以後調用run方法的時間間隔
            timer.schedule(timerTask, 500, 500);
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
            mWaveDataList.clear();
        }
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
                closeCamera();
                onResume();
                mNumBeats = 0;
                prevNumBeats = 0;
                heartBeatCount.setText("量測準備中...");
                waveShowView.resetCanavas();
            }
        });
    }


}











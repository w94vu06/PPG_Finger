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

import com.github.mikephil.charting.charts.LineChart;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
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
    private int mCurrentRollingAverage;
    private int mLastRollingAverage;
    private int mLastLastRollingAverage;
    private long[] mTimeArray;
    private int numCaptures = 0;
    private int mNumBeats = 0;
    private int prevNumBeats = 0;
    // My own variable
    private TextView heartBeatCount;
    private Button btn_restart;
    //chartDraw
    private ChartDrawer chartDrawer;
    private LineChart chart_wave;
    private LineChart chart_sawtooth;
    private TextView scv_text;
    private Timer mTimer;
    private LineChart mLineChart;
    private int captureRate = 60;

    private int stopWatch = 0;

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
        chart_wave = findViewById(R.id.chart_wave);
        chart_sawtooth = findViewById(R.id.chart_sawtooth);
        textureView.setSurfaceTextureListener(textureListener);

        //關閉標題列
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }

        btn_restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onResume();
                heartBeatCount.setText("量測準備中...");
                mNumBeats = 0;
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
            Bitmap bmp = textureView.getBitmap();
            int width = bmp.getWidth();
            int height = bmp.getHeight();
            int[] pixels = new int[height * width];

            // Get pixels from the bitmap, starting at (x,y) = (width/2,height/2) and totaling width/20 rows and height/20 columns
            bmp.getPixels(pixels, 0, width, width / 2, height / 2, width / 20, height / 20);
            int sum = 0;
            for (int i = 0; i < height * width; i++) {
                int red = (pixels[i] >> 16) & 0xFF;
                sum = sum + red;
            }

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

                    }
                    prevNumBeats = mNumBeats;
                    heartBeatCount.setText("檢測到的心跳次數：" + mNumBeats);
                    if (mNumBeats == captureRate) {
                        calcBPM();
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

    private CountDownTimer stopwatchTimer = new CountDownTimer(Long.MAX_VALUE, 1) {
        @Override
        public void onTick(long millisUntilFinished) {
            stopWatch++; // 每次計時器觸發時，計時器變數加一
        }

        @Override
        public void onFinish() {
            // ...
        }
    };

    public void startDraw(View view) {
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {

            }
        }, 0, 1000);
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
        return rmssd;
    }

}











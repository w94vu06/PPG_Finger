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
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;


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
    /**
     * 總共要抓的心跳數
     */
    private final int captureRate = 35;
    // detectTime
    private final int setHeartDetectTime = 40;
    private final int rollAvgStandard = setHeartDetectTime + 29;
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
    //chart
    private boolean chartIsRunning = false;
    private LineChart chart;
    private Thread chartThread;
    //IQR
    private FilterAndIQR filterAndIQR;
    private JsonUpload jsonUpload;

    long[] outlierRRI;
    int fullAvgRed,fullAvgGreen,fullAvgBlue;

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
//        time_startCountDown = findViewById(R.id.time_countDown);
        CameraView.setSurfaceTextureListener(textureListener);
        filterAndIQR = new FilterAndIQR();
        jsonUpload = new JsonUpload();

        chart = findViewById(R.id.lineChart);
        initChart();

        closeTopBar();
        restartBtn();

    }


    /**
     * //關閉標題列
     */
    public void closeTopBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    /**
     * //初始化量測用數值
     */
    public void initValue() {
        numCaptures = 0;
        prevNumBeats = 0;
        mNumBeats = 0;
    }

    /**
     * 重新量測-按鈕
     */
    public void restartBtn() {
        btn_restart.setOnClickListener(v -> {
            heartBeatCount.setText("量測準備中...");
            shoutDownDetect();
            onResume();
        });
    }

    public void shoutDownDetect() {
        closeCamera();
        chart.clear();//畫布清除
        initValue();//初始化量測用數值
        initChart();//確保畫布初始化
    }

    /**
     * 設定抓到的畫面
     */
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
            setScreenOn();
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

            int redThreshold = 0;
            int greenThreshold = 0;
            int blueThreshold = 0;//小畫面的紅綠藍
            int fullScreenRed = 0;
            int fullScreenGreen = 0;
            int fullScreenBlue = 0;//整個畫面的紅綠藍
            for (int i = 0; i < height * width; i++) {
                //RED
                int red = (pixels[i] >> 16) & 0xFF;
                int redFull = (pixelsFullScreen[i] >> 16) & 0xFF;
                fullScreenRed += redFull;
                redThreshold += red;
                //GREEN
                int green = (pixels[i] >> 8) & 0xFF;
                int greenFull = (pixelsFullScreen[i] >> 8) & 0xFF;
                fullScreenGreen += greenFull;
                greenThreshold += green;
                //BLUE
                int blue = pixels[i] & 0xFF;
                int blueFull = pixelsFullScreen[i] & 0xFF;
                fullScreenBlue += blueFull;
                blueThreshold += blue;
            }
            //小畫面平均值
            int averageRedThreshold = redThreshold / (height * width);
            int averageGreenThreshold = greenThreshold / (height * width);
            int averageBlueThreshold = blueThreshold / (height * width);
            //整個畫面平均值
            fullAvgRed = fullScreenRed / (height * width);
            fullAvgGreen = fullScreenGreen / (height * width);
            fullAvgBlue = fullScreenBlue / (height * width);
//            Log.d("yyyy", "RED: " + averageRedThreshold + "\nGREEN: " + averageGreenThreshold + "\nBLUE: " + averageBlueThreshold);
            //如果色素閥值是正確的才進行量測
            if (averageRedThreshold == 2) {
                idleHandler.removeCallbacks(idleRunnable);

                // Waits 20 captures, to remove startup artifacts.  First average is the sum.
                //等待前幾個取樣，以去除啟動過程中的初始偏差
                if (numCaptures == setHeartDetectTime) {
                    mCurrentRollingAverage = fullScreenRed;
                }
                // Next 18 averages needs to incorporate the sum with the correct N multiplier
                // in rolling average.
                //在接下來18個取樣之間，程式會使用前面的取樣和當前取樣的加權平均值來計算移動平均值
                else if (numCaptures > setHeartDetectTime && numCaptures < rollAvgStandard) {
                    mCurrentRollingAverage = (mCurrentRollingAverage * (numCaptures - setHeartDetectTime) + fullScreenRed) / (numCaptures - (setHeartDetectTime - 1));
                }

                // From 49 on, the rolling average incorporates the last 30 rolling averages.
                else if (numCaptures >= rollAvgStandard) {
                    mCurrentRollingAverage = (mCurrentRollingAverage * 29 + fullScreenRed) / 30;
                    if (mLastRollingAverage > mCurrentRollingAverage && mLastRollingAverage > mLastLastRollingAverage && mNumBeats < captureRate) {
                        mTimeArray[mNumBeats] = System.currentTimeMillis();
                        mNumBeats++;
                        if (mNumBeats > prevNumBeats) {
                            triggerHandler();
                        }

                        prevNumBeats = mNumBeats;
                        startChartRun();//開始跑圖表
                        heartBeatCount.setText("檢測到的心跳次數：" + mNumBeats);
                        if (mNumBeats == captureRate) {
                            chartIsRunning = false;
                            closeCamera();
                            setScreenOff();
                            calcBPM_RMMSD_SDNN();
                            showInfoOnScrollView();
                            uploadResult();
                        }
                    }
                }
                // Another capture
                numCaptures++;
                // Save previous two values
                mLastLastRollingAverage = mLastRollingAverage;
                mLastRollingAverage = mCurrentRollingAverage;
            } else if (averageBlueThreshold != 0 && averageGreenThreshold != 0) {
                idleHandler.postDelayed(idleRunnable, 15000);
                qualityHandler.postDelayed(qualityRunnable, 5000);
            } else {
//                qualityHandler.postDelayed(qualityRunnable, 5000);
            }
        }
    };

    /**
     * 閒置太久強制中止
     */
    private Handler idleHandler = new Handler();
    private Runnable idleRunnable = new Runnable() {
        @Override
        public void run() {
            // 在此暫時關閉某些功能
            disableSomeFunction();
            chartIsRunning = false;//關閉畫圖
            heartBeatCount.setText("訊號過差，量測失敗");
        }
    };

    /**
     * 關閉功能
     */
    private void disableSomeFunction() {
        // 關閉功能，停止影像
        shoutDownDetect();
    }

    /**
     * 量測提醒
     */
    private Handler qualityHandler = new Handler();
    private Runnable qualityRunnable = new Runnable() {
        @Override
        public void run() {
            heartBeatCount.setText("把手指靠近相機鏡頭，調整直到畫面充滿紅色，然後保持靜止。");
        }
    };


    /**
     * 開啟相機服務
     */
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

    private void calcBPM_RMMSD_SDNN() {
        int med;
        long[] time_dist = new long[captureRate];
        //calcRRI
        for (int i = 5; i < time_dist.length - 1; i++) {
            time_dist[i] = mTimeArray[i + 1] - mTimeArray[i];
        }
        outlierRRI = filterAndIQR.IQR(time_dist);//去掉離群值

        //calcBPM
        long[] getBPMOutlier = outlierRRI;
        Arrays.sort(getBPMOutlier);
        med = (int) outlierRRI[outlierRRI.length / 2];
        heart_rate_bpm = 60000 / med;

        //calcRMSSD_SDNN
        double rmssd = filterAndIQR.calculateRMSSD(outlierRRI);
        double sdnn = filterAndIQR.calculateSDNN(outlierRRI);
        DecimalFormat df = new DecimalFormat("#.##");//設定輸出格式
        String RMSSD = df.format(rmssd);
        String SDNN = df.format(sdnn);

        heartBeatCount.setText("RMSSD：" + RMSSD + "\n" + "SDNN：" + SDNN + "\n" + "BPM：" + heart_rate_bpm);
        onPause();
    }

    /**
     * 顯示結果在ScrollView
     */
    private void showInfoOnScrollView() {
        for (int i = 0; i < outlierRRI.length; i++) {
            scv_text.append("RRi：" + outlierRRI[i] + " / " + "紅色色素：" + fullAvgRed + "\n");
        }
    }

    /**
     * 上傳量測結果至伺服器
     */
    private void uploadResult() {
        jsonUpload.jsonUploadToServer(outlierRRI);//上傳完成的RRI
    }

    /**
     * 顯示從伺服器拉回來的資料
     */
    private void showJsonResultDialog() {
//        BottomSheetDialog dialog = new BottomSheetDialog().passData(heart_rate_bpm);
//        dialog.show(getSupportFragmentManager(), "tag?");
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

    /**
     * keep screen open
     */
    public void setScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void setScreenOff() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
        setScreenOn();
        idleHandler.removeCallbacks(idleRunnable);
        if (CameraView.isAvailable()) {
            openCamera();
        } else {
            CameraView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        setScreenOff();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * 開始跑圖表
     */
    private void startChartRun() {
        if (chartIsRunning) return;
        if (chartThread != null) chartThread.interrupt();

        //簡略寫法
        chartIsRunning = true;
        Runnable runnable = () -> {
            addData(50);
        };

        //簡略寫法
        chartThread = new Thread(() -> {
            while (chartIsRunning) {
                runOnUiThread(runnable);
                if (!chartIsRunning) break;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        chartThread.start();
    }

    /**
     * 觸發心跳
     */
    public void triggerHandler() {
        float inputData = 70;
        addData(inputData);
    }

    /**
     * 設定圖表格式
     */
    private void initChart() {
        chart.getDescription().setEnabled(false);//設置不要圖表標籤
        chart.setBackgroundColor(Color.parseColor("#fdf3f1"));
        chart.setTouchEnabled(false);//設置不可觸碰
        chart.setDragEnabled(false);//設置不可互動
        chart.setDrawBorders(true);  // 啟用畫布的外框線
        chart.setBorderWidth(0.5f);   // 設置外框線的寬度
        chart.setBorderColor(Color.BLACK);  // 設置外框線的顏色
        //設置單一線數據
        LineData data = new LineData();
        data.setValueTextColor(Color.BLACK);
        chart.setData(data);
        //設置左下角標籤
        Legend l = chart.getLegend();
        l.setForm(Legend.LegendForm.LINE);
        l.setTextColor(Color.BLACK);

        //設置Ｘ軸
        XAxis x = chart.getXAxis();
        x.setTextColor(Color.BLACK);
        x.setDrawLabels(false);//去掉X軸標籤
        x.setDrawGridLines(true);//畫X軸線
        x.setGridColor(Color.GRAY);
        x.setGranularity(0.5f);

        YAxis y = chart.getAxisLeft();
        y.setTextColor(Color.BLACK);
        y.setDrawLabels(false);//去掉Y軸標籤
        y.setDrawGridLines(true);//畫Y軸線
        y.setGridColor(Color.GRAY);
        y.setGranularity(0.2f);

        y.setAxisMaximum(100);//最高100
        y.setAxisMinimum(0);//最低0

        chart.getAxisRight().setEnabled(false);//右邊Y軸不可視
//        chart.setVisibleXRange(0,60);//設置顯示範圍
    }

    /**
     * 新增資料
     */
    private void addData(float inputData) {
        LineData data = chart.getData();//取得原數據
        ILineDataSet set = data.getDataSetByIndex(0);//取得曲線(因為只有一條，故為0，若有多條則需指定)
        if (set == null) {
            set = createSet();
            data.addDataSet(set);//如果是第一次跑則需要載入數據
        }
        data.addEntry(new Entry(set.getEntryCount(), inputData), 0);//新增數據點
        //
        data.notifyDataChanged();
        data.setDrawValues(false);//是否繪製線條上的文字
        chart.notifyDataSetChanged();
        chart.setVisibleXRange(0, 60);//設置可見範圍
        chart.moveViewToX(data.getEntryCount());//將可視焦點放在最新一個數據，使圖表可移動
    }

    /**
     * 設置數據線的樣式
     */
    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, "");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(Color.parseColor("#7d7d7d"));
        set.setLineWidth(2);
        set.setDrawCircles(false);
        set.setDrawFilled(false);
//        set.setFillColor(Color.RED);
//        set.setFillAlpha(50);
        set.setValueTextColor(Color.BLACK);
        set.setDrawValues(false);
        return set;
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
}











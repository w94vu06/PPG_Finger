package app.payload.purpulse;

import android.graphics.Color;
import android.graphics.Paint;
import android.sax.EndElementListener;
import android.view.TextureView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.listener.BarLineChartTouchListener;

import java.util.ArrayList;
import java.util.List;

public class ChartDrawer {

    private static int maxCount = 60;
    public static void addEntry() {

    }
    /** 直角三角波 */
    public static void initSawtoothChart(LineChart chart) {
        //設定資料
        List<Entry> entries = new ArrayList<>();
//        for (int i = 0; i < 60; i++) {
//            entries.add(new Entry(i, longs[i]));
//        }
        // 設置LineChart的屬性
        chart.getXAxis().setAxisMinimum(0);
        chart.getXAxis().setAxisMaximum(20);
        chart.getAxisLeft().setAxisMinimum(-1.5f);
        chart.getAxisLeft().setAxisMaximum(1.5f);
        chart.getDescription().setEnabled(false);

        // 創建LineDataSet對象
        LineDataSet dataSet = new LineDataSet(entries, "直角三角波");
        dataSet.setColor(Color.parseColor("#7d7d7d"));//線條顏色
        dataSet.setCircleColor(Color.parseColor("#7d7d7d"));//圓點顏色
        dataSet.setLineWidth(1.5f);//線條寬度
        dataSet.setDrawCircles(false);//關閉點點

        // 將LineDataSet添加到LineData中
        LineData lineData = new LineData(dataSet);
        lineData.setDrawValues(false);//是否繪製線條上的文字
        chart.setData(lineData);
        chart.getDescription().setEnabled(false);
        chart.setScaleYEnabled(false);
        chart.setBackgroundColor(Color.parseColor("#fff3fa"));

        YAxis rightAxis = chart.getAxisRight();
        YAxis leftAxis = chart.getAxisLeft();
        rightAxis.setEnabled(false);
        leftAxis.setEnabled(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setTextSize(1f);
        xAxis.setAxisMinimum(50f);
        xAxis.setDrawAxisLine(true);//是否繪製軸線
        xAxis.setDrawGridLines(false);//設置x軸上每個點對應的線
        xAxis.setDrawLabels(false);//繪製標籤  指x軸上的對應數值
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);//設置x軸的顯示位置
        xAxis.setGranularity(1f);//禁止放大後x軸標籤重繪
        xAxis.setEnabled(true);
        xAxis.setDrawGridLines(true);//背景網格
        //slide
        float scaleX = chart.getScaleX();
        if (scaleX == 1) {
            chart.zoomToCenter(5, 1f);
        } else {
            BarLineChartTouchListener barLineChartTouchListener = (BarLineChartTouchListener) chart.getOnTouchListener();
            barLineChartTouchListener.stopDeceleration();
            chart.fitScreen();
        }
        // 將數組中的數據點添加到LineDataSet中
        dataSet.setValues(entries);

        // 調用invalidate()方法以重新繪製圖表
        chart.invalidate();
    }


    //突波
    public static void initWaveChart(LineChart chart) {
        // 設置LineChart的屬性
        chart.getXAxis().setAxisMinimum(0);
        chart.getXAxis().setAxisMaximum(10);
        chart.getAxisLeft().setAxisMinimum(0);
        chart.getAxisLeft().setAxisMaximum(100);
        chart.getDescription().setEnabled(false);
        // 創建LineDataSet對象
        LineDataSet dataSet = new LineDataSet(new ArrayList<Entry>(), "心電圖");
        dataSet.setColor(Color.RED);
        dataSet.setLineWidth(2f);
        dataSet.setDrawCircles(false);

        // 將LineDataSet添加到LineData中
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // 新增新數據
        float newData = 50f;
        dataSet.addEntry(new Entry(dataSet.getEntryCount(), newData));

        // 調用invalidate()方法以重新繪製圖表
        chart.invalidate();
    }
}


package app.payload.purpulse;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;

/**
 * @Author: gl
 * @CreateDate: 2019/10/23
 * @Description: 繪製波形的view
 */
public class WaveShowView extends View {

    private float mWidth = 0, mHeight = 0;//自身大小
    private int mBackGroundColor = Color.WHITE;
    private Paint mLinePaint;//畫筆
    private Paint mWavePaint;//心電圖的折現
    private Path mPath;//心電圖的路徑

    private ArrayList refreshList = new ArrayList();//後加的數據點
    private int row;//背景網格的行數和列數

    //心電
    private float MAX_VALUE = 100;
    private float WAVE_LINE_STROKE_WIDTH = 3;
    private int mWaveLineColor = Color.parseColor("#E92900");//波形颜色
    private float nowX, nowY;//目前的xy坐標

    //網格
    private final int GRID_SMALL_WIDTH = 10;//每一個網格的寬度和高度,包括線
    private final int GRID_BIG_WIDTH = 50;//每一個大網格的寬度和高度,包括線
    private int xSmallNum, ySmallNum, xBigNum, yBigNum;//小網格的橫格，豎格，大網格的橫格，豎格數量
    private final int GRID_LINE_WIDTH = 2;//網格的線的寬度
    private int mWaveSmallLineColor = Color.parseColor("#ffffff");//中網格顏色
    private int mWaveBigLineColor = Color.parseColor("#fdf3f1");//小網格顏色

    public WaveShowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveShowView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        mHeight = h;
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void init() {
        mLinePaint = new Paint();
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(GRID_LINE_WIDTH);
        mLinePaint.setAntiAlias(true);//抗锯齿效果

        mWavePaint = new Paint();
        mWavePaint.setStyle(Paint.Style.STROKE);
        mWavePaint.setColor(mWaveLineColor);
        mWavePaint.setStrokeWidth(WAVE_LINE_STROKE_WIDTH);
        mWavePaint.setAntiAlias(true);//抗锯齿效果

        mPath = new Path();

    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mWidth = getMeasuredWidth();//獲取view的寬
        mHeight = getMeasuredHeight();//獲取view的高

        row = (int) (mWidth / (GRID_SMALL_WIDTH));//獲取行數

        //小網格
        xSmallNum = (int) (mHeight / GRID_SMALL_WIDTH);//橫線個數=總高度/小網格高度
        ySmallNum = (int) (mWidth / GRID_SMALL_WIDTH);//豎線個數=總寬度/小網格寬度
        //大網格
        xBigNum = (int) (mHeight / GRID_BIG_WIDTH);//橫線個數
        yBigNum = (int) (mWidth / GRID_BIG_WIDTH);//豎線個數
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //繪製網格
        drawGrid(canvas);
        //繪製波形
        drawWaveLine(canvas);
    }

    /**
     * 畫折線
     *
     * @param canvas
     */
    private void drawWaveLine(Canvas canvas) {
        if (null == refreshList || refreshList.size() <= 0) {
            return;
        }
        mPath.reset();
        mPath.moveTo(-2f, mHeight / 1.5f);
        for (int i = 0; i < refreshList.size(); i++) {
            nowX = i * GRID_SMALL_WIDTH;
            try {
                float dataValue = (float) refreshList.get(i);
                if (dataValue > 0) {
//                if (dataValue > MAX_VALUE * 0.8) {
//                    dataValue = MAX_VALUE * 0.8f;
//                }
                } else {
                    if (dataValue < -MAX_VALUE * 0.8) {
                        dataValue = -MAX_VALUE * 0.8f;
                    }
                }
                nowY = mHeight / 4 + dataValue * (mHeight / (MAX_VALUE * 2));
                mPath.lineTo(nowX, nowY);
            } catch (Exception e) {
            }
        }
        canvas.drawPath(mPath, mWavePaint);
        if (refreshList.size() > row && !refreshList.isEmpty()) {
            refreshList.remove(0);
        }
    }

    //畫網格
    private void drawGrid(Canvas canvas) {
        canvas.drawColor(mBackGroundColor);
        //畫小網格
        mLinePaint.setColor(mWaveSmallLineColor);
        //畫橫線
        for (int i = 0; i < xSmallNum + 1; i++) {
            canvas.drawLine(0, i * GRID_SMALL_WIDTH,
                    mWidth, i * GRID_SMALL_WIDTH, mLinePaint);
        }
        //畫豎線
        for (int i = 0; i < ySmallNum + 1; i++) {
            canvas.drawLine(i * GRID_SMALL_WIDTH, 0,
                    i * GRID_SMALL_WIDTH, mHeight, mLinePaint);
        }

        //畫大網格
        mLinePaint.setColor(mWaveBigLineColor);
        //畫橫線
        for (int i = 0; i < xBigNum + 1; i++) {
            canvas.drawLine(0, i * GRID_BIG_WIDTH,
                    mWidth, i * GRID_BIG_WIDTH, mLinePaint);
        }
        //畫豎線
        for (int i = 0; i < yBigNum + 1; i++) {
            canvas.drawLine(i * GRID_BIG_WIDTH, 0,
                    i * GRID_BIG_WIDTH, mHeight, mLinePaint);
        }
    }

    public void showLine(float line) {
        refreshList.add(line);
        postInvalidate();
    }

    //重置折線的座標集合
    public void resetCanavas() {
        refreshList.clear();
    }

}


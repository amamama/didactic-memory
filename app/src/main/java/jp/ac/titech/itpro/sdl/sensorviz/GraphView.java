package jp.ac.titech.itpro.sdl.sensorviz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class GraphView extends View {
    private static final String TAG = GraphView.class.getSimpleName();
    private static final float MAX_Y = 30;
    private static final int NDATA_INIT = 256;
    private static final int DW = 5;

    private int ndata = NDATA_INIT;
    private float[] data1 = new float[NDATA_INIT];
    private float[] data2 = new float[NDATA_INIT];
    private int index = 0;
    private int x0, y0;

    private int dh = 1;

    private int internalWidth;

    private final Paint paint = new Paint();

    public GraphView(Context context) {
        this(context, null);
    }

    public GraphView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Log.i(TAG, "onSizeChanged: w=" + w + " h=" + h);

        internalWidth = (w / DW) * DW;
        // data size
        ndata = (internalWidth / DW) + 1;
        if (ndata > data1.length) {
            index = 0;
            data1 = new float[ndata];
            data2 = new float[ndata];
        }

        // original point, width, height
        x0 = 0;
        y0 = h / 2;
        dh = Math.max((int) (y0 / MAX_Y), 1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // grid lines
        paint.setColor(Color.LTGRAY);
        paint.setStrokeWidth(1);
        int h = getHeight();

        for (int y = y0; y < h; y += dh * 5) {
            canvas.drawLine(0, y, internalWidth, y, paint);
        }
        for (int y = y0; y > 0; y -= dh * 5) {
            canvas.drawLine(0, y, internalWidth, y, paint);
        }
        for (int x = x0; x < internalWidth; x += DW * 5) {
            canvas.drawLine(x, 0, x, h, paint);
        }

        // y0 line, whole area
        paint.setColor(Color.BLACK);
        canvas.drawLine(0, y0, internalWidth, y0, paint);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRect(1, 1, internalWidth, h, paint);

        // graph
        paint.setColor(Color.BLUE);
        paint.setStrokeWidth(2);
        for (int i = 0; i < ndata - 1; i++) {
            int j = (index + i) % ndata;
            int x1 = x0 + DW * i;
            int x2 = x0 + DW * (i + 1);
            int y1 = (int) (y0 + dh * data1[j]);
            int y2 = (int) (y0 + dh * data1[(j + 1) % ndata]);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
        paint.setColor(Color.RED);
        for (int i = 0; i < ndata - 1; i++) {
            int j = (index + i) % ndata;
            int x1 = x0 + DW * i;
            int x2 = x0 + DW * (i + 1);
            int y1 = (int) (y0 + dh * data2[j]);
            int y2 = (int) (y0 + dh * data2[(j + 1) % ndata]);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
    }

    public void addData(float val1, float val2) {
        data1[index] = val1;
        data2[index] = val2;
        index = (index + 1) % ndata;
        invalidate();
    }
}

package jp.ac.titech.itpro.sdl.sensorviz;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements SensorEventListener, Runnable {
    private final static String TAG = MainActivity.class.getSimpleName();
    private final static long GRAPH_REFRESH_PERIOD_MS = 20;

    private static List<Integer> DELAYS = new ArrayList<>();
    static {
        DELAYS.add(SensorManager.SENSOR_DELAY_FASTEST);
        DELAYS.add(SensorManager.SENSOR_DELAY_GAME);
        DELAYS.add(SensorManager.SENSOR_DELAY_UI);
        DELAYS.add(SensorManager.SENSOR_DELAY_NORMAL);
    }

    private static final float ALPHA = 0.75f;

    private TextView typeView;
    private TextView infoView;
    private GraphView xView, yView, zView;

    private SensorManager manager;
    private Sensor sensor;

    private final Handler handler = new Handler();
    private final Timer timer = new Timer();

    private float rx, ry, rz;
    private float vx, vy, vz;

    private int rate;
    private int accuracy;
    private long prevTimestamp;

    private int delay = SensorManager.SENSOR_DELAY_NORMAL;
    private int type = Sensor.TYPE_ACCELEROMETER;

    private AudioTrack accAudioTrack[] = new AudioTrack[3];
    private  AudioTrack gyroAudioTrack[] = new AudioTrack[3];
    private int HZ = 44100;
    private int ENC = AudioFormat.ENCODING_PCM_FLOAT;


    private void initAudioTrack() {
        AudioAttributes attr = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();

        AudioFormat format = new AudioFormat.Builder()
            .setSampleRate(HZ)
            .setEncoding(ENC)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build();
        int bufSize = AudioTrack.getMinBufferSize(HZ, AudioFormat.CHANNEL_OUT_MONO, ENC);
        int id = AudioManager.AUDIO_SESSION_ID_GENERATE;
        for(int i = 0; i < 3; i++) {
            accAudioTrack[i] = new AudioTrack(attr, format, bufSize, AudioTrack.MODE_STREAM, id);
            gyroAudioTrack[i] = new AudioTrack(attr, format, bufSize, AudioTrack.MODE_STREAM, id);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        typeView = findViewById(R.id.type_view);
        infoView = findViewById(R.id.info_view);
        xView = findViewById(R.id.x_view);
        yView = findViewById(R.id.y_view);
        zView = findViewById(R.id.z_view);

        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (manager == null) {
            Toast.makeText(this, R.string.toast_no_sensor_manager, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        sensor = manager.getDefaultSensor(type);
        if (sensor == null) {
            String text = getString(R.string.toast_no_sensor_available, sensorTypeName(type));
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initAudioTrack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        manager.registerListener(this, sensor, delay);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(MainActivity.this);
            }
        }, 0, GRAPH_REFRESH_PERIOD_MS);
    }

    private int phase[] = {0, 0, 0};

    private float[] generateCurve(float hz, float amp, int size, int idx) {
        float arr[] = new float[size];
        int thre = (int)(HZ / hz);
        for(int i = 0; i < size; i++) {
            arr[i] = amp * (float)Math.sin(2 * Math.PI * (i + phase[idx]) / thre);
        }
        phase[idx] = (size + phase[idx]) % thre;
        return arr;
    }

    private float f(float x) {
        return 2 * x * x + x + 1;
    }

    @Override
    public void run() {
        infoView.setText(getString(R.string.info_format, accuracy, rate));
        xView.addData(rx, vx);
        yView.addData(ry, vy);
        zView.addData(rz, vz);
        float rate_s = (float)rate / 1000 / 1000;
        int size = (int)(rate_s * HZ);
        //int size = (int) GRAPH_REFRESH_PERIOD_MS / 1000 * HZ;
        //Log.i(TAG, "rate_s = " + rate_s + ", size = " + size);

        Sensor sensor = manager.getDefaultSensor(type);
        Log.i(TAG, "mr" + sensor.getMaximumRange());
        float mr = sensor.getMaximumRange();
        float hzx = f(((vx + mr) / (2 * mr)));
        float hzy = f(((vy + mr) / (2 * mr)));
        float hzz = f(((vz + mr) / (2 * mr)));
        float ax = Math.abs(vx / mr);
        float ay = Math.abs(vy / mr);
        float az = Math.abs(vz / mr);
        /*
        float datax[] = generateCurve(hzx * 440, ax, size, 0);
        float datay[] = generateCurve(hzy * 440 * (float)Math.pow(2, 3.0/12), ay, size, 1);
        float dataz[] = generateCurve(hzz * 440 * (float)Math.pow(2, 5.0/12), az, size, 2);
        float data[] = new float[size];
        for(int i = 0; i < size; i++) {
            data[i] = (datax[i] + datay[i] + dataz[i]) / 3;
        }

        accAudioTrack[0].play();
        accAudioTrack[0].write(data, 0, size, AudioTrack.WRITE_NON_BLOCKING);
        */
        // 0 < ((v + mr) / 2mr) < 1
        accAudioTrack[0].play();
        accAudioTrack[0].write(generateCurve(hzx * 440, ax, size, 0), 0, size, AudioTrack.WRITE_BLOCKING);
        accAudioTrack[1].play();
        accAudioTrack[1].write(generateCurve(hzy * 440 * (float)Math.pow(2, 3.0/12), ay, size, 1), 0, size, AudioTrack.WRITE_BLOCKING);
        accAudioTrack[2].play();
        accAudioTrack[2].write(generateCurve(hzz * 440 * (float)Math.pow(2, 5.0/12), az , size, 2), 0, size, AudioTrack.WRITE_BLOCKING);

        /*
        accAudioTrack[0];
        accAudioTrack[1];
        accAudioTrack[2];
        gyroAudioTrack[0];
        gyroAudioTrack[1];
        gyroAudioTrack[2];
        */


    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        timer.cancel();
        manager.unregisterListener(this);

        for(int i = 0; i < 3; i++) {
            accAudioTrack[i].pause();
            accAudioTrack[i].flush();
            gyroAudioTrack[i].pause();
            gyroAudioTrack[i].flush();
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for(int i = 0; i < 3; i++) {
            accAudioTrack[i].pause();
            accAudioTrack[i].flush();
            accAudioTrack[i].release();
            gyroAudioTrack[i].pause();
            gyroAudioTrack[i].flush();
            gyroAudioTrack[i].release();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu");
        int title = 0;
        switch (delay) {
        case SensorManager.SENSOR_DELAY_FASTEST:
            title = R.string.menu_delay_fastest;
            break;
        case SensorManager.SENSOR_DELAY_GAME:
            title = R.string.menu_delay_game;
            break;
        case SensorManager.SENSOR_DELAY_UI:
            title = R.string.menu_delay_ui;
            break;
        case SensorManager.SENSOR_DELAY_NORMAL:
            title = R.string.menu_delay_normal;
            break;
        }
        menu.findItem(R.id.menu_delay).setTitle(title);
        menu.findItem(R.id.menu_accelerometer).setEnabled(type != Sensor.TYPE_ACCELEROMETER);
        menu.findItem(R.id.menu_gyroscope).setEnabled(type != Sensor.TYPE_GYROSCOPE);
        menu.findItem(R.id.menu_magnetic_field).setEnabled(type != Sensor.TYPE_MAGNETIC_FIELD);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        switch (item.getItemId()) {
        case R.id.menu_delay:
            Log.d(TAG, "menu_delay");
            int index = DELAYS.indexOf(delay);
            delay = DELAYS.get((index + 1) % DELAYS.size());
            break;
        case R.id.menu_accelerometer:
            type = Sensor.TYPE_ACCELEROMETER;
            typeView.setText(R.string.menu_accelerometer);
            break;
        case R.id.menu_gyroscope:
            type = Sensor.TYPE_GYROSCOPE;
            typeView.setText(R.string.menu_gyroscope);
            break;
        case R.id.menu_magnetic_field:
            type = Sensor.TYPE_MAGNETIC_FIELD;
            typeView.setText(R.string.menu_magnetic_field);
            break;
        }
        invalidateOptionsMenu();
        changeConfig();
        return super.onOptionsItemSelected(item);
    }

    private void changeConfig() {
        manager.unregisterListener(this);
        Sensor sensor = manager.getDefaultSensor(type);
        manager.registerListener(this, sensor, delay);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        rx = event.values[0];
        ry = event.values[1];
        rz = event.values[2];
        //Log.i(TAG, "x=" + rx + ", y=" + ry + ", z=" + rz);

        vx = ALPHA * vx + (1 - ALPHA) * rx;
        vy = ALPHA * vy + (1 - ALPHA) * ry;
        vz = ALPHA * vz + (1 - ALPHA) * rz;
        long ts = event.timestamp;
        rate = (int) (ts - prevTimestamp) / 1000;
        prevTimestamp = ts;
        if(rate < 0) rate = -rate;

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "onAccuracyChanged");
        this.accuracy = accuracy;
    }

    private String sensorTypeName(int sensorType) {
        try {
            Class klass = Sensor.class;
            for (Field field : klass.getFields()) {
                String fieldName = field.getName();
                if (fieldName.startsWith("TYPE_") && field.getInt(klass) == sensorType)
                    return fieldName;
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}
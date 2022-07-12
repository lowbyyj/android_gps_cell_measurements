package com.example.cellgpsv2;

import static android.telephony.CellInfo.UNAVAILABLE_LONG;
import static android.telephony.TelephonyManager.NETWORK_TYPE_LTE;
import static android.telephony.TelephonyManager.NETWORK_TYPE_NR;
import static java.lang.String.valueOf;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.telephony.CellIdentity;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    FileWriter writer;
    final int PERMISSIONS_REQUEST_CODE = 1;
    String whereDir;
    String thisFile;
    ToggleButton tb;
    TextView tvDebug;
    Timer tmr;
    String debugBuilder;

    //testEnvironmentsConfigure
    double MeasurementStartAltitudeThreshold = 1;
    double MeasurementStopAltitudeThreshold = 0.9;

    //data
    String rsrpNow = "RSRP NOT MEASURED YET";
    String RSSINow = "RSSI NOT MEASURED YET";
    String RSRQNow = "RSRQ NOT MEASURED YET";
    String altNow = "ALTITUDE NOT MEASURED YET";
    String gpsNow = "GPS NOT MEASURED YET";
    String CQINow = "CQI NOT MEASURED YET";
    String CQITableIdx = "CQI Table Idx NA";
    String CsiSinr = "CSI SINR NA";
    int cellID = 0;
    int nzCellID = 0;
    long nrCellID = 0;
    int maxCellCounter = 0;
    double altitudeCarrier = 0;
    double startingAltitude = 0;
    boolean isFileRecordFlag = false;
    //int LAC = 0;
    int LTE_NR_flag = 0;
    //0:lte, 1:NR

    //managers
    LocationManager lm;
    TelephonyManager tm;
    SensorManager sm;
    Sensor ps;
    SensorEventListener sel;
    CellIdentityLte myCID;
    CellIdentityNr myCIDnr;
    Context inThis;

    //additionalDebugParameters
    Boolean debugBool1 = false;

    //////////////////////////////PERMISSIONPARTS/////////////////////////////////////////////////////

    private void requestPermission() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE);//사용자가 이전에 거절한적이 있어도 true 반환

        if (shouldProvideRationale) {
            //앱에 필요한 권한이 없어서 권한 요청
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_CODE);
            //권한있을때.
            //오레오부터 꼭 권한체크내에서 파일 만들어줘야함
            makeDir();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //권한 허용 선택시
                    //오레오부터 꼭 권한체크내에서 파일 만들어줘야함
                    makeDir();
                } else {
                    //사용자가 권한 거절시
                    denialDialog();
                }
                return;
            }
        }
    }

    public void denialDialog() {

    }

    public void makeDir() {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String directoryName = "cellGPSv2";
        final File myDir = new File(root + "/" + directoryName);
        Log.d("cellGPSv2", root);
        if (!myDir.exists()) {
            boolean wasSuccessful = myDir.mkdir();
            Log.d("cellGPSv2", "notexitsts");
            if (!wasSuccessful) {
                System.out.println("file: was not successful.");
                Log.d("cellGPSv2", "this2");
            } else {
                System.out.println("file: first create files." + root + "/" + directoryName);
            }
        } else {
            System.out.println("file: " + root + "/" + directoryName + "already exists");
        }
        whereDir = root + "/" + directoryName;
        long now = System.currentTimeMillis(); //TODO 현재시간 받아오기
        Date date = new Date(now); //TODO Date 객체 생성
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
        String nowTime = sdf.format(date);
        nowTime = nowTime.replaceAll(":",".");
        String textFileName = "cellGPSv2 " + nowTime + ".txt";
        thisFile = textFileName;
        File file = new File(myDir + textFileName);
        Log.d("cellGPSv2", "this3");
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                if (!Environment.isExternalStorageManager()) {
                    Intent getpermission = new Intent();
                    getpermission.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(getpermission);
                    if (!file.exists()) {
                        file.createNewFile();
                        Log.d("cellGPSv2", "madein");
                    }
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    public void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    PERMISSIONS_REQUEST_CODE
            );
        }
    }
    private void repetitiveMyCheckPermissionsWork1(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
        }
//        switch (tm.getDataNetworkType()) {
//            case NETWORK_TYPE_NR:
//                Log.d("cellGPSv2", "NETWORK_TYPE_NR");
//                tvDebug.setText("NETWORK_TYPE_NR");
//                break;
//            case NETWORK_TYPE_LTE:
//                Log.d("cellGPSv2", "NETWORK_TYPE_LTE");
//                tvDebug.setText("NETWORK_TYPE_LTE");
//                break;
//            default:
//                Log.d("cellGPSv2", "UNKNOWN_NETWORK_TYPE");
//        }
    }
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    public void updateCellIds() {
        if(myCID.getCi()!=0) {
            cellID = myCID.getCi();
        }
        if(myCIDnr!=null){
            if(myCIDnr.getNci()!=UNAVAILABLE_LONG){
                nrCellID = myCIDnr.getNci();
            }
        }

//        Log.d("cellGPSv2", "updateCellIds: "+cellID);
    }

    public void mySaveText() {
        try {
            verifyStoragePermissions(this);
            File fileReal = new File(whereDir + "/" + thisFile);
            writer = new FileWriter(fileReal, true);

            long now2 = System.currentTimeMillis(); //TODO 현재시간 받아오기
            Date date2 = new Date(now2); //TODO Date 객체 생성
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd kk:mm:ss");
            String nowTime2 = sdf.format(date2);

            writer.write("[" + nowTime2 + "]\n");
            if(LTE_NR_flag == 0){
                writer.write("5G/LTE Signal Indicator = LTE\n");
            }
            else if(LTE_NR_flag==1){
                writer.write("5G/LTE Signal Indicator = NR\n");
            }
            writer.write("RSRP: [" + rsrpNow + "]\nAltitude: [" + altNow + "]\n" + gpsNow + "\n");
            writer.write("CellID: [" + nzCellID + "]\n");
            writer.write("Strength(RSSI): [" + RSSINow + "]\nQuality(RSRQ): [" + RSRQNow + "]\nIndicator(CQI): [" + CQINow + "]\n");
            if (LTE_NR_flag == 1) {
                writer.write("NRcqiTableIndex: [" + CQITableIdx + "]\n");
            }
            writer.write("\n");
            writer.flush();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void tempTask() {
        TimerTask TT = new TimerTask() {
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void run() {
                Log.d("cellGPSv2", "run is working now, mNrValid:" + debugBool1);
                //repetitiveMyCheckPermissionsWork1();


                if(altitudeCarrier>startingAltitude+MeasurementStartAltitudeThreshold && !isFileRecordFlag) {
                    requestPermission();
                    isFileRecordFlag = true;
                    //debugBuilder = "Record started, starting Altitude: " + valueOf(startingAltitude) + " m";
                    //tvDebug.setText(debugBuilder);
                    Log.d("cellGPSv2", "isFileRecordFlag = true");
                }
                if(isFileRecordFlag && altitudeCarrier<startingAltitude+MeasurementStopAltitudeThreshold){
                    isFileRecordFlag = false;
//                    debugBuilder = "Record just stopped\nStarting Altitude was: " + valueOf(startingAltitude) + " m\nAltitude Carrier value is: " +valueOf(altitudeCarrier) + " m";
                    tvDebug.setText(debugBuilder);
                    Log.d("cellGPSv2", "isFileRecordFlag = false");
                }
                if (isFileRecordFlag) {
                    int tempCellCounter = 0;
                    //debugBuilder = "";
                    if (ActivityCompat.checkSelfPermission(inThis, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    List<CellInfo> cells = tm.getAllCellInfo();
                    if(cells!=null){
                        for(CellInfo info : cells){
                            if (info instanceof CellInfoLte){
                                myCID = (((CellInfoLte) info).getCellIdentity());
                                if(myCID.getCi()!=0){
                                    nzCellID = myCID.getCi();
                                }
                                tempCellCounter++;
                                if(tempCellCounter>maxCellCounter){
                                    maxCellCounter = tempCellCounter;
                                }
                                //debugBuilder = debugBuilder + valueOf(tempCellCounter) + "-th ECI(Cell ID) : " + valueOf(myCID.getCi()) + "\n";
                            }
                            else if (info instanceof CellInfoNr){
                                myCIDnr = (CellIdentityNr) (((CellInfoNr) info).getCellIdentity());
                                if(myCIDnr.getNci()!=0){
                                    nrCellID = myCIDnr.getNci();
                                }
                                tempCellCounter++;
                                if(tempCellCounter>maxCellCounter){
                                    maxCellCounter = tempCellCounter;
                                }
                            }
                        }
                    }
                    //debug here
                    //debugBuilder = "Now recording\nStarting Altitude was: " + valueOf(startingAltitude) + " m\nAltitude Carrier value is: " +valueOf(altitudeCarrier) + " m\n";
                    if(debugBool1){
                        //debugBuilder+="NETWORK_TYPE_NR";
                    }
                    else{
                        //debugBuilder+="NETWORK_TYPE_LTE";
                    }
                    tvDebug.setText(debugBuilder);
                    //end of debug
                    updateCellIds();
                    mySaveText();
                }
                tvDebug.setText(debugBuilder);
            }
        };
        tmr = new Timer();
        tmr.schedule(TT, 0, 500);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        inThis = this;
        //TextView part
        tvDebug = (TextView)findViewById(R.id.debugText);
        //end of text view part

        //ToggleButton parts
        tb = (ToggleButton) findViewById(R.id.ssBtn);
        Log.d("cellGPSv2", "thiscreate");

        tb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isFileRecordFlag = false;
                startingAltitude = altitudeCarrier;
                try {
                    Log.d("cellGPSv2", "click");
                    if (tb.isChecked()) {
                        Log.d("cellGPSv2", "on");
                        tempTask();
                    } else {
                        tmr.cancel();
                    }
                } catch (SecurityException ex) {
                }
            }
        });//end of toggle button onClickListener

        //sensor parts
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        ps = sm.getDefaultSensor(Sensor.TYPE_PRESSURE);
        sel = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float sval = event.values[0];
                altitudeCarrier = sm.getAltitude(sm.PRESSURE_STANDARD_ATMOSPHERE, sval);
                altNow = valueOf(altitudeCarrier);
//                Log.d("cellGPSv2","altNow:" + altNow);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };
        sm.registerListener(sel, ps, sm.SENSOR_DELAY_UI);
        //end of sensor parts

        //Telephony parts
        tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
//        tm.registerTelephonyCallback();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        List<CellInfo> cells = tm.getAllCellInfo();
        if(cells!=null){
            for(CellInfo info : cells){
                if (info instanceof CellInfoLte){
//                    myCID = (((CellInfoLte) info).getCellIdentity());
                }
            }
        }
        PhoneStateListener psl = new PhoneStateListener(){
            @RequiresApi(api = Build.VERSION_CODES.Q)
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                super.onSignalStrengthsChanged(signalStrength);
                //RSRP (Reference Signal Received Power) - 단위 dBm (절대크기). - 단말에 수신되는 Reference Signal의 Power
                String strSignal = signalStrength.toString();
                debugBuilder = strSignal;
                debugBool1 = !strSignal.contains("mNr=Invalid");
                if(debugBool1){
                    LTE_NR_flag = 1;
                }
                else{
                    LTE_NR_flag = 0;
                }
                Log.d("cellGPSv2","LTE_NR_flag: " + LTE_NR_flag + ", sigNow: "+signalStrength.getCellSignalStrengths().toString());
                if (LTE_NR_flag==0) {
                    CellSignalStrengthLte lteSig = (CellSignalStrengthLte) signalStrength.getCellSignalStrengths().get(0);
                    rsrpNow = valueOf(lteSig.getRsrp());
                    RSSINow = valueOf(lteSig.getRssi());
                    RSRQNow = valueOf(lteSig.getRsrq());
                    CQINow = valueOf(lteSig.getCqi());
                }
                else if (LTE_NR_flag==1){
                    debugBuilder = signalStrength.getCellSignalStrengths().toString();
//                    CellSignalStrengthNr nrSig = (CellSignalStrengthNr) signalStrength.getCellSignalStrengths().get(0);
//                    rsrpNow = valueOf(nrSig.getCsiRsrp());
//                    RSRQNow = valueOf(nrSig.getCsiRsrq());
//                    CQINow = valueOf(nrSig.getCsiCqiReport().get(0));
//                    CQITableIdx = valueOf(nrSig.getCsiCqiTableIndex());
//                    CsiSinr = valueOf(nrSig.getCsiSinr());
                }
            }
        };
        tm.listen(psl, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        //end of Telephony parts


        //GPS parts
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationListener ls = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                double longitude = location.getLongitude();
                double latitude = location.getLatitude();
                double altitude = location.getAltitude();
                float accuracy = location.getAccuracy();
                String provider = location.getProvider();
                gpsNow = "GPS위치정보: [" + provider + "]\n위도: [" + latitude + "]\n경도: [" + longitude + "]\nmgps고도: [" + altitude + "]\n정확도: ["  + accuracy + "]";
            }
        };
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, // 등록할 위치제공자
                    0, // 통지사이의 최소 시간간격 (miliSecond)
                    0, // 통지사이의 최소 변경거리 (m)
                    ls);
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, // 등록할 위치제공자
                    0, // 통지사이의 최소 시간간격 (miliSecond)
                    0, // 통지사이의 최소 변경거리 (m)
                    ls);
        } catch(SecurityException ex){
        }
        //end of GPS part



    }//end of onCreate



    @Override
    protected void onDestroy(){
        super.onDestroy();
    }
}


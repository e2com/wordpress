package com.billcorea.wordpress;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MediaPostService extends Service {

    String TAG = "MediaPostService" ;
    File file = null ;

    private static final int MILLISINFUTURE = 1000*1000;
    private static final int COUNT_DOWN_INTERVAL = 1000 * 300; // 180 초 = 3분에 1번씩

    private CountDownTimer countDownTimer;
    ArrayList<String> fileList = null ;
    int maxCnt = 0 ;
    int rowCnt = 0 ;
    int errCnt = 0 ;

    Gson gson;
    List<Object> list;
    Map<String,Object> mapPost;
    Map<String,Object> mapId;
    Map<String,Object> mapTitle;
    String postTitle[];
    RequestQueue rQueue ;
    StringRequest request ;

    public MediaPostService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        Log.d(TAG, "");
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        unregisterRestartAlarm();
        super.onCreate();

        initData();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        startForeground(1,new Notification());

        /**
         * startForeground 를 사용하면 notification 을 보여주어야 하는데 없애기 위한 코드
         */
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        Notification notification;

        notification = new Notification.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_notifycation)
                .setContentTitle("워드프레스!!!")
                .setContentText("Catch me if you can !!!")
                .build();

        nm.notify(startId, notification);
        nm.cancel(startId);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(TAG , "onDestroy" );
        countDownTimer.cancel();

        /**
         * 서비스 종료 시 알람 등록을 통해 서비스 재 실행
         */
        registerRestartAlarm();
    }

    /**
     * 데이터 초기화
     */
    private void initData(){

        maxCnt = 0 ;
        fileList = getPathOfAllImages() ;
        countDownTimer();
        countDownTimer.start();
    }

    public void countDownTimer(){

        countDownTimer = new CountDownTimer(MILLISINFUTURE, COUNT_DOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {

                if (getWIFIStatus() && StringUtil.sToken != null) {
                    Log.i(TAG, "onTick [" + StringUtil.sToken + "]");
                    Log.d(TAG, "rowCnt=" + rowCnt + ", maxCnt=" + maxCnt) ;
                    if(rowCnt < maxCnt) {
                        Log.d(TAG, "fleName (" + rowCnt + ")=" + fileList.get(rowCnt)) ;
                        getChkSndImage(fileList.get(rowCnt)) ;
                    }

                } else {
                    Log.d(TAG, "onTick !!! ") ;
                }
            }
            public void onFinish() {

                Log.i(TAG,"onFinish");
            }
        };
    }

    public void getChkSndImage(final String imgName) {

        File n = new File(imgName);
        final String nn = n.getName() ;
        String nn1 = nn.replaceAll(" ", "_");
        Log.d(TAG, "Call URL=" + StringUtil.getMediaSearchUrl(nn)) ;

        request = new StringRequest(Request.Method.GET, StringUtil.getMediaSearchUrl(nn1), new Response.Listener<String>() {
            @Override
            public void onResponse(String s) {
                Log.d(TAG, "-----------------------------------------") ;
                Log.d(TAG, "response=///" + s + "///") ;
                Log.d(TAG, "-----------------------------------------") ;
                String strId = "" ;
                if (s.indexOf("id") < 0) {
                    Log.d(TAG, "not Found !!!" ) ;
                    try {
                        setImagePost(imgName, nn);
                    } catch (Exception e) {

                    }
                } else {
                    Log.d(TAG, "id = " + s.indexOf("id")) ;
                    rowCnt++; // 다음으로 넘어가기
                    if (rowCnt >= maxCnt) {
                        maxCnt = 0 ;
                        fileList = getPathOfAllImages() ;
                    }
                }
            }

        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                //Toast.makeText(MainWordPress.this, "Some error occurred", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Some Error Occurred !!! [" + volleyError.toString() + "]");
                errCnt++;
                if (errCnt > 5) {
                    Log.d(TAG, "errCnt=" + errCnt) ;
                    countDownTimer.cancel();
                }
            }
        }) ;

        request.setRetryPolicy(new RetryPolicy() {
            @Override
            public int getCurrentTimeout() {
                return COUNT_DOWN_INTERVAL;
            }

            @Override
            public int getCurrentRetryCount() {
                return 5;
            }

            @Override
            public void retry(VolleyError error) throws VolleyError {
                Log.e(TAG, "Some Error Occurred !!! [" + error.toString() + "]");
                errCnt++;
                if (errCnt > 5) {
                    Log.d(TAG, "errCnt=" + errCnt) ;
                    countDownTimer.cancel();
                }
            }
        });
        rQueue = Volley.newRequestQueue(getBaseContext());
        rQueue.add(request);
    }

    public void setImagePost(String imgFileName, String fName) throws Exception {

        final String imgName = fName.replaceAll(" ", "_") ;
        File iFile = new File(imgFileName) ;
        FileInputStream bImage = null ;
        try {
            bImage = new FileInputStream(iFile);
        } catch (Exception e) {

        }
        Bitmap bm = BitmapFactory.decodeStream(bImage);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100 , baos);
        byte[] b = baos.toByteArray();

        final StringRequest requestPost = new StringRequest(Request.Method.POST, StringUtil.getMediaUrl(), new Response.Listener<String>() {
            @Override
            public void onResponse(String s) {
                Log.d(TAG, "onResponse(" + s.toString() + ")") ;
                if (s.indexOf("id") < 0) {
                    Log.d(TAG, "Media Posting ERROR K !!! ID<<<") ;
                } else {
                    Log.d(TAG, "Media Posting OK !!! [" + s.toString() + "]") ;
                }
                rowCnt++; // 다음으로 넘어가기
                if (rowCnt >= maxCnt) {
                    maxCnt = 0 ;
                    fileList = getPathOfAllImages() ;
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                rowCnt++; // 다음으로 넘어가기
                if (rowCnt >= maxCnt) {
                    maxCnt = 0 ;
                    fileList = getPathOfAllImages() ;
                }
                Log.e(TAG, "Some Error Occurred !!! [" + error.toString() + "]");
            }
        }) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                Log.d(TAG, "getBody() ++++++++++++++++++++++++++++++++++++") ;
                return baos.toByteArray();
            }
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                params.put("Content-Disposition","attachment; filename=" + imgName);
                params.put("Authorization", "Bearer " + StringUtil.sToken);
                return params;
            }
        };

        requestPost.setRetryPolicy(new RetryPolicy() {
            @Override
            public int getCurrentTimeout() {
                return COUNT_DOWN_INTERVAL;
            }

            @Override
            public int getCurrentRetryCount() {
                return 5;
            }

            @Override
            public void retry(VolleyError error) throws VolleyError {
                rowCnt++; // 다음으로 넘어가기
                if (rowCnt >= maxCnt) {
                    maxCnt = 0 ;
                    fileList = getPathOfAllImages() ;
                }
                Log.e(TAG, "setRetryPolicy error [" + error.toString() + "]");
            }
        });

        rQueue = Volley.newRequestQueue(getBaseContext());
        VolleyLog.DEBUG = true ;
        Log.d(TAG, "Media Post request Start -------------------------------------------------------------------------") ;
        rQueue.add(requestPost);
    }

    private ArrayList<String> getPathOfAllImages()
    {
        ArrayList<String> result = new ArrayList<>();
        Uri uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME };

        Cursor cursor = getContentResolver().query(uri, projection, null, null, MediaStore.MediaColumns.DATE_ADDED + " desc");
        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
        int columnDisplayname = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME);

        int lastIndex;
        while (cursor.moveToNext())
        {
            String absolutePathOfImage = cursor.getString(columnIndex);
            String nameOfFile = cursor.getString(columnDisplayname);
            lastIndex = absolutePathOfImage.lastIndexOf(nameOfFile);
            lastIndex = lastIndex >= 0 ? lastIndex : nameOfFile.length() - 1;

            if (!TextUtils.isEmpty(absolutePathOfImage))
            {
                result.add(absolutePathOfImage);
                maxCnt++ ;
            }
        }

        for (String string : result)
        {
            Log.i(TAG, "|" + string + "|");
        }
        return result;
    }

    private void showExif(ExifInterface exif) {

        String myAttribute = "[Exif information] \n\n";

        myAttribute += getTagString(ExifInterface.TAG_DATETIME, exif); // 년:월:일 시:분:초
        myAttribute += getTagString(ExifInterface.TAG_FLASH, exif);
        myAttribute += getTagString(ExifInterface.TAG_GPS_AREA_INFORMATION, exif);
        myAttribute += getTagString(ExifInterface.TAG_GPS_LATITUDE, exif); // 위도/경도
        myAttribute += getTagString(ExifInterface.TAG_GPS_LATITUDE_REF, exif);
        myAttribute += getTagString(ExifInterface.TAG_GPS_LONGITUDE, exif);
        myAttribute += getTagString(ExifInterface.TAG_GPS_LONGITUDE_REF, exif);
        myAttribute += getTagString(ExifInterface.TAG_MAKE, exif); // 휴대폰 제조사
        myAttribute += getTagString(ExifInterface.TAG_MAKER_NOTE, exif);
        myAttribute += getTagString(ExifInterface.TAG_MODEL, exif); // 모델명
        myAttribute += getTagString(ExifInterface.TAG_ORIENTATION, exif);
        myAttribute += getTagString(ExifInterface.TAG_WHITE_BALANCE, exif);
        myAttribute += getTagString(ExifInterface.TAG_IMAGE_DESCRIPTION, exif);
        myAttribute += getTagString(ExifInterface.TAG_IMAGE_UNIQUE_ID, exif); // 번호
        myAttribute += getTagString(ExifInterface.TAG_IMAGE_LENGTH, exif);
        myAttribute += getTagString(ExifInterface.TAG_IMAGE_WIDTH, exif);

        Log.d(TAG, myAttribute) ;
    }

    @NonNull
    static String getMimeType(@NonNull File file) {
        String type = null;
        final String url = file.toString();
        final String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null) {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        if (type == null) {
            type = "image/*"; // fallback type. You might set it to */*
        }
        return type;
    }

    private String getTagString(String tag, ExifInterface exif) {
        return (tag + " : " + exif.getAttribute(tag) + "\n");
    }

    /**
     * wifi 사용중인지 확인
     * @return
     */
    public boolean getWIFIStatus() {
        ConnectivityManager cm = (ConnectivityManager) getBaseContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
        boolean isWiFi = false ;
        if (isConnected) {
            isWiFi = activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
        }
        return isWiFi ;
    }

    /**
     * 알람 매니져에 서비스 등록
     */
    private void registerRestartAlarm(){

        Log.d(TAG, "registerRestartAlarm" );
        Intent intent = new Intent(MediaPostService.this,RestartService.class);
        intent.setAction("ACTION.RESTART.PersistentService");
        PendingIntent sender = PendingIntent.getBroadcast(MediaPostService.this,0,intent,0);

        long firstTime = SystemClock.elapsedRealtime();
        firstTime += 1*1000;

        AlarmManager alarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);

        /**
         * 알람 등록
         */
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,firstTime,1*1000,sender);

    }

    /**
     * 알람 매니져에 서비스 해제
     */
    private void unregisterRestartAlarm() {

        Log.i(TAG, "unregisterRestartAlarm");

        Intent intent = new Intent(MediaPostService.this, RestartService.class);
        intent.setAction("ACTION.RESTART.PersistentService");
        PendingIntent sender = PendingIntent.getBroadcast(MediaPostService.this, 0, intent, 0);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        /**
         * 알람 취소
         */
        alarmManager.cancel(sender);

    }
        //출처: http://iw90.tistory.com/155 [woong's]
}

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
import android.widget.Toast;

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

    private static final int MILLISINFUTURE = 3000 * 5 * 60 * 1000;  // 3000개 파일 * 5분 : Timer 의 제한 시간
    private static final int COUNT_DOWN_INTERVAL = 3000;  // loop 재실행 시간 3초에 1회
    private static final int SEND_IMAGE_INTERVAL = 500000; // image upload 대기시간 500초 까지 대기

    private CountDownTimer countDownTimer;
    ArrayList<String> fileList = null ;
    int maxCnt = 0 ;
    int rowCnt = 0 ;
    int errCnt = 0 ;

    boolean sendIng = false ;

    Gson gson;
    List<Object> list;
    Map<String,Object> mapPost;
    Map<String,Object> mapId;
    Map<String,Object> mapTitle;
    Map<String, String> picInfo ;
    String postTitle[];
    RequestQueue rQueue ;
    StringRequest request ;

    DBHandler dbHandler = null ;

    public MediaPostService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        Log.d(TAG, "onBind() ... ");
        // throw new UnsupportedOperationException("Not yet implemented");
        return null ;
    }

    @Override
    public void onCreate() {
        unregisterRestartAlarm();
        super.onCreate();

        initData();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {
            Log.d(TAG, "----------------------------  onStartCommand() Start !!!") ;
            startForeground(1, new Notification());

            /**
             * startForeground 를 사용하면 notification 을 보여주어야 하는데 없애기 위한 코드
             */
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification notification;

            notification = new Notification.Builder(getApplicationContext())
                    .setSmallIcon(R.drawable.ic_notifycation)
                    .setContentTitle("")
                    .setContentText("Have a Nice Day !!!")
                    .setPriority(Notification.PRIORITY_MIN) //요 부분이 핵심입니다. MAX가 아닌 MIN을 줘야 합니다.
                    .build();

            nm.notify(startId, notification);
            nm.cancel(startId);

            Log.d(TAG, "----------------------------  onStartCommand() Notify End") ;
        } catch (Exception e) {

        } finally {

        }
        return super.onStartCommand(intent, flags, startId);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "--------------------------------------------------------------------------------------------------- onDestroy()") ;
        countDownTimer.cancel();

        /**
         * 서비스 종료 시 알람 등록을 통해 서비스 재 실행
         */
        registerRestartAlarm();
        Log.d(TAG, "--------------------------------------------------------------------------------------------------- registerRestartAlarm()") ;
    }

    /**
     * 데이터 초기화
     */
    private void initData(){

        maxCnt = 0 ;
        picInfo = new HashMap<String, String>(); // 사진정보 취합을 위해서 초기화
        dbHandler = new DBHandler(getApplicationContext()) ; // 정보 저장을 위해서 초기화

        countDownTimer();
        countDownTimer.start();
    }

    /**
     * count down 처리
     */
    public void countDownTimer(){

        /**
         * count down 을 합니다. (시간동안 유지, 이시간 간격으로 1/1000 초 단위로 표기를 해야 합니다.)
         */
        countDownTimer = new CountDownTimer(MILLISINFUTURE, COUNT_DOWN_INTERVAL) {
            public void onTick(long millisUntilFinished) {

                if (getWIFIStatus() && StringUtil.sToken != null) {
                    if (maxCnt < 1) {
                        fileList = getPathOfAllImages() ;
                    }
                    Log.d(TAG, "rowCnt=" + rowCnt + ", maxCnt=" + maxCnt) ;
                    if(rowCnt < maxCnt) {
                        try {
                            dbHandler = DBHandler.open(getApplicationContext());
                            File n = new File(fileList.get(rowCnt));
                            final String nn = n.getName();
                            String nn1 = nn.replaceAll(" ", "_");
                            Log.d(TAG, "fleName (" + rowCnt + ")=" + fileList.get(rowCnt) + ">>>>" + nn1);
                            if (!"Y".equals(dbHandler.getSendTy(fileList.get(rowCnt), nn1))) {
                                try {
                                    if (!sendIng) { // 전송중이 아닐때만
                                        setImagePost(fileList.get(rowCnt), nn1);
                                    }
                                    //dbHandler.updateSendTy(fileList.get(rowCnt), nn1,"Y");
                                } catch (Exception e) {

                                }
                            } else {
                                rowCnt++; // 전송한 파일 이면 다음으로 넘어가게 하기 위해서.
                            }
                        } catch (Exception e) {

                        } finally {
                            dbHandler.close();
                        }
                        //getChkSndImage(fileList.get(rowCnt)) ;
                    }

                } else {
                    Log.d(TAG, "onTick !!! WifiStatus=(" + getWIFIStatus() + "), Token=" + StringUtil.sToken) ;
                    if (StringUtil.sToken == null && !sendIng) {
                        StringUtil.sToken = getToken() ;
                    }
                }
            }
            public void onFinish() {

                Log.i(TAG,"onFinish");
            }
        };
    }

    public void setImagePost(String imgFileName, String fName) {

        final String imgName = fName.replaceAll(" ", "_") ;
        final String sndFullPath = imgFileName ;
        final String sndFileName = imgName ;
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
                    String rDescription = s.substring(s.indexOf("description"), s.length() - 1);
                    rDescription = rDescription.substring(rDescription.indexOf("rendered"), rDescription.length() - 1);
                    rDescription = rDescription.substring(rDescription.indexOf(":") + 1, rDescription.indexOf("}"));
                    rDescription = rDescription.replaceAll("\"", "") ;
                    rDescription = rDescription.replaceAll("\\\\", "") ;
                    Log.d(TAG, "rDescription=[" + rDescription + "]");
                    Toast.makeText(getApplicationContext(), "image Post OK!!!", Toast.LENGTH_LONG).show();
                    try {
                        dbHandler = DBHandler.open(getApplicationContext());
                        Log.d(TAG, sndFullPath + ">>>>>" + sndFileName);
                        dbHandler.updateSendTy(sndFullPath, sndFileName, "Y", rDescription);
                    } catch (Exception e) {

                    } finally {
                        dbHandler.close();
                    }

                    Boolean rSw = setPostText(sndFullPath, sndFileName, rDescription) ;
                }
                rowCnt++; // 다음으로 넘어가기
                if (rowCnt >= maxCnt) {
                    maxCnt = 0 ;
                    fileList = getPathOfAllImages() ;
                }
                sendIng = false ; // 전송이 완료됨을 표시
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
                sendIng = false ;
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
                return SEND_IMAGE_INTERVAL;
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
                sendIng = false ;
            }
        });

        rQueue = Volley.newRequestQueue(getBaseContext());
        VolleyLog.DEBUG = true ;
        Log.d(TAG, "Media Post request Start -------------------------------------------------------------------------") ;
        sendIng = true ; // 전송중 이라고 설정
        rQueue.add(requestPost);
    }

    /**
     * 문자로 Post 하기
     * @param pFullPath  : 전체 경로
     * @param pFileName  : 파일 이름
     * @param pContents  : 내용
     * @return : 잘 되면 true 반환
     */
    public boolean setPostText(String pFullPath, String pFileName, String pContents) {
        boolean bResult = false ;

        final String fpTitle = pFileName;
        String fpContents = pContents ;
        String  tag_datetime = "" ; // 2018-05-01T00:33:44
        String  tag_gps_latitude = "" ;
        String  tag_make = "" ;
        String  tag_model = "";

        try {
            // DB 에서 사진에 대한 정보를 취득 한다.
            dbHandler = DBHandler.open(getApplicationContext());
            Cursor rs = dbHandler.selectFileName(pFullPath, pFileName);
            rs.isBeforeFirst();
            if (rs.moveToNext()) {
                tag_datetime = rs.getString(7);
                tag_gps_latitude = rs.getString(3);
                tag_make = rs.getString(4);
                tag_model = rs.getString(5);

                fpContents = fpContents + "<br>" + tag_datetime
                        + "<br>" + tag_make
                        + "<br>" + tag_model
                        + "<br>" + tag_gps_latitude
                        + "<br>";
            }
        } catch (Exception e) {

        } finally {
            dbHandler.close();
        }

        final String ffpContents = fpContents ;

        StringRequest requestPost = new StringRequest(Request.Method.POST, StringUtil.getPostUrl(), new Response.Listener<String>() {
            @Override
            public void onResponse(String s) {
                //Log.d(TAG, "respone(" + s.toString() + ")") ;
                String pageName = "" ;
                try {
                    JSONObject obj = new JSONObject(s.toString());
                    pageName = obj.getJSONObject("id").toString();
                } catch (Exception e) {
                    pageName = "" ;
                }
                Log.d(TAG, "Posting OK !!! ID<<<" + pageName + ">>> Res[" + s.toString() + "]") ;
                Toast.makeText(getApplicationContext(), "Posting OK !!! ID=" + pageName, Toast.LENGTH_LONG).show();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(getApplicationContext(), "Some error occurred", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Some Error Occurred !!! [" + error.toString() + "]");

            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("status","publish");
                try {
                    params.put("title", fpTitle);
                    params.put("content",ffpContents);
                } catch (Exception e) {
                    params.put("title", "Post Data Error !!!");
                    params.put("content","Test !!!");
                }
                params.put("categories","4");
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                //params.put("Content-Type", "application/json");
                Log.d(TAG, "Post Token[" + StringUtil.sToken + "]");
                params.put("Authorization", "Bearer " + StringUtil.sToken);
                return params;
            }
        };

        requestPost.setRetryPolicy(new RetryPolicy() {
            @Override
            public int getCurrentTimeout() {
                return 300000;
            }

            @Override
            public int getCurrentRetryCount() {
                return 5;
            }

            @Override
            public void retry(VolleyError error) throws VolleyError {
                Log.e(TAG, "Some Error Occurred !!! [" + error.toString() + "]");
            }
        });

        rQueue = Volley.newRequestQueue(getBaseContext());
        VolleyLog.DEBUG = true ;
        Log.d(TAG, "Text Post request Start -------------------------------------------------------------------------") ;
        rQueue.add(requestPost);

        return bResult ;
    }
    /**
     * 보관하고 있는 이미지 파일 전체다 찾기 : SDCARD 에 있는 것들도 한번에 찾아옴
     * @return
     */
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

        int nCnt = 0 ;
        for (String string : result)
        {
            Log.i(TAG, "|" + string + "|");
            File n = new File(string);
            final String nn = n.getName() ;
            String nn1 = nn.replaceAll(" ", "_");
            try {

                nCnt++ ;
                Log.d("DBHandler", "(" + maxCnt + "," + nCnt + ")" + "path=" + string ) ;
                try {
                    dbHandler = DBHandler.open(getApplicationContext());
                    if (dbHandler.chkFileExist(string, nn1)) {
                        // 이미 등록된 파일은 전송을 했다고 봄
                        // dbHandler.updateSendTy(string, nn1, "N");
                    } else {
                        ExifInterface exif = new ExifInterface(string);
                        picInfo.clear();
                        showExif(exif);
                        picInfo.put("fullpath", string);
                        picInfo.put("filename", nn1);
                        picInfo.put("send_ty", "N"); //  처음에는 전송하지 않음으로 설정
                        Log.d(TAG, "insert=" + string + "/" + nn1);
                        dbHandler.insertMapData(picInfo);
                    }
                } catch (Exception e) {

                } finally {
                    dbHandler.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Some Error=" + e.toString()) ;
            }

        }
        return result;
    }

    private void showExif(ExifInterface exif) {

//        String myAttribute = "[Exif information] \n\n";
//
//        myAttribute += getTagString(ExifInterface.TAG_DATETIME, exif); // 년:월:일 시:분:초
//        myAttribute += getTagString(ExifInterface.TAG_FLASH, exif);
//        myAttribute += getTagString(ExifInterface.TAG_GPS_AREA_INFORMATION, exif);
//        myAttribute += getTagString(ExifInterface.TAG_GPS_LATITUDE, exif); // 위도/경도
//        myAttribute += getTagString(ExifInterface.TAG_GPS_LATITUDE_REF, exif);
//        myAttribute += getTagString(ExifInterface.TAG_GPS_LONGITUDE, exif);
//        myAttribute += getTagString(ExifInterface.TAG_GPS_LONGITUDE_REF, exif);
//        myAttribute += getTagString(ExifInterface.TAG_MAKE, exif); // 휴대폰 제조사
//        myAttribute += getTagString(ExifInterface.TAG_MAKER_NOTE, exif);
//        myAttribute += getTagString(ExifInterface.TAG_MODEL, exif); // 모델명
//        myAttribute += getTagString(ExifInterface.TAG_ORIENTATION, exif);
//        myAttribute += getTagString(ExifInterface.TAG_WHITE_BALANCE, exif);
//        myAttribute += getTagString(ExifInterface.TAG_IMAGE_DESCRIPTION, exif);
//        myAttribute += getTagString(ExifInterface.TAG_IMAGE_UNIQUE_ID, exif); // 번호
//        myAttribute += getTagString(ExifInterface.TAG_IMAGE_LENGTH, exif); // 크기
//        myAttribute += getTagString(ExifInterface.TAG_IMAGE_WIDTH, exif);  // 넓이

        picInfo.put("tag_gps_latitude", getTagString(ExifInterface.TAG_GPS_LATITUDE, exif));
        picInfo.put("tag_make", getTagString(ExifInterface.TAG_MAKE, exif));
        picInfo.put("tag_model", getTagString(ExifInterface.TAG_MODEL, exif));
        picInfo.put("tag_image_unique_id", getTagString(ExifInterface.TAG_IMAGE_UNIQUE_ID, exif));
        picInfo.put("tag_datetime", getTagString(ExifInterface.TAG_DATETIME, exif));
        picInfo.put("tag_image_length", getTagString(ExifInterface.TAG_IMAGE_LENGTH, exif));
        picInfo.put("tag_image_width", getTagString(ExifInterface.TAG_IMAGE_WIDTH, exif));

       // Log.d(TAG, myAttribute) ;
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
     * 토큰 얻어 오기
     * @return
     */
    public String getToken() {

        StringRequest requestPost = new StringRequest(Request.Method.POST, StringUtil.getJWTurl(), new Response.Listener<String>() {
            @Override
            public void onResponse(String s) {
                //Log.d(TAG, "respone(" + s.toString() + ")") ;
                try {
                    JSONObject obj = new JSONObject(s);
                    StringUtil.sToken = obj.get("token").toString();
                } catch (Exception e) {
                    StringUtil.sToken = "" ;
                }
                Log.d(TAG, "getToken<<" + StringUtil.sToken + ">>>") ;
                sendIng = false ;
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Some Error Occurred !!! [" + error.toString() + "]");
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("username",StringUtil.sUearName);
                params.put("password",StringUtil.sPassword);
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                //params.put("username",StringUtil.sUearName);
                //params.put("password",StringUtil.sPassword);
                return params;
            }
        };
        requestPost.setRetryPolicy(new RetryPolicy() {
            @Override
            public int getCurrentTimeout() {
                return 50000;
            }

            @Override
            public int getCurrentRetryCount() {
                return 5;
            }

            @Override
            public void retry(VolleyError error) throws VolleyError {
                Log.e(TAG, "Some Error Occurred !!! [" + error.toString() + "]");
            }
        });

        RequestQueue rQueue = Volley.newRequestQueue( getApplicationContext() );
        rQueue.add(requestPost);
        sendIng = true ;
        return StringUtil.sToken;
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

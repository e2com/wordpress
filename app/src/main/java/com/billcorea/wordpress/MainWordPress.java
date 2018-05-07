package com.billcorea.wordpress;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
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

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class MainWordPress extends AppCompatActivity {

    String TAG = "MainWordPress" ;

    ArrayList<String> fileList = null ;
    List<Object> list;
    Gson gson;
    ProgressDialog progressDialog;
    ListView postList;
    Map<String,Object> mapPost;
    Map<String,Object> mapTitle;
    Map<String, Object> mapToken;
    Map<String, String> picInfo ;
    int postID;
    String postTitle[];

    String sToken = "" ;
    File file = null ;

    boolean bFTPConnect = false ;
    boolean bSendFTP = false ;
    String rootFD = "" ;

    RequestQueue rQueue ;
    DBHandler dbHandler = null ;

    private Intent intent;
    private MediaPostService mediaPostService ;
    private RestartService restartService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_wordpress);

        checkFunction() ; // 권한 획득

        dbHandler = new DBHandler(MainWordPress.this);

        postList = (ListView)findViewById(R.id.postList);
        progressDialog = new ProgressDialog(MainWordPress.this);
        progressDialog.setMessage("Loading...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.show();

        mediaPostService = new MediaPostService();
        restartService = new RestartService();

        sToken = getToken() ;

        // 죽지 않도록 등록 하기
        PackageManager pm = this.getPackageManager() ;
        ComponentName cm = new ComponentName("com.billcorea.wordpress", "com.billcorea.wordpress.RestartService");
        pm.setComponentEnabledSetting(cm, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        // 실행중이 아니라면 다시 실행 하기
        if (!isServiceRunningCheck()) {
            intent = new Intent(MainWordPress.this, MediaPostService.class);
            startService(intent);
        }
        IntentFilter intentFilter = new IntentFilter("com.billcorea.wordpress.MediaPostService");
        registerReceiver(restartService, intentFilter);

        // 화면 초기화 하면서 Post 조회
        getPostListView() ;

        postList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mapPost = (Map<String,Object>)list.get(position);
                postID = ((Double)mapPost.get("id")).intValue();

                Intent intent = new Intent(getApplicationContext(),Post.class);
                intent.putExtra("id", ""+postID);
                startActivity(intent);
            }
        });

        // 이 경로는 내 폰에서 만 가능한 경로임.
        rootFD = "/storage/572B-3465" ;
        Log.d(TAG, "rootFD=" + rootFD) ;

        /* ftp 로 전송해 주지 않아도 이미지 파일의 전송은 가능함. */
        utilFTP.ConnectFTP(); // 객체 선언
        new Thread(new Runnable() {
            @Override
            public void run() {
                bFTPConnect = utilFTP.ftpConnect(utilFTP.SERVER, utilFTP.ID, utilFTP.PASS, utilFTP.port) ;
                if(bFTPConnect == true) {
                    Log.d(TAG, "Connection Success");
                }
                else {
                    Log.d(TAG, "Connection failed");
                }
            }
        }).start();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.i(TAG,"onDestroy");
        //브로드 캐스트 해제
        unregisterReceiver(restartService);
    }

    /**
     * 상단 오른쪽 팝업 메뉴 구성
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ftpSend:
                if (getWIFIStatus()) {
                    if (bFTPConnect) getFileList(rootFD) ;
                } else {
                    Toast.makeText(MainWordPress.this, "WIFI 사용을 확인하세요.", Toast.LENGTH_LONG) ;
                    Log.d(TAG, "WIFI 사용 상태가 아님.") ;
                }
                return true;
            case R.id.postText:
                Log.d(TAG, "Test Postion ...");

                setPostTextExample() ;
                getPostListView() ;

                return true ;
            case R.id.imgPost:
                try {

                    File n = new File(fileList.get(0).toString());
                    final String nn = n.getName() ;
                    String nn1 = nn.replaceAll(" ", "_");
                    Log.d(TAG, "Call URL=" + StringUtil.getMediaSearchUrl(nn)) ;

                    setImagePost(fileList.get(0).toString(), nn1);

                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(TAG, "imgPost Error:" + e.toString()) ;
                }
                return true ;
            case R.id.backupData:
                if (isExternalStorageAvail()) {
                    new MainWordPress.ExportDatabaseTask().execute();
                    SystemClock.sleep(500);
                } else {
                    Toast.makeText(MainWordPress.this,
                            "Backup이 안되요...", Toast.LENGTH_SHORT)
                            .show();
                }
                return true ;
            case R.id.help:
                // showHelp();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     *  Text Post 연습으로 등록 하기
     */
    public void setPostTextExample() {

        final StringRequest requestPost = new StringRequest(Request.Method.POST, StringUtil.getPostUrl(), new Response.Listener<String>() {
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
                Toast.makeText(MainWordPress.this, "Posting OK !!! ID=" + pageName, Toast.LENGTH_LONG).show();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(MainWordPress.this, "Some error occurred", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Some Error Occurred !!! [" + error.toString() + "]");

                sToken = getToken() ;

                //error.printStackTrace();
                //onErrorResponse(error);
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("status","publish");
                try {
                    params.put("title", "Post Test...");
                    params.put("content","publish test");
                } catch (Exception e) {
                    params.put("title", "Test1");
                    params.put("content","Test1");
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

        rQueue = Volley.newRequestQueue(MainWordPress.this);
        VolleyLog.DEBUG = true ;
        rQueue.add(requestPost);
    }

    /**
     * 화면의 ListView 에 post 조회하기
     */
    public void getPostListView() {

        final StringRequest request = new StringRequest(Request.Method.GET, StringUtil.getUrl(), new Response.Listener<String>() {
            @Override
            public void onResponse(String s) {
                gson = new Gson();
                list = (List) gson.fromJson(s, List.class);
                postTitle = new String[list.size()];

                for(int i=0;i<list.size();++i){
                    mapPost = (Map<String,Object>)list.get(i);
                    mapTitle = (Map<String, Object>) mapPost.get("title");
                    postTitle[i] = (String) mapTitle.get("rendered");
                }

                postList.setAdapter(new ArrayAdapter(MainWordPress.this,android.R.layout.simple_list_item_1,postTitle));
                progressDialog.dismiss();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                //Toast.makeText(MainWordPress.this, "Some error occurred", Toast.LENGTH_LONG).show();
                Log.d(TAG, "Some Error Occurred !!! [" + volleyError.toString() + "]");
            }
        });

        request.setRetryPolicy(new DefaultRetryPolicy(300000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES,  DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        rQueue = Volley.newRequestQueue(MainWordPress.this);
        rQueue.add(request);
    }

    /**
     * 외장 MicroSD Card 사용 가능 한가?
     * @return
     */
    private boolean isExternalStorageAvail() {
        return Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    }

    /**
     * 백업 하기
     */
    private class ExportDatabaseTask extends AsyncTask<Void, Void, Boolean> {
        private final ProgressDialog dialog = new ProgressDialog(MainWordPress.this);

        // can use UI thread here
        @Override
        protected void onPreExecute() {
            dialog.setMessage("Backup 하기...");
            dialog.show();
        }

        // automatically done on worker thread (separate from UI thread)
        @Override
        protected Boolean doInBackground(final Void... args) {

            File dbFile = new File(Environment.getDataDirectory() + "/data/com.billcorea.wordpress/databases/MY_WORDPRESS");
            File exportDir = new File(Environment.getExternalStorageDirectory(), "MY_WORDPRESS");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            File file = new File(exportDir, dbFile.getName());

            try {
                file.createNewFile();
                FileUtil.copyFile(dbFile, file);
                return true;
            } catch (IOException e) {
                return false;
            }

        }

        // can use UI thread here
        @Override
        protected void onPostExecute(final Boolean success) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            if (success) {
                Toast.makeText(MainWordPress.this, "백업 했다", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainWordPress.this, "백업 안됨", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 파일 받아서 media 에 posting 하기
     * @param imgFileName
     * @param fName
     * @throws Exception
     */
    public void setImagePost(String imgFileName, String fName) throws Exception {

        final String imgName = fName ;
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
                String pageName = "" ;
                try {
                    JSONObject obj = new JSONObject(s.toString());
                    pageName = obj.getJSONObject("id").toString();
                } catch (Exception e) {
                    pageName = "" ;
                }
                Log.d(TAG, "Media Posting OK !!! ID<<<" + pageName + ">>> Res[" + s.toString() + "]") ;
                Toast.makeText(MainWordPress.this, "Posting OK !!! ID=" + pageName, Toast.LENGTH_LONG).show();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(MainWordPress.this, "Some error occurred", Toast.LENGTH_LONG).show();
                Log.e(TAG, "Some Error Occurred !!! [" + error.toString() + "]");

                sToken = getToken() ;

                //error.printStackTrace();
                //onErrorResponse(error);
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
                return 5000000;
            }

            @Override
            public int getCurrentRetryCount() {
                return 5;
            }

            @Override
            public void retry(VolleyError error) throws VolleyError {
                Log.e(TAG, "retry Some Error Occurred !!! [" + error.toString() + "]");
            }
        });

        rQueue = Volley.newRequestQueue(MainWordPress.this);
        VolleyLog.DEBUG = true ;
        Log.d(TAG, "Media Post request Start -------------------------------------------------------------------------") ;
        rQueue.add(requestPost);
    }

    public static String getBase64String( String fileFullPath ) throws Exception{

        String content = "" ;

        if( fileFullPath.length() > 0 )
        {
            String filePathName = fileFullPath;
            String fileExtName = filePathName.substring( filePathName.lastIndexOf(".") + 1);

            FileInputStream inputStream = null;
            ByteArrayOutputStream byteOutStream = null;

            try
            {
                File file = new File( filePathName );

                if( file.exists() )
                {
                    inputStream = new FileInputStream( file );
                    byteOutStream = new ByteArrayOutputStream();

                    int len = 0;
                    byte[] buf = new byte[1024];
                    while( (len = inputStream.read( buf )) != -1 ) {
                        byteOutStream.write(buf, 0, len);
                    }

                    byte[] fileArray = byteOutStream.toByteArray();
                    content = new String( Base64.encodeToString( fileArray, Base64.DEFAULT) ); //  Base64.encodeToString(data, Base64.DEFAULT);

                    String changeString = "data:image/"+ fileExtName +";base64, "+ content;
                    content = content.replace(content, changeString);
                }
            }
            catch( IOException e)
            {
                e.printStackTrace();
            }
            finally
            {
                inputStream.close();
                byteOutStream.close();
            }
        }

        return content;
    }

    public void getFileList(String filePath) {
        file = new File(filePath) ;
        File list[] = file.listFiles() ;
        for (int i = 0 ; i < list.length ; i++) {
            if (list[i].isDirectory()) {
                getFileList(list[i].toString()) ;
            } else {
                if (list[i].getName().indexOf(".jpg") > -1) {
                    Log.d(TAG, list[i].getPath());

                    String mimeType = getMimeType(new File(list[i].getPath().toString()));
                    String filename = list[i].getPath();
                    Log.d(TAG, "mimeType=" + mimeType) ;
                    try {
                        ExifInterface exif = new ExifInterface(filename);
                        showExif(exif);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    putFTPSend(list[i].getPath(), list[i].getName());
                }
            }
        }
    }

    public boolean putFTPSend(String path, String fName) {
        final String strPath = path ;
        final String strfName = fName ;

        boolean bResult = false ;

        String strDate = getUploadYM() ;

        final String strDefaultDir = utilFTP.mDefaultBaseDirectory + strDate;
        if (bFTPConnect && getWIFIStatus()) {
            Thread sendFileftp = new Thread()
            {
                public void run() {
                    Log.d(TAG, "SendFTP ----------------------------------------") ;
                    if(bFTPConnect) {
                        Log.d(TAG, "SendFTP Connect OK----------------------------------------") ;
                        bSendFTP = utilFTP.ftpUploadFile(strPath, strfName, strDefaultDir);
                    }
                }
            };
            sendFileftp.start();
            try {
                sendFileftp.join();
                Log.d(TAG, "FTP Send Join OK!!! -------------------------------") ;
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (bSendFTP) {
                Log.d(TAG, "Send OK=" + strfName);
                bResult = true ;
            } else {
                Log.d(TAG, "Send Error=" + strfName);
            }
        } else {
            Log.d(TAG, "WIFI 로 FTP 를 사용할 수 없음.") ;
            Toast.makeText(MainWordPress.this, "WIFI 로 FTP 를 사용할 수 없음.", Toast.LENGTH_LONG);
        }
        return bResult ;
    }

    public String getUploadYM() {

        DateFormat df = new SimpleDateFormat("/YYYY/MM");
        String strValue = df.format(Calendar.getInstance().getTime());
        return strValue ;
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
     * 출처: http://mainia.tistory.com/1179 [녹두장군 - 상상을 현실로]
     * @param exif
     */
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

        picInfo.clear();
        picInfo.put("tag_gps_latitude", getTagString(ExifInterface.TAG_GPS_LATITUDE, exif));
        picInfo.put("tag_make", getTagString(ExifInterface.TAG_MAKE, exif));
        picInfo.put("tag_model", getTagString(ExifInterface.TAG_MODEL, exif));
        picInfo.put("tag_image_unique_id", getTagString(ExifInterface.TAG_IMAGE_UNIQUE_ID, exif));
        picInfo.put("tag_datetime", getTagString(ExifInterface.TAG_DATETIME, exif));
        picInfo.put("tag_image_length", getTagString(ExifInterface.TAG_IMAGE_LENGTH, exif));
        picInfo.put("tag_image_width", getTagString(ExifInterface.TAG_IMAGE_WIDTH, exif));

        // Log.d(TAG, myAttribute) ;
    }

    /**
     *     출처: http://mainia.tistory.com/1179 [녹두장군 - 상상을 현실로]
     * @param tag
     * @param exif
     * @return
     */
    private String getTagString(String tag, ExifInterface exif) {
        return (tag + " : " + exif.getAttribute(tag) + "\n");
    }

    public void checkFunction(){
        int permissioninfo = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if(permissioninfo == PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this,"SDCard 쓰기 권한 있음",Toast.LENGTH_SHORT).show();
        }else{
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},100);

            }else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},100);
            }
        }

        permissioninfo = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE);
        if(permissioninfo == PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this,"WIFI Status 얻기 권한 있음",Toast.LENGTH_SHORT).show();
        }else{
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.ACCESS_NETWORK_STATE)){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_NETWORK_STATE},101);

            }else{
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_NETWORK_STATE},101);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        String str = null;
        if(requestCode == 100){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                str = "SD Card 쓰기권한 승인";
            else str = "SD Card 쓰기권한 거부";
            Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
        } else if(requestCode == 101) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                str = "WIFI Status 얻기 권한 승인";
            else str = "WIFI Status 얻기 권한 거부";
            Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
        }
    }

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

        RequestQueue rQueue = Volley.newRequestQueue( MainWordPress.this );
        rQueue.add(requestPost);
        return StringUtil.sToken;
    }

    /**
     * 서비스 살아 있는 지 확인
     * @return
     */
    public boolean isServiceRunningCheck() {
        ActivityManager manager = (ActivityManager) this.getSystemService(Activity.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ("com.billcorea.wordpress.MediaPostService".equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        return true;
    }
}
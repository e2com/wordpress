package com.billcorea.wordpress;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.Map;

public class DBHandler {

	private DBHelper helper;
	private SQLiteDatabase db;

	String TAG = "DBHandler" ;
	
	public DBHandler(Context ctx) {
		helper = new DBHelper(ctx);
		db = helper.getWritableDatabase(); 
	}
	public static DBHandler open(Context ctx) throws SQLException{
		DBHandler handler = new DBHandler(ctx);
		return handler;
	}
	
	public void close(){
		helper.close();
	}

	/**
	 * 이미지 정보 저장
	 * @param picInfo
	 * @return
	 */
	public long insertMapData(Map<String, String> picInfo)
	{
		ContentValues values = new ContentValues();
		values.put("fullpath", picInfo.get("fullpath")) ;
		values.put("filename",  picInfo.get("filename"));
		values.put("tag_gps_latitude",  picInfo.get("tag_gps_latitude"));
		values.put("tag_make",  picInfo.get("tag_make"));
		values.put("tag_model",  picInfo.get("tag_model")) ;
		values.put("tag_image_unique_id",  picInfo.get("tag_image_unique_id")) ;
		values.put("tag_datetime",  picInfo.get("tag_datetime")) ;
		values.put("tag_image_length",  picInfo.get("tag_image_length")) ;
		values.put("tag_image_width",  picInfo.get("tag_image_width")) ;
		values.put("send_ty",  picInfo.get("send_ty")) ;

		long result = db.insert("UploadImageList", null, values);

		Log.d(TAG, "insert count=<" + result + ">" + picInfo.get("fullpath") + "," + picInfo.get("filename")) ;

		return result;
	}

	/**
	 * 전송여부와 컨텐츠에 들어갈 내용 저장
	 * @param fullpath     : 전체경로
	 * @param filename     : 파일이름
	 * @param send_ty      : 전송여부
	 * @param rDescription : HTML TAG 로 구성된 문장임 (image post 후에 결과로 오는 값)
	 * @return
	 */
	public long updateSendTy(String fullpath, String filename, String send_ty, String rDescription) {
		long result = 0 ;
		ContentValues values = new ContentValues();
		values.put("send_ty", send_ty);
		values.put("real_url", rDescription);
		result = db.update("UploadImageList", values, "fullpath = '" + fullpath + "' and filename ='" + filename +"'", null );
		Log.d(TAG, "update cnt=<" + result + ">" + fullpath + "," + filename) ;
		return result ;
	}

	/**
	 * 글자 포스팅 되면 그 아이디 저장
	 * @param fullpath
	 * @param filename
	 * @param postId
	 * @return
	 */
	public long updatePostId(String fullpath, String filename, String postId) {
		long result = 0 ;
		ContentValues values = new ContentValues();
		values.put("text_post_id", postId);
		result = db.update("UploadImageList", values, "fullpath = '" + fullpath + "' and filename ='" + filename +"'", null );
		Log.d(TAG, "update cnt=<" + result + ">" + fullpath + "," + filename) ;
		return result ;
	}
	
	public Cursor selectAll(){
		String sql = "select * from UploadImageList order by _id desc" ;
		Cursor cursor = db.rawQuery(sql, null) ;
		return cursor;
	}
	
	public Cursor selectFileName(String fullpath, String filename){
		String sql = "select * from UploadImageList where fullpath = '" + fullpath + "' and filename = '" + filename + "'";
		Cursor cursor = db.rawQuery(sql, null);
		if(cursor != null){
			cursor.moveToFirst();
		}
		return cursor;
	}

	public boolean chkFileExist(String fullpath, String filename)
	{
		boolean returnValue = false ;
		String sql = "select * from UploadImageList where fullpath = '" + fullpath + "' and filename = '" + filename + "'" ;
		Cursor cursor = db.rawQuery(sql, null);
		cursor.moveToFirst();
		if(cursor.moveToNext()){
			returnValue = true ;
		}
		cursor.close();
        //Log.d(TAG, "chkFileExist(" + fullpath + "," + filename + ")=" + returnValue ) ;
		return returnValue;
	}

	public String getSendTy(String fullpath, String filename){
		String strResult = "" ;
		String sql = "select send_ty from UploadImageList where fullpath = '" + fullpath + "' and filename = '" + filename + "'" ;
		Cursor cursor = db.rawQuery(sql, null);
		if(cursor != null) {
			cursor.moveToFirst();
			if (cursor.moveToNext()) {
				strResult = cursor.getString(0);
			} else {
				strResult = "";
			}
		}
		cursor.close();
		Log.d(TAG, "getSendTy(" + fullpath + "," + filename + ")=" + strResult ) ;
		return strResult;
	}
}

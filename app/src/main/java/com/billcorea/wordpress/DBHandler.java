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
		Log.d(TAG, "updateSendTy cnt=<" + result + ">" + fullpath + "," + filename + "," + send_ty + "," + rDescription) ;
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
		Log.d(TAG, "updatePostId cnt=<" + result + ">" + fullpath + "," + filename + "=" + postId) ;
		return result ;
	}

	/**
	 * FTP 전송을 기록함
	 * @param fullpath
	 * @param filename
	 * @param send_ty : FTP 전송 여부
	 * @return
	 */
	public long updatemFtpTy(String fullpath, String filename, String send_ty) {
		long result = 0 ;
		ContentValues values = new ContentValues();
		values.put("send_ty", send_ty);
		result = db.update("UploadImageList", values, "fullpath = '" + fullpath + "' and filename ='" + filename +"'", null );
		Log.d(TAG, "updatemFtpTy cnt=<" + result + ">" + fullpath + "," + filename + "," + send_ty) ;
		return result ;
	}

	public long updateAllFTPReset() {
		long result = 0 ;
		ContentValues values = new ContentValues();
		values.put("send_ty", "Y");
		result = db.update("UploadImageList", values, "send_ty = 'M'", null );
		Log.d(TAG, "updatemFtpTy cnt=<" + result + ">" ) ;
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

	/**
	 * 현재 까지 처리된 상태에 대한 결과를 받음
	 * @return
	 */
	public String selectGetCnt()
	{
		String returnValue = "" ;
		String sql = "select count(_id) as X1, sum(case when send_ty = 'M' then 1 else 0 end) as X2, sum(case when send_ty = 'Y' then 1 else 0 end) as X3 from UploadImageList" ;
		Cursor cursor = db.rawQuery(sql, null);
		cursor.moveToFirst();
		returnValue = "총 건수=" + cursor.getInt(0) + ", FTP 완료 건수=" + cursor.getInt(1) + ", Post 완료 건수=" + cursor.getInt(2);
		returnValue += ", 남은 건수=" + (cursor.getInt(0) - cursor.getInt(1) - cursor.getInt(2));
		cursor.close();
		Log.d(TAG, "selectGetCnt()=" + returnValue) ;
		return returnValue;
	}

	/**
	 * 파일 존재여부 확인
	 * @param fullpath
	 * @param filename
	 * @return
	 */
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

	/**
	 * 전송여부 취득
	 * @param fullpath
	 * @param filename
	 * @return
	 */
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
		//Log.d(TAG, "getSendTy(" + fullpath + "," + filename + ")=" + strResult ) ;
		return strResult;
	}
}

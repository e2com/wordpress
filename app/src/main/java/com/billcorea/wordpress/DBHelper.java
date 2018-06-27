package com.billcorea.wordpress;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBHelper extends SQLiteOpenHelper{
	private static final String DB_NAME = "MY_WORDPRESS" ;
	private static final int DB_Ver = 2 ; // 2018.6.20 테이블 추가
	public DBHelper(Context context) {
		super(context, DB_NAME, null, DB_Ver);
	}
	@Override 
	public void onCreate(SQLiteDatabase db) {
		String sql = "create table UploadImageList (" +
				"_id integer primary key autoincrement, " +
				"fullpath text," +
				"filename text," +
				"tag_gps_latitude text," +
				"tag_make text," +
				"tag_model text," +
				"tag_image_unique_id text," +
				"tag_datetime text," +
				"tag_image_length text," +
				"tag_image_width text," +
				"send_ty text," +       // 파일 전송여부 기록
				"real_url," +           // 이미지 파일을 다음 post 의 contents 에 기록할 내용 (URL등의 정보)
				"text_post_id" +        // 글자 포스팅이 된 ID 저장
			")";
		db.execSQL(sql) ;
	}

	/**
	 * 2018.6.20 테이블 추가 : Version 2 : MediaPostService 에서 전송중인 상태를 표시 하기 위해서
	 * @param db
	 * @param oldVersion
	 * @param newVersion
	 */
	@Override 
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		/* 삭제하면 안됨.
		db.execSQL("drop table if exists UploadImageList") ;
		onCreate(db) ;*/
		if ( newVersion == 2) {
            String sql = "create table SendStatus (" +
					"_id integer primary key autoincrement, " +
					"rDate text," +
					"totCnt text," +
					"sndCnt text" +
					")";
			db.execSQL(sql) ;
		}

	}
	
}

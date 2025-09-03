package com.example.prompt_aeropuerto;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static DatabaseHelper instance;
    private static final String DATABASE_NAME = "BiometricDB";
    private static final int DATABASE_VERSION = 1;
    private Context context;

    // Tabla de usuarios
    private static final String TABLE_USERS = "users";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NATIONALITY = "nationality";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_TIME_TAKEN = "time_taken";
    private static final String COLUMN_FINGERPRINT = "fingerprint";
    private static final String COLUMN_FACE_DATA = "face_data";
    private static final String COLUMN_TIMESTAMP = "timestamp";

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_USERS_TABLE = "CREATE TABLE " + TABLE_USERS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NATIONALITY + " TEXT NOT NULL,"
                + COLUMN_NAME + " TEXT NOT NULL,"
                + COLUMN_TIME_TAKEN + " INTEGER,"
                + COLUMN_FINGERPRINT + " BLOB,"
                + COLUMN_FACE_DATA + " BLOB,"
                + COLUMN_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP"
                + ")";
        db.execSQL(CREATE_USERS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    public long saveUserData(String nationality, String name, long timeTaken,
                           byte[] fingerprint, byte[] faceData) {
        try {
            String encryptedNationality = EncryptionUtils.encrypt(nationality);
            String encryptedName = EncryptionUtils.encrypt(name);

            if (encryptedNationality == null || encryptedName == null) {
                throw new Exception("Error en la encriptación de datos");
            }

            SQLiteDatabase db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_NATIONALITY, encryptedNationality);
            values.put(COLUMN_NAME, encryptedName);
            values.put(COLUMN_TIME_TAKEN, timeTaken);

            if (fingerprint != null) {
                values.put(COLUMN_FINGERPRINT, fingerprint);
            }
            if (faceData != null) {
                values.put(COLUMN_FACE_DATA, faceData);
            }

            long id = db.insert(TABLE_USERS, null, values);
            db.close();
            return id;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public List<UserData> getAllUserData() {
        List<UserData> userDataList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null, null, null, null, null, null);

        if (cursor.moveToFirst()) {
            do {
                try {
                    UserData userData = new UserData();
                    userData.setId(cursor.getInt(cursor.getColumnIndex(COLUMN_ID)));

                    String encryptedNationality = cursor.getString(cursor.getColumnIndex(COLUMN_NATIONALITY));
                    String encryptedName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));

                    String decryptedNationality = EncryptionUtils.decrypt(encryptedNationality);
                    String decryptedName = EncryptionUtils.decrypt(encryptedName);

                    if (decryptedNationality != null && decryptedName != null) {
                        userData.setNationality(decryptedNationality);
                        userData.setName(decryptedName);
                        userData.setTimeTaken(cursor.getLong(cursor.getColumnIndex(COLUMN_TIME_TAKEN)));

                        byte[] fingerprintData = cursor.getBlob(cursor.getColumnIndex(COLUMN_FINGERPRINT));
                        byte[] faceData = cursor.getBlob(cursor.getColumnIndex(COLUMN_FACE_DATA));
                        userData.setFingerprintData(fingerprintData);
                        userData.setFaceData(faceData);

                        userDataList.add(userData);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // Si hay error en un registro, continuamos con el siguiente
                    continue;
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return userDataList;
    }

    public void deleteAllData() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_USERS, null, null);
        db.close();
    }

    public void deleteUserData(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_USERS, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public UserData getUserData(long id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS, null,
                COLUMN_ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);

        UserData userData = null;
        if (cursor != null && cursor.moveToFirst()) {
            try {
                userData = new UserData();
                userData.setId(cursor.getInt(cursor.getColumnIndex(COLUMN_ID)));

                String encryptedNationality = cursor.getString(cursor.getColumnIndex(COLUMN_NATIONALITY));
                String encryptedName = cursor.getString(cursor.getColumnIndex(COLUMN_NAME));

                String decryptedNationality = EncryptionUtils.decrypt(encryptedNationality);
                String decryptedName = EncryptionUtils.decrypt(encryptedName);

                if (decryptedNationality != null && decryptedName != null) {
                    userData.setNationality(decryptedNationality);
                    userData.setName(decryptedName);
                    userData.setTimeTaken(cursor.getLong(cursor.getColumnIndex(COLUMN_TIME_TAKEN)));

                    byte[] fingerprintData = cursor.getBlob(cursor.getColumnIndex(COLUMN_FINGERPRINT));
                    byte[] faceData = cursor.getBlob(cursor.getColumnIndex(COLUMN_FACE_DATA));
                    userData.setFingerprintData(fingerprintData);
                    userData.setFaceData(faceData);
                }
            } catch (Exception e) {
                e.printStackTrace();
                userData = null;
            }
        }
        if (cursor != null) {
            cursor.close();
        }
        db.close();
        return userData;
    }
}

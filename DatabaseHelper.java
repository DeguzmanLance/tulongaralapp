package tulongaral.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "tulongaral.db";
    private static final int DATABASE_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        // user table
        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE, " +
                "password TEXT, " +
                "name TEXT, " +
                "email TEXT, " +
                "is_student INTEGER, " +
                "is_tutor INTEGER)");

        // sub table
        db.execSQL("CREATE TABLE subjects (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "description TEXT)");

        // tutor sub table
        db.execSQL("CREATE TABLE tutor_subjects (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "tutor_id INTEGER, " +
                "subject_id INTEGER)");

        // slots table
        db.execSQL("CREATE TABLE slots (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "tutor_id INTEGER, " +
                "date TEXT, " +
                "time_start TEXT, " +
                "time_end TEXT, " +
                "is_available INTEGER)");

        // sesh table
        db.execSQL("CREATE TABLE sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "student_id INTEGER, " +
                "slot_id INTEGER, " +
                "status TEXT, " +
                "rating INTEGER, " +
                "tutor_hours_earned REAL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS users");
        db.execSQL("DROP TABLE IF EXISTS subjects");
        db.execSQL("DROP TABLE IF EXISTS tutor_subjects");
        db.execSQL("DROP TABLE IF EXISTS slots");
        db.execSQL("DROP TABLE IF EXISTS sessions");
        onCreate(db);
    }

    // user sign up
    public boolean addUser(String username, String password, String name, String email, int isStudent, int isTutor) {
        SQLiteDatabase db = this.getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("username", username);
        values.put("password", password);
        values.put("name", name);
        values.put("email", email);
        values.put("is_student", isStudent);
        values.put("is_tutor", isTutor);
        long result = db.insert("users", null, values);
        db.close();
        return result != -1;
    }

    // user sign in
    public boolean checkUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        android.database.Cursor cursor = db.rawQuery(
                "SELECT * FROM users WHERE username=? AND password=?",
                new String[]{username, password}
        );
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        db.close();
        return exists;
    }

    // get user by user name
    public android.database.Cursor getUser(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM users WHERE username=?", new String[]{username});
    }

    // add slot - tutor sets availability
    public boolean addSlot(int tutorId, String date, String timeStart, String timeEnd) {
        SQLiteDatabase db = this.getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("tutor_id", tutorId);
        values.put("date", date);
        values.put("time_start", timeStart);
        values.put("time_end", timeEnd);
        values.put("is_available", 1);
        long result = db.insert("slots", null, values);
        db.close();
        return result != -1;
    }

    // get avail slots
    public android.database.Cursor getAvailableSlots(int tutorId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM slots WHERE tutor_id=? AND is_available=1",
                new String[]{String.valueOf(tutorId)});
    }

    // session booking
    public synchronized boolean bookSession(int studentId, int slotId) {
        SQLiteDatabase db = this.getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("student_id", studentId);
        values.put("slot_id", slotId);
        values.put("status", "upcoming");
        values.put("rating", 0);
        values.put("tutor_hours_earned", 0);
        long result = db.insert("sessions", null, values);
        if (result != -1) {
            android.content.ContentValues slotUpdate = new android.content.ContentValues();
            slotUpdate.put("is_available", 0);
            db.update("slots", slotUpdate, "id=?", new String[]{String.valueOf(slotId)});
        }
        db.close();
        return result != -1;
    }

    // booking history for turor
    public android.database.Cursor getBookingHistory(int studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM sessions WHERE student_id=?",
                new String[]{String.valueOf(studentId)});
    }

    // cancel session
    public boolean cancelSession(int sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();

        // Get the slot time for this session
        android.database.Cursor cursor = db.rawQuery(
                "SELECT slots.date, slots.time_start FROM sessions " +
                        "JOIN slots ON sessions.slot_id = slots.id " +
                        "WHERE sessions.id=?",
                new String[]{String.valueOf(sessionId)}
        );

        if (cursor.moveToFirst()) {
            String date = cursor.getString(0);
            String timeStart = cursor.getString(1);
            cursor.close();

            // Checks if cancellation is at least 4 hours before DONT TAMPER
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
                java.util.Date sessionDate = sdf.parse(date + " " + timeStart);
                java.util.Date now = new java.util.Date();
                long diff = sessionDate.getTime() - now.getTime();
                long hoursLeft = diff / (1000 * 60 * 60);

                if (hoursLeft < 4) {
                    db.close();
                    return false; // Too late to cancel
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // cancellation
        android.content.ContentValues values = new android.content.ContentValues();
        values.put("status", "cancelled");
        int result = db.update("sessions", values, "id=?",
                new String[]{String.valueOf(sessionId)});
        db.close();
        return result > 0;
    }
}
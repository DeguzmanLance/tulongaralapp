package tulongaral.app;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SetAvailabilityActivity extends AppCompatActivity {

    // ── UI References ──────────────────────────────────────────────────────────
    private LinearLayout daysContainer;
    private TextView tvWeeklyCount;
    private FloatingActionButton fabAddDay;
    private Button btnSave;

    // ── Data ───────────────────────────────────────────────────────────────────
    /**
     * Maps day name → list of "HH:mm" slot strings for that day.
     * Uses LinkedHashMap to preserve day order (Mon → Sun).
     */
    private final Map<String, List<String>> schedule = new LinkedHashMap<>();

    private static final String[] DAYS_OF_WEEK = {
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    };

    // ── Auth ───────────────────────────────────────────────────────────────────
    private DatabaseHelper dbHelper;
    private int currentTutorId = -1;

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final String PREFS = "tulong_prefs";
    private static final String KEY_USER = "logged_in_username";

    // ──────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_availability);

        dbHelper = new DatabaseHelper(this);
        resolveCurrentTutor();

        bindViews();
        loadExistingSlots();
        renderSchedule();
        setListeners();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Looks up the logged-in user and stores their DB id. */
    private void resolveCurrentTutor() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String username = prefs.getString(KEY_USER, null);
        if (username == null) return;

        Cursor c = dbHelper.getUser(username);
        if (c != null && c.moveToFirst()) {
            currentTutorId = c.getInt(c.getColumnIndexOrThrow("id"));
            c.close();
        }
    }

    private void bindViews() {
        daysContainer  = findViewById(R.id.daysContainer);
        tvWeeklyCount  = findViewById(R.id.tvWeeklyCount);
        fabAddDay      = findViewById(R.id.fabAddDay);
        btnSave        = findViewById(R.id.btnSaveAvailability);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    /**
     * Pulls existing available slots from the DB and rebuilds the in-memory
     * schedule map so the UI reflects previously saved data.
     */
    private void loadExistingSlots() {
        if (currentTutorId == -1) return;

        Cursor c = dbHelper.getAvailableSlots(currentTutorId);
        if (c == null) return;

        int colDate  = c.getColumnIndexOrThrow("date");
        int colStart = c.getColumnIndexOrThrow("time_start");

        while (c.moveToNext()) {
            String date  = c.getString(colDate);   // "yyyy-MM-dd"
            String start = c.getString(colStart);  // "HH:mm"

            // Convert date → day-of-week name
            String dayName = dateToDayName(date);
            if (dayName == null) continue;

            schedule.computeIfAbsent(dayName, k -> new ArrayList<>()).add(start);
        }
        c.close();
    }

    /** Maps "yyyy-MM-dd" to the matching DAYS_OF_WEEK entry. */
    private String dateToDayName(String date) {
        if (date == null || date.isEmpty()) return null;
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            java.util.Date parsedDate = sdf.parse(date);
            if (parsedDate == null) return null;

            Calendar cal = Calendar.getInstance();
            cal.setTime(parsedDate);
            int dow = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun … 7=Sat
            String[] map = {"Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday"};
            return map[dow - 1];
        } catch (Exception e) {
            return null;
        }
    }

    private void setListeners() {
        fabAddDay.setOnClickListener(v -> showAddDayDialog());
        btnSave.setOnClickListener(v -> saveAllSlots());
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    /** Clears and redraws the whole schedule UI from the in-memory map. */
    private void renderSchedule() {
        daysContainer.removeAllViews();
        int total = 0;

        for (Map.Entry<String, List<String>> entry : schedule.entrySet()) {
            total += entry.getValue().size();
            daysContainer.addView(buildDayCard(entry.getKey(), entry.getValue()));
        }

        tvWeeklyCount.setText(String.valueOf(total));
    }

    /**
     * Inflates item_day_card.xml, populates it with the day's slots, and
     * wires up the Clear button and "Add Slot" chip.
     */
    private View buildDayCard(String dayName, List<String> slots) {
        View card = LayoutInflater.from(this)
                .inflate(R.layout.item_day_card, daysContainer, false);

        TextView tvDay   = card.findViewById(R.id.tvDayName);
        TextView tvCount = card.findViewById(R.id.tvSlotCount);
        View btnClear    = card.findViewById(R.id.btnClearDay);
        TextView tvClear = card.findViewById(R.id.tvClearLabel);
        FlexboxLayout flex = card.findViewById(R.id.flexSlots);

        tvDay.setText(dayName);
        updateSlotCount(tvCount, slots.size());

        // Populate slot chips
        flex.removeAllViews();
        for (String time : new ArrayList<>(slots)) {
            flex.addView(buildSlotChip(flex, dayName, time, slots, tvCount));
        }

        // "+ Add Slot" pseudo-chip
        flex.addView(buildAddSlotChip(flex, dayName, slots, tvCount));

        // Clear button
        View.OnClickListener clearListener = v -> {
            slots.clear();
            schedule.put(dayName, slots);
            renderSchedule();
            Toast.makeText(this, dayName + " cleared", Toast.LENGTH_SHORT).show();
        };
        btnClear.setOnClickListener(clearListener);
        tvClear.setOnClickListener(clearListener);

        return card;
    }

    /** Builds one active time slot chip. Tapping it removes (toggles off) the slot. */
    private View buildSlotChip(FlexboxLayout parent, String dayName,
                               String time, List<String> slots, TextView tvCount) {
        View chip = LayoutInflater.from(this)
                .inflate(R.layout.item_slot_chip, parent, false);

        bindChipTime(chip, time);

        chip.setTag("active");
        chip.setOnClickListener(v -> {
            // Toggle: remove slot on tap
            slots.remove(time);
            schedule.put(dayName, slots);
            renderSchedule();
        });

        return chip;
    }

    /** The "+ Add Slot" chip at the end of each day row. */
    private View buildAddSlotChip(FlexboxLayout parent, String dayName,
                                  List<String> slots, TextView tvCount) {
        View chip = LayoutInflater.from(this)
                .inflate(R.layout.item_slot_chip, parent, false);

        // Style as inactive / "add" chip
        chip.setBackground(AppCompatResources.getDrawable(this, R.drawable.bg_slot_chip_inactive));
        TextView amPm = chip.findViewById(R.id.tvAmPm);
        TextView time = chip.findViewById(R.id.tvTime);
        amPm.setText("");
        time.setText("+ Add\nSlot");
        time.setTextSize(11f);

        chip.setOnClickListener(v -> showTimePicker(dayName, slots));
        return chip;
    }

    /** Populates the AM/PM label and HH:MM time for a slot chip. */
    private void bindChipTime(View chip, String time) {
        TextView tvAmPm = chip.findViewById(R.id.tvAmPm);
        TextView tvTime = chip.findViewById(R.id.tvTime);

        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            String formattedTime = String.format(Locale.getDefault(), "%02d:%s",
                    hour > 12 ? hour - 12 : (hour == 0 ? 12 : hour), parts[1]);
            tvAmPm.setText(hour < 12 ? "AM" : "PM");
            tvTime.setText(formattedTime);
        } catch (Exception e) {
            tvAmPm.setText("");
            tvTime.setText(time);
        }
    }

    private void updateSlotCount(TextView tv, int count) {
        tv.setText(count + (count == 1 ? " SLOT AVAILABLE" : " SLOTS AVAILABLE"));
    }

    // ── Dialogs ────────────────────────────────────────────────────────────────

    /** Lets the tutor pick a time to add as a new slot for the given day. */
    private void showTimePicker(String dayName, List<String> slots) {
        Calendar now = Calendar.getInstance();
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String time = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
            if (!slots.contains(time)) {
                slots.add(time);
                slots.sort(null);
            }
            schedule.put(dayName, slots);
            renderSchedule();
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
    }

    /** FAB dialog: pick which day to add (only shows days not already present). */
    private void showAddDayDialog() {
        List<String> available = new ArrayList<>();
        for (String d : DAYS_OF_WEEK) {
            if (!schedule.containsKey(d)) available.add(d);
        }

        if (available.isEmpty()) {
            Toast.makeText(this, "All days already added!", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = available.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Add day")
                .setItems(items, (dialog, which) -> {
                    String chosen = items[which];
                    schedule.put(chosen, new ArrayList<>());

                    // Reorder map to match DAYS_OF_WEEK order
                    Map<String, List<String>> ordered = new LinkedHashMap<>();
                    for (String d : DAYS_OF_WEEK) {
                        if (schedule.containsKey(d)) ordered.put(d, schedule.get(d));
                    }
                    schedule.clear();
                    schedule.putAll(ordered);

                    renderSchedule();
                    // Immediately show time picker for the new day
                    showTimePicker(chosen, schedule.get(chosen));
                })
                .show();
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    /**
     * Saves all slots to SQLite via DatabaseHelper.addSlot().
     * Only inserts NEW slots (not already stored as available).
     * Uses the NEXT occurrence of each day-of-week as the date.
     */
    private void saveAllSlots() {
        if (currentTutorId == -1) {
            Toast.makeText(this, "You must be logged in as a tutor.", Toast.LENGTH_SHORT).show();
            return;
        }

        int saved = 0;
        for (Map.Entry<String, List<String>> entry : schedule.entrySet()) {
            String dayName = entry.getKey();
            String date    = nextDateForDay(dayName);  // "yyyy-MM-dd"

            for (String slotTime : entry.getValue()) {
                // Default slot length: 1 hour
                String timeEnd = addOneHour(slotTime);
                boolean ok = dbHelper.addSlot(currentTutorId, date, slotTime, timeEnd);
                if (ok) saved++;
            }
        }

        String msg = saved > 0
                ? "Availability saved! (" + saved + " slot" + (saved > 1 ? "s" : "") + ")"
                : "No new slots to save.";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    /** Returns "yyyy-MM-dd" for the next (or current) occurrence of the given weekday. */
    private String nextDateForDay(String dayName) {
        Map<String, Integer> dayMap = new LinkedHashMap<>();
        dayMap.put("Sunday",    Calendar.SUNDAY);
        dayMap.put("Monday",    Calendar.MONDAY);
        dayMap.put("Tuesday",   Calendar.TUESDAY);
        dayMap.put("Wednesday", Calendar.WEDNESDAY);
        dayMap.put("Thursday",  Calendar.THURSDAY);
        dayMap.put("Friday",    Calendar.FRIDAY);
        dayMap.put("Saturday",  Calendar.SATURDAY);

        Calendar cal = Calendar.getInstance();
        int targetDow = dayMap.getOrDefault(dayName, Calendar.MONDAY);
        while (cal.get(Calendar.DAY_OF_WEEK) != targetDow) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(cal.getTime());
    }

    /** Returns the time 1 hour after the given "HH:mm" string. */
    private String addOneHour(String time) {
        try {
            String[] p = time.split(":");
            int h = (Integer.parseInt(p[0]) + 1) % 24;
            return String.format(Locale.getDefault(), "%02d:%s", h, p[1]);
        } catch (Exception e) {
            return time;
        }
    }
}
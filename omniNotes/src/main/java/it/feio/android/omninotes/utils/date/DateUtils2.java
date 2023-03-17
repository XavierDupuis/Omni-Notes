package it.feio.android.omninotes.utils.date;

import static it.feio.android.omninotes.utils.ConstantsBase.DATE_FORMAT_SORTABLE_OLD;

import android.content.Context;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import it.feio.android.omninotes.helpers.LogDelegate;

public class DateUtils2 {
    public static String getString(long date, String format) {
        Date d = new Date(date);
        return DateUtils2.getString(d, format);
    }

    public static String getString(Date d, String format) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(d);
    }

    public static Calendar getDateFromString(String str, String format) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        try {
            cal.setTime(sdf.parse(str));
        } catch (ParseException e) {
            LogDelegate.e("Malformed datetime string" + e.getMessage());

        } catch (NullPointerException e) {
            LogDelegate.e("Date or time not set");
        }
        return cal;
    }

    public static Calendar getLongFromDateTime(String date, String dateFormat, String time,
                                               String timeFormat) {
        Calendar cal = Calendar.getInstance();
        Calendar cDate = Calendar.getInstance();
        Calendar cTime = Calendar.getInstance();
        SimpleDateFormat sdfDate = new SimpleDateFormat(dateFormat);
        SimpleDateFormat sdfTime = new SimpleDateFormat(timeFormat);
        try {
            cDate.setTime(sdfDate.parse(date));
            cTime.setTime(sdfTime.parse(time));
        } catch (ParseException e) {
            LogDelegate.e("Date or time parsing error: " + e.getMessage());
        }
        cal.set(Calendar.YEAR, cDate.get(Calendar.YEAR));
        cal.set(Calendar.MONTH, cDate.get(Calendar.MONTH));
        cal.set(Calendar.DAY_OF_MONTH, cDate.get(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, cTime.get(Calendar.HOUR_OF_DAY));
        cal.set(Calendar.MINUTE, cTime.get(Calendar.MINUTE));
        cal.set(Calendar.SECOND, 0);
        return cal;
    }

    public static Calendar getCalendar(Long dateTime) {
        Calendar cal = Calendar.getInstance();
        if (dateTime != null && dateTime != 0) {
            cal.setTimeInMillis(dateTime);
        }
        return cal;
    }

    public static String getLocalizedDateTime(Context mContext,
                                              String dateString, String format) {
        String res = null;
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        Date date = null;
        try {
            date = sdf.parse(dateString);
        } catch (ParseException e) {
            sdf = new SimpleDateFormat(DATE_FORMAT_SORTABLE_OLD);
            try {
                date = sdf.parse(dateString);
            } catch (ParseException e1) {
                LogDelegate.e("String is not formattable into date");
            }
        }

        if (date != null) {
            String dateFormatted = android.text.format.DateUtils
                    .formatDateTime(mContext, date.getTime(), android
                            .text.format.DateUtils.FORMAT_ABBREV_MONTH);
            String timeFormatted = android.text.format.DateUtils
                    .formatDateTime(mContext, date.getTime(), android
                            .text.format.DateUtils.FORMAT_SHOW_TIME);
            res = dateFormatted + " " + timeFormatted;
        }

        return res;
    }

    public static long getNextMinute() {
        return Calendar.getInstance().getTimeInMillis() + 1000 * 60;
    }

    /**
     * Returns actually set reminder if that is on the future, next-minute-reminder otherwise
     */
    public static long getPresetReminder(Long currentReminder) {
        long now = Calendar.getInstance().getTimeInMillis();
        return currentReminder != null && currentReminder > now ? currentReminder : DateUtils2.getNextMinute();
    }

    public static Long getPresetReminder(String alarm) {
        long alarmChecked = alarm == null ? 0 : Long.parseLong(alarm);
        return DateUtils2.getPresetReminder(alarmChecked);
    }
}

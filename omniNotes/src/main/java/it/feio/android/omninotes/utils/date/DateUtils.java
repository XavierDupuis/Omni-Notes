/*
 * Copyright (C) 2013-2022 Federico Iosue (federico@iosue.it)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.feio.android.omninotes.utils.date;

import static it.feio.android.omninotes.utils.ConstantsBase.DATE_FORMAT_SORTABLE_OLD;

import android.content.Context;
import it.feio.android.omninotes.OmniNotes;
import it.feio.android.omninotes.helpers.LogDelegate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.ocpsoft.prettytime.PrettyTime;


/**
 * Helper per la generazione di date nel formato specificato nelle costanti
 */
public class DateUtils extends DateUtils2 {

  private DateUtils() {
    throw new IllegalStateException("Utility class");
  }


  public static boolean is24HourMode(Context mContext) {
    Calendar c = Calendar.getInstance();
    String timeFormatted = android.text.format.DateUtils
        .formatDateTime(mContext, c.getTimeInMillis(), android
            .text.format.DateUtils.FORMAT_SHOW_TIME);
    return !timeFormatted.toLowerCase().contains("am") && !timeFormatted.toLowerCase()
        .contains("pm");
  }


  public static boolean isSameDay(long date1, long date2) {
    Calendar cal1 = Calendar.getInstance();
    Calendar cal2 = Calendar.getInstance();
    cal1.setTimeInMillis(date1);
    cal2.setTimeInMillis(date2);
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
        && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get
        (Calendar.DAY_OF_YEAR);
  }


  /**
   * Checks if a epoch-date timestamp is in the future
   */
  public static boolean isFuture(String timestamp) {
    return !StringUtils.isEmpty(timestamp) && isFuture(Long.parseLong(timestamp));
  }

  /**
   * Checks if a epoch-date timestamp is in the future
   */
  public static boolean isFuture(Long timestamp) {
    return timestamp != null && timestamp > Calendar.getInstance().getTimeInMillis();
  }

  public static String prettyTime(String timeInMillisec) {
    if (timeInMillisec == null) {
      return "";
    }
    return prettyTime(Long.parseLong(timeInMillisec),
        OmniNotes.getAppContext().getResources().getConfiguration().locale);
  }


  public static String prettyTime(Long timeInMillisec) {
    return prettyTime(timeInMillisec,
        OmniNotes.getAppContext().getResources().getConfiguration().locale);
  }


  static String prettyTime(Long timeInMillisec, Locale locale) {
    if (timeInMillisec == null) {
      return "";
    }
    Date d = new Date(timeInMillisec);
    PrettyTime pt = new PrettyTime();
    if (locale != null) {
      pt.setLocale(locale);
    }
    return pt.format(d);
  }
}

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
package it.feio.android.omninotes.db;

import static it.feio.android.checklistview.interfaces.Constants.UNCHECKED_SYM;
import static it.feio.android.omninotes.utils.Constants.DATABASE_NAME;
import static it.feio.android.omninotes.utils.ConstantsBase.PREF_PASSWORD;

import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import com.pixplicity.easyprefs.library.Prefs;
import it.feio.android.omninotes.OmniNotes;
import it.feio.android.omninotes.async.upgrade.UpgradeProcessor;
import it.feio.android.omninotes.exceptions.DatabaseException;
import it.feio.android.omninotes.helpers.LogDelegate;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.Category;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.models.Tag;
import it.feio.android.omninotes.utils.AssetUtils;
import it.feio.android.omninotes.utils.Navigation;
import it.feio.android.omninotes.utils.Security;
import it.feio.android.omninotes.utils.TagsHelper;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;


public class DbHelper extends DbHelper2 {

  // Database name
  // Database version aligned if possible to software version
  private static final int DATABASE_VERSION = 560;
  // Sql query file directory
  private static final String SQL_DIR = "sql";

  // Notes table name
  public static final String TABLE_NOTES = "notes";
  // Notes table columns
  public static final String KEY_ID = "creation";
  public static final String KEY_CREATION = "creation";
  public static final String KEY_LAST_MODIFICATION = "last_modification";
  public static final String KEY_TITLE = "title";
  public static final String KEY_CONTENT = "content";
  public static final String KEY_ARCHIVED = "archived";
  public static final String KEY_TRASHED = "trashed";
  public static final String KEY_REMINDER = "alarm";
  public static final String KEY_REMINDER_FIRED = "reminder_fired";
  public static final String KEY_RECURRENCE_RULE = "recurrence_rule";
  public static final String KEY_LATITUDE = "latitude";
  public static final String KEY_LONGITUDE = "longitude";
  public static final String KEY_ADDRESS = "address";
  public static final String KEY_CATEGORY = "category_id";
  public static final String KEY_LOCKED = "locked";
  public static final String KEY_CHECKLIST = "checklist";

  // Attachments table name
  public static final String TABLE_ATTACHMENTS = "attachments";
  // Attachments table columns
  public static final String KEY_ATTACHMENT_ID = "attachment_id";
  public static final String KEY_ATTACHMENT_URI = "uri";
  public static final String KEY_ATTACHMENT_NAME = "name";
  public static final String KEY_ATTACHMENT_SIZE = "size";
  public static final String KEY_ATTACHMENT_LENGTH = "length";
  public static final String KEY_ATTACHMENT_MIME_TYPE = "mime_type";
  public static final String KEY_ATTACHMENT_NOTE_ID = "note_id";

  // Categories table name
  public static final String TABLE_CATEGORY = "categories";
  // Categories table columns
  public static final String KEY_CATEGORY_ID = "category_id";
  public static final String KEY_CATEGORY_NAME = "name";
  public static final String KEY_CATEGORY_DESCRIPTION = "description";
  public static final String KEY_CATEGORY_COLOR = "color";

  // Queries
  private static final String CREATE_QUERY = "create.sql";
  private static final String UPGRADE_QUERY_PREFIX = "upgrade-";
  private static final String UPGRADE_QUERY_SUFFIX = ".sql";


  private final Context mContext;

  private static DbHelper instance = null;


  public static synchronized DbHelper getInstance() {
    return getInstance(OmniNotes.getAppContext());
  }


  public static synchronized DbHelper getInstance(Context context) {
    if (instance == null) {
      instance = new DbHelper(context);
    }
    return instance;
  }


  public static synchronized DbHelper getInstance(boolean forcedNewInstance) {
    if (instance == null || forcedNewInstance) {
      Context context = (instance == null || instance.mContext == null) ? OmniNotes.getAppContext()
          : instance.mContext;
      instance = new DbHelper(context);
    }
    return instance;
  }


  private DbHelper(Context mContext) {
    super(mContext, DATABASE_NAME, null, DATABASE_VERSION);
    this.mContext = mContext;
  }


  public String getDatabaseName() {
    return DATABASE_NAME;
  }

  @Override
  public void onOpen(SQLiteDatabase db) {
    db.disableWriteAheadLogging();
    super.onOpen(db);
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    try {
      LogDelegate.i("Database creation");
      execSqlFile(CREATE_QUERY, db);
    } catch (IOException e) {
      throw new DatabaseException("Database creation failed: " + e.getMessage(), e);
    }
  }


  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    this.db = db;
    LogDelegate.i("Upgrading database version from " + oldVersion + " to " + newVersion);

    try {

      UpgradeProcessor.process(oldVersion, newVersion);

      for (String sqlFile : AssetUtils.list(SQL_DIR, mContext.getAssets())) {
        if (sqlFile.startsWith(UPGRADE_QUERY_PREFIX)) {
          int fileVersion = Integer.parseInt(sqlFile.substring(UPGRADE_QUERY_PREFIX.length(),
              sqlFile.length() - UPGRADE_QUERY_SUFFIX.length()));
          if (fileVersion > oldVersion && fileVersion <= newVersion) {
            execSqlFile(sqlFile, db);
          }
        }
      }
      LogDelegate.i("Database upgrade successful");

    } catch (IOException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException("Database upgrade failed", e);
    }
  }


  public Note updateNote(Note note, boolean updateLastModification) {
    db = getDatabase(true);

    String content = Boolean.TRUE.equals(note.isLocked())
        ? Security.encrypt(note.getContent(), Prefs.getString(PREF_PASSWORD, ""))
        : note.getContent();

    // To ensure note and attachments insertions are atomic and boost performances transaction are used
    db.beginTransaction();

    ContentValues values = new ContentValues();
    values.put(KEY_TITLE, note.getTitle());
    values.put(KEY_CONTENT, content);
    values.put(KEY_CREATION,
        note.getCreation() != null ? note.getCreation() : Calendar.getInstance().getTimeInMillis());
    long lastModification = note.getLastModification() != null && !updateLastModification
        ? note.getLastModification()
        : Calendar.getInstance().getTimeInMillis();
    values.put(KEY_LAST_MODIFICATION, lastModification);
    values.put(KEY_ARCHIVED, note.isArchived());
    values.put(KEY_TRASHED, note.isTrashed());
    values.put(KEY_REMINDER, note.getAlarm());
    values.put(KEY_REMINDER_FIRED, note.isReminderFired());
    values.put(KEY_RECURRENCE_RULE, note.getRecurrenceRule());
    values.put(KEY_LATITUDE, note.getLatitude());
    values.put(KEY_LONGITUDE, note.getLongitude());
    values.put(KEY_ADDRESS, note.getAddress());
    values.put(KEY_CATEGORY, note.getCategory() != null ? note.getCategory().getId() : null);
    values.put(KEY_LOCKED, note.isLocked() != null && note.isLocked());
    values.put(KEY_CHECKLIST, note.isChecklist() != null && note.isChecklist());

    db.insertWithOnConflict(TABLE_NOTES, KEY_ID, values, SQLiteDatabase.CONFLICT_REPLACE);
    LogDelegate.d("Updated note titled '" + note.getTitle() + "'");

    // Updating attachments
    List<Attachment> deletedAttachments = note.getAttachmentsListOld();
    for (Attachment attachment : note.getAttachmentsList()) {
      updateAttachment(note.get_id() != null ? note.get_id() : values.getAsLong(KEY_CREATION),
          attachment, db);
      deletedAttachments.remove(attachment);
    }
    // Remove from database deleted attachments
    for (Attachment attachmentDeleted : deletedAttachments) {
      db.delete(TABLE_ATTACHMENTS, KEY_ATTACHMENT_ID + " = ?",
          new String[]{String.valueOf(attachmentDeleted.getId())});
    }

    db.setTransactionSuccessful();
    db.endTransaction();

    // Fill the note with correct data before returning it
    note.setCreation(
        note.getCreation() != null ? note.getCreation() : values.getAsLong(KEY_CREATION));
    note.setLastModification(values.getAsLong(KEY_LAST_MODIFICATION));

    return note;
  }


  private void execSqlFile(String sqlFile, SQLiteDatabase db) throws SQLException, IOException {
    LogDelegate.i("  exec sql file: {}" + sqlFile);
    for (String sqlInstruction : SqlParser
        .parseSqlFile(SQL_DIR + "/" + sqlFile, mContext.getAssets())) {
      LogDelegate.v("    sql: {}" + sqlInstruction);
      try {
        db.execSQL(sqlInstruction);
      } catch (Exception e) {
        LogDelegate.e("Error executing command: " + sqlInstruction, e);
      }
    }
  }


  /**
   * Attachments update
   */
  public Attachment updateAttachment(Attachment attachment) {
    return updateAttachment(-1, attachment, getDatabase(true));
  }


  /**
   * New attachment insertion
   */
  public Attachment updateAttachment(long noteId, Attachment attachment, SQLiteDatabase db) {
    ContentValues valuesAttachments = new ContentValues();
    valuesAttachments
        .put(KEY_ATTACHMENT_ID, attachment.getId() != null ? attachment.getId() : Calendar
            .getInstance().getTimeInMillis());
    valuesAttachments.put(KEY_ATTACHMENT_NOTE_ID, noteId);
    valuesAttachments.put(KEY_ATTACHMENT_URI, attachment.getUri().toString());
    valuesAttachments.put(KEY_ATTACHMENT_MIME_TYPE, attachment.getMime_type());
    valuesAttachments.put(KEY_ATTACHMENT_NAME, attachment.getName());
    valuesAttachments.put(KEY_ATTACHMENT_SIZE, attachment.getSize());
    valuesAttachments.put(KEY_ATTACHMENT_LENGTH, attachment.getLength());
    db.insertWithOnConflict(TABLE_ATTACHMENTS, KEY_ATTACHMENT_ID, valuesAttachments,
        SQLiteDatabase.CONFLICT_REPLACE);
    return attachment;
  }


  public List<Note> getNotesWithLocation() {
    String whereCondition = " WHERE " + KEY_LONGITUDE + " IS NOT NULL "
        + "AND " + KEY_LONGITUDE + " != 0 ";
    return getNotes(whereCondition, true);
  }


  /**
   * Archives/restore single note
   */
  public void archiveNote(Note note, boolean archive) {
    note.setArchived(archive);
    updateNote(note, false);
  }


  /**
   * Trashes/restore single note
   */
  public void trashNote(Note note, boolean trash) {
    note.setTrashed(trash);
    updateNote(note, false);
  }


  /**
   * Deleting single note
   */
  public boolean deleteNote(Note note) {
    return deleteNote(note, false);
  }


  /**
   * Deleting single note, eventually keeping attachments
   */
  public boolean deleteNote(Note note, boolean keepAttachments) {
    return deleteNote(note.get_id(), keepAttachments);
  }


  /**
   * Deleting single note by its ID
   */
  public boolean deleteNote(long noteId, boolean keepAttachments) {
    SQLiteDatabase db = getDatabase(true);
    db.delete(TABLE_NOTES, KEY_ID + " = ?", new String[]{String.valueOf(noteId)});
    if (!keepAttachments) {
      db.delete(TABLE_ATTACHMENTS, KEY_ATTACHMENT_NOTE_ID + " = ?",
          new String[]{String.valueOf(noteId)});
    }
    return true;
  }


  /**
   * Empties trash deleting all trashed notes
   */
  public void emptyTrash() {
    for (Note note : getNotesTrashed()) {
      deleteNote(note);
    }
  }


  /**
   * Gets notes matching pattern with title or content text
   *
   * @param pattern String to match with
   * @return Notes list
   */
  public List<Note> getNotesByPattern(String pattern) {
    String escapedPattern = escapeSql(pattern);
    int navigation = Navigation.getNavigation();
    String whereCondition = " WHERE "
        + KEY_TRASHED + (navigation == Navigation.TRASH ? " IS 1" : " IS NOT 1")
        + (navigation == Navigation.ARCHIVE ? " AND " + KEY_ARCHIVED + " IS 1" : "")
        + (navigation == Navigation.CATEGORY ? " AND " + KEY_CATEGORY + " = " + Navigation
        .getCategory() : "")
        + (navigation == Navigation.UNCATEGORIZED ? " AND (" + KEY_CATEGORY + " IS NULL OR "
        + KEY_CATEGORY_ID
        + " == 0) " : "")
        + (Navigation.checkNavigation(Navigation.REMINDERS) ? " AND " + KEY_REMINDER
        + " IS NOT NULL" : "")
        + " AND ("
        + " ( " + KEY_LOCKED + " IS NOT 1 AND (" + KEY_TITLE + " LIKE '%" + escapedPattern
        + "%' ESCAPE '\\' " + " OR "
        +
        KEY_CONTENT + " LIKE '%" + escapedPattern + "%' ESCAPE '\\' ))"
        + " OR ( " + KEY_LOCKED + " = 1 AND " + KEY_TITLE + " LIKE '%" + escapedPattern
        + "%' ESCAPE '\\' )"
        + ")";
    return getNotes(whereCondition, true);
  }

  static String escapeSql(String pattern) {
    return StringUtils.replace(pattern, "'", "''")
        .replace("%", "\\%")
        .replace("_", "\\_");
  }


  /**
   * Returns all notes that have a reminder that has not been alredy fired
   *
   * @return Notes list
   */
  public List<Note> getNotesWithReminderNotFired() {
    String whereCondition = " WHERE " + KEY_REMINDER + " IS NOT NULL"
        + " AND " + KEY_REMINDER_FIRED + " IS NOT 1"
        + " AND " + KEY_ARCHIVED + " IS NOT 1"
        + " AND " + KEY_TRASHED + " IS NOT 1";
    return getNotes(whereCondition, true);
  }


  /**
   * Retrieves locked or unlocked notes
   */
  public List<Note> getNotesWithLock(boolean locked) {
    String whereCondition = " WHERE " + KEY_LOCKED + (locked ? " = 1 " : " IS NOT 1 ");
    return getNotes(whereCondition, true);
  }


  /**
   * Search for notes with reminder expiring the current day
   *
   * @return Notes list
   */
  public List<Note> getTodayReminders() {
    String whereCondition =
        " WHERE DATE(" + KEY_REMINDER + "/1000, 'unixepoch') = DATE('now') AND " +
            KEY_TRASHED + " IS NOT 1";
    return getNotes(whereCondition, false);
  }


  public List<Note> getChecklists() {
    String whereCondition = " WHERE " + KEY_CHECKLIST + " = 1";
    return getNotes(whereCondition, false);
  }


  public List<Note> getMasked() {
    String whereCondition = " WHERE " + KEY_LOCKED + " = 1";
    return getNotes(whereCondition, false);
  }


  /**
   * Retrieves all tags
   */
  public List<Tag> getTags() {
    return getTags(null);
  }


  /**
   * Retrieves all tags of a specified note
   */
  public List<Tag> getTags(Note note) {
    List<Tag> tags = new ArrayList<>();
    HashMap<String, Integer> tagsMap = new HashMap<>();

    String whereCondition = " WHERE "
        + (note != null ? KEY_ID + " = " + note.get_id() + " AND " : "")
        + "(" + KEY_CONTENT + " LIKE '%#%' OR " + KEY_TITLE + " LIKE '%#%' " + ")"
        + " AND " + KEY_TRASHED + " IS " + (Navigation.checkNavigation(Navigation.TRASH) ? ""
        : " NOT ") + " 1";
    List<Note> notesRetrieved = getNotes(whereCondition, true);

    for (Note noteRetrieved : notesRetrieved) {
      HashMap<String, Integer> tagsRetrieved = TagsHelper.retrieveTags(noteRetrieved);
      for (String s : tagsRetrieved.keySet()) {
        int count = tagsMap.get(s) == null ? 0 : tagsMap.get(s);
        tagsMap.put(s, ++count);
      }
    }

    for (Entry<String, Integer> entry : tagsMap.entrySet()) {
      Tag tag = new Tag(entry.getKey(), entry.getValue());
      tags.add(tag);
    }

    Collections.sort(tags, (tag1, tag2) -> tag1.getText().compareToIgnoreCase(tag2.getText()));
    return tags;
  }


  /**
   * Retrieves all notes related to category it passed as parameter
   */
  public List<Note> getNotesByTag(String tag) {
    if (tag.contains(",")) {
      return getNotesByTag(tag.split(","));
    } else {
      return getNotesByTag(new String[]{tag});
    }
  }


  /**
   * Retrieves all notes with specified tags
   */
  public List<Note> getNotesByTag(String[] tags) {
    StringBuilder whereCondition = new StringBuilder();
    whereCondition.append(" WHERE ");
    for (int i = 0; i < tags.length; i++) {
      if (i != 0) {
        whereCondition.append(" AND ");
      }
      whereCondition.append("(" + KEY_CONTENT + " LIKE '%").append(tags[i]).append("%' OR ")
          .append(KEY_TITLE)
          .append(" LIKE '%").append(tags[i]).append("%')");
    }
    // Trashed notes must be included in search results only if search if performed from trash
    whereCondition.append(" AND " + KEY_TRASHED + " IS ")
        .append(Navigation.checkNavigation(Navigation.TRASH) ?
            "" : "" +
            " NOT ").append(" 1");

    return rx.Observable.from(getNotes(whereCondition.toString(), true))
        .map(note -> {
          boolean matches = rx.Observable.from(tags)
              .all(tag -> {
                Pattern p = Pattern.compile(".*(\\s|^)" + tag + "(\\s|$).*",
                    Pattern.MULTILINE);
                return p.matcher(
                    (note.getTitle() + " " + note.getContent())).find();
              }).toBlocking().single();
          return matches ? note : null;
        })
        .filter(Objects::nonNull)
        .toList().toBlocking().single();
  }

  /**
   * Retrieves all uncompleted checklists
   */
  public List<Note> getNotesByUncompleteChecklist() {
    String whereCondition =
        " WHERE " + KEY_CHECKLIST + " = 1 AND " + KEY_CONTENT + " LIKE '%" + UNCHECKED_SYM + "%' AND "
    + KEY_TRASHED + (Navigation.checkNavigation(Navigation.TRASH) ? " IS 1" : " IS NOT 1");
    return getNotes(whereCondition, true);
  }


  /**
   * Updates or insert a new a category
   *
   * @param category Category to be updated or inserted
   * @return Rows affected or new inserted category ID
   */
  public Category updateCategory(Category category) {
    ContentValues values = new ContentValues();
    values.put(KEY_CATEGORY_ID, category.getId() != null ? category.getId() : Calendar.getInstance()
        .getTimeInMillis());
    values.put(KEY_CATEGORY_NAME, category.getName());
    values.put(KEY_CATEGORY_DESCRIPTION, category.getDescription());
    values.put(KEY_CATEGORY_COLOR, category.getColor());
    getDatabase(true).insertWithOnConflict(TABLE_CATEGORY, KEY_CATEGORY_ID, values, SQLiteDatabase
        .CONFLICT_REPLACE);
    return category;
  }


  /**
   * Deletion of  a category
   *
   * @param category Category to be deleted
   * @return Number 1 if category's record has been deleted, 0 otherwise
   */
  public long deleteCategory(Category category) {
    long deleted;

    SQLiteDatabase db = getDatabase(true);
    // Un-categorize notes associated with this category
    ContentValues values = new ContentValues();
    values.put(KEY_CATEGORY, "");

    // Updating row
    db.update(TABLE_NOTES, values, KEY_CATEGORY + " = ?",
        new String[]{String.valueOf(category.getId())});

    // Delete category
    deleted = db.delete(TABLE_CATEGORY, KEY_CATEGORY_ID + " = ?",
        new String[]{String.valueOf(category.getId())});
    return deleted;
  }


}

package it.feio.android.omninotes.db;

import static it.feio.android.omninotes.utils.ConstantsBase.MIME_TYPE_AUDIO;
import static it.feio.android.omninotes.utils.ConstantsBase.MIME_TYPE_FILES;
import static it.feio.android.omninotes.utils.ConstantsBase.MIME_TYPE_IMAGE;
import static it.feio.android.omninotes.utils.ConstantsBase.MIME_TYPE_SKETCH;
import static it.feio.android.omninotes.utils.ConstantsBase.MIME_TYPE_VIDEO;
import static it.feio.android.omninotes.utils.ConstantsBase.PREF_FILTER_ARCHIVED_IN_CATEGORIES;
import static it.feio.android.omninotes.utils.ConstantsBase.PREF_FILTER_PAST_REMINDERS;
import static it.feio.android.omninotes.utils.ConstantsBase.PREF_PASSWORD;
import static it.feio.android.omninotes.utils.ConstantsBase.PREF_SORTING_COLUMN;
import static it.feio.android.omninotes.utils.ConstantsBase.TIMESTAMP_UNIX_EPOCH;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import com.pixplicity.easyprefs.library.Prefs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import it.feio.android.omninotes.helpers.LogDelegate;
import it.feio.android.omninotes.helpers.NotesHelper;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.Category;
import it.feio.android.omninotes.models.Note;
import it.feio.android.omninotes.models.Stats;
import it.feio.android.omninotes.utils.Navigation;
import it.feio.android.omninotes.utils.Security;
import it.feio.android.omninotes.utils.TagsHelper;

public abstract class DbHelper2 extends SQLiteOpenHelper {
    protected SQLiteDatabase db;

    public DbHelper2(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    public SQLiteDatabase getDatabase() {
        return getDatabase(false);
    }

    public SQLiteDatabase getDatabase(boolean forceWritable) {
        try {
            return forceWritable ? getWritableDatabase() : getReadableDatabase();
        } catch (IllegalStateException e) {
            return this.db;
        }
    }

    /**
     * Getting single note
     */
    public Note getNote(long id) {
        List<Note> notes = getNotes(" WHERE " + DbHelper.KEY_ID + " = " + id, true);
        return notes.isEmpty() ? null : notes.get(0);
    }

    /**
     * Getting All notes
     *
     * @param checkNavigation Tells if navigation status (notes, archived) must be kept in
     *                        consideration or if all notes have to be retrieved
     * @return Notes list
     */
    public List<Note> getAllNotes(Boolean checkNavigation) {
        String whereCondition = "";
        if (Boolean.TRUE.equals(checkNavigation)) {
            int navigation = Navigation.getNavigation();
            switch (navigation) {
                case Navigation.NOTES:
                    return getNotesActive();
                case Navigation.ARCHIVE:
                    return getNotesArchived();
                case Navigation.REMINDERS:
                    return getNotesWithReminder(Prefs.getBoolean(PREF_FILTER_PAST_REMINDERS, false));
                case Navigation.TRASH:
                    return getNotesTrashed();
                case Navigation.UNCATEGORIZED:
                    return getNotesUncategorized();
                case Navigation.CATEGORY:
                    return getNotesByCategory(Navigation.getCategory());
                default:
                    return getNotes(whereCondition, true);
            }
        } else {
            return getNotes(whereCondition, true);
        }

    }

    public List<Note> getNotesActive() {
        String whereCondition =
                " WHERE " + DbHelper.KEY_ARCHIVED + " IS NOT 1 AND " + DbHelper.KEY_TRASHED + " IS NOT 1 ";
        return getNotes(whereCondition, true);
    }

    public List<Note> getNotesArchived() {
        String whereCondition = " WHERE " + DbHelper.KEY_ARCHIVED + " = 1 AND " + DbHelper.KEY_TRASHED + " IS NOT 1 ";
        return getNotes(whereCondition, true);
    }

    public List<Note> getNotesTrashed() {
        String whereCondition = " WHERE " + DbHelper.KEY_TRASHED + " = 1 ";
        return getNotes(whereCondition, true);
    }

    public List<Note> getNotesUncategorized() {
        String whereCondition = " WHERE "
                + "(" + DbHelper.KEY_CATEGORY_ID + " IS NULL OR " + DbHelper.KEY_CATEGORY_ID + " == 0) "
                + "AND " + DbHelper.KEY_TRASHED + " IS NOT 1";
        return getNotes(whereCondition, true);
    }

    /**
     * Common method for notes retrieval. It accepts a query to perform and returns matching records.
     */
    public List<Note> getNotes(String whereCondition, boolean order) {
        List<Note> noteList = new ArrayList<>();

        String sortColumn = "";
        String sortOrder = "";

        // Getting sorting criteria from preferences. Reminder screen forces sorting.
        if (Navigation.checkNavigation(Navigation.REMINDERS)) {
            sortColumn = DbHelper.KEY_REMINDER;
        } else {
            sortColumn = Prefs.getString(PREF_SORTING_COLUMN, DbHelper.KEY_TITLE);
        }
        if (order) {
            sortOrder =
                    DbHelper.KEY_TITLE.equals(sortColumn) || DbHelper.KEY_REMINDER.equals(sortColumn) ? " ASC " : " DESC ";
        }

        // In case of title sorting criteria it must be handled empty title by concatenating content
        sortColumn = DbHelper.KEY_TITLE.equals(sortColumn) ? DbHelper.KEY_TITLE + "||" + DbHelper.KEY_CONTENT : sortColumn;

        // In case of reminder sorting criteria the empty reminder notes must be moved on bottom of results
        sortColumn = DbHelper.KEY_REMINDER.equals(sortColumn) ? "IFNULL(" + DbHelper.KEY_REMINDER + ", " +
                "" + TIMESTAMP_UNIX_EPOCH + ")" : sortColumn;

        // Generic query to be specialized with conditions passed as parameter
        String query = "SELECT "
                + DbHelper.KEY_CREATION + ","
                + DbHelper.KEY_LAST_MODIFICATION + ","
                + DbHelper.KEY_TITLE + ","
                + DbHelper.KEY_CONTENT + ","
                + DbHelper.KEY_ARCHIVED + ","
                + DbHelper.KEY_TRASHED + ","
                + DbHelper.KEY_REMINDER + ","
                + DbHelper.KEY_REMINDER_FIRED + ","
                + DbHelper.KEY_RECURRENCE_RULE + ","
                + DbHelper.KEY_LATITUDE + ","
                + DbHelper.KEY_LONGITUDE + ","
                + DbHelper.KEY_ADDRESS + ","
                + DbHelper.KEY_LOCKED + ","
                + DbHelper.KEY_CHECKLIST + ","
                + DbHelper.KEY_CATEGORY + ","
                + DbHelper.KEY_CATEGORY_NAME + ","
                + DbHelper.KEY_CATEGORY_DESCRIPTION + ","
                + DbHelper.KEY_CATEGORY_COLOR
                + " FROM " + DbHelper.TABLE_NOTES
                + " LEFT JOIN " + DbHelper.TABLE_CATEGORY + " USING( " + DbHelper.KEY_CATEGORY + ") "
                + whereCondition
                + (order ? " ORDER BY " + sortColumn + " COLLATE NOCASE " + sortOrder : "");

        LogDelegate.v("Query: " + query);

        try (Cursor cursor = getDatabase().rawQuery(query, null)) {

            if (cursor.moveToFirst()) {
                do {
                    int i = 0;
                    Note note = new Note();
                    note.setCreation(cursor.getLong(i++));
                    note.setLastModification(cursor.getLong(i++));
                    note.setTitle(cursor.getString(i++));
                    note.setContent(cursor.getString(i++));
                    note.setArchived("1".equals(cursor.getString(i++)));
                    note.setTrashed("1".equals(cursor.getString(i++)));
                    note.setAlarm(cursor.getString(i++));
                    note.setReminderFired(cursor.getInt(i++));
                    note.setRecurrenceRule(cursor.getString(i++));
                    note.setLatitude(cursor.getString(i++));
                    note.setLongitude(cursor.getString(i++));
                    note.setAddress(cursor.getString(i++));
                    note.setLocked("1".equals(cursor.getString(i++)));
                    note.setChecklist("1".equals(cursor.getString(i++)));

                    // Eventual decryption of content
                    if (Boolean.TRUE.equals(note.isLocked())) {
                        note.setContent(
                                Security.decrypt(note.getContent(), Prefs.getString(PREF_PASSWORD, "")));
                    }

                    // Set category
                    long categoryId = cursor.getLong(i++);
                    if (categoryId != 0) {
                        Category category = new Category(categoryId, cursor.getString(i++),
                                cursor.getString(i++), cursor.getString(i));
                        note.setCategory(category);
                    }

                    // Add eventual attachments uri
                    note.setAttachmentsList(getNoteAttachments(note));

                    // Adding note to list
                    noteList.add(note);

                } while (cursor.moveToNext());
            }

        }

        LogDelegate.v("Query: Retrieval finished!");
        return noteList;
    }

    /**
     * Search for notes with reminder
     *
     * @param filterPastReminders Excludes past reminders
     * @return Notes list
     */
    public List<Note> getNotesWithReminder(boolean filterPastReminders) {
        String whereCondition = " WHERE " + DbHelper.KEY_REMINDER
                + (filterPastReminders ? " >= " + Calendar.getInstance().getTimeInMillis() : " IS NOT NULL")
                + " AND " + DbHelper.KEY_ARCHIVED + " IS NOT 1"
                + " AND " + DbHelper.KEY_TRASHED + " IS NOT 1";
        return getNotes(whereCondition, true);
    }

    /**
     * Retrieves all attachments related to specific note
     */
    public ArrayList<Attachment> getNoteAttachments(Note note) {
        String whereCondition = " WHERE " + DbHelper.KEY_ATTACHMENT_NOTE_ID + " = " + note.get_id();
        return getAttachments(whereCondition);
    }

    /**
     * Retrieves all notes related to Category it passed as parameter
     *
     * @param categoryId Category integer identifier
     * @return List of notes with requested category
     */
    public List<Note> getNotesByCategory(Long categoryId) {
        List<Note> notes;
        boolean filterArchived = Prefs
                .getBoolean(PREF_FILTER_ARCHIVED_IN_CATEGORIES + categoryId, false);
        try {
            String whereCondition = " WHERE "
                    + DbHelper.KEY_CATEGORY_ID + " = " + categoryId
                    + " AND " + DbHelper.KEY_TRASHED + " IS NOT 1"
                    + (filterArchived ? " AND " + DbHelper.KEY_ARCHIVED + " IS NOT 1" : "");
            notes = getNotes(whereCondition, true);
        } catch (NumberFormatException e) {
            notes = getAllNotes(true);
        }
        return notes;
    }

    /**
     * Retrieves all attachments
     */
    public ArrayList<Attachment> getAllAttachments() {
        return getAttachments("");
    }

    /**
     * Retrieves attachments using a condition passed as parameter
     *
     * @return List of attachments
     */
    public ArrayList<Attachment> getAttachments(String whereCondition) {

        ArrayList<Attachment> attachmentsList = new ArrayList<>();
        String sql = "SELECT "
                + DbHelper.KEY_ATTACHMENT_ID + ","
                + DbHelper.KEY_ATTACHMENT_URI + ","
                + DbHelper.KEY_ATTACHMENT_NAME + ","
                + DbHelper.KEY_ATTACHMENT_SIZE + ","
                + DbHelper.KEY_ATTACHMENT_LENGTH + ","
                + DbHelper.KEY_ATTACHMENT_MIME_TYPE
                + " FROM " + DbHelper.TABLE_ATTACHMENTS
                + whereCondition;
        SQLiteDatabase db;
        Cursor cursor = null;

        try {

            cursor = getDatabase().rawQuery(sql, null);

            // Looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                Attachment mAttachment;
                do {
                    mAttachment = new Attachment(cursor.getLong(0),
                            Uri.parse(cursor.getString(1)), cursor.getString(2), cursor.getInt(3),
                            (long) cursor.getInt(4), cursor.getString(5));
                    attachmentsList.add(mAttachment);
                } while (cursor.moveToNext());
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return attachmentsList;
    }

    /**
     * Retrieves categories list from database
     *
     * @return List of categories
     */
    public ArrayList<Category> getCategories() {
        ArrayList<Category> categoriesList = new ArrayList<>();
        String sql = "SELECT "
                + DbHelper.KEY_CATEGORY_ID + ","
                + DbHelper.KEY_CATEGORY_NAME + ","
                + DbHelper.KEY_CATEGORY_DESCRIPTION + ","
                + DbHelper.KEY_CATEGORY_COLOR + ","
                + " COUNT(" + DbHelper.KEY_ID + ") count"
                + " FROM " + DbHelper.TABLE_CATEGORY
                + " LEFT JOIN ("
                + " SELECT " + DbHelper.KEY_ID + ", " + DbHelper.KEY_CATEGORY
                + " FROM " + DbHelper.TABLE_NOTES
                + " WHERE " + DbHelper.KEY_TRASHED + " IS NOT 1"
                + ") USING( " + DbHelper.KEY_CATEGORY + ") "
                + " GROUP BY "
                + DbHelper.KEY_CATEGORY_ID + ","
                + DbHelper.KEY_CATEGORY_NAME + ","
                + DbHelper.KEY_CATEGORY_DESCRIPTION + ","
                + DbHelper.KEY_CATEGORY_COLOR
                + " ORDER BY IFNULL(NULLIF(" + DbHelper.KEY_CATEGORY_NAME + ", ''),'zzzzzzzz') ";

        Cursor cursor = null;
        try {
            cursor = getDatabase().rawQuery(sql, null);
            // Looping through all rows and adding to list
            if (cursor.moveToFirst()) {
                do {
                    categoriesList.add(new Category(cursor.getLong(0),
                            cursor.getString(1), cursor.getString(2), cursor
                            .getString(3), cursor.getInt(4)));
                } while (cursor.moveToNext());
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return categoriesList;
    }

    /**
     * Get note Category
     */
    public Category getCategory(Long id) {
        Category category = null;
        String sql = "SELECT "
                + DbHelper.KEY_CATEGORY_ID + ","
                + DbHelper.KEY_CATEGORY_NAME + ","
                + DbHelper.KEY_CATEGORY_DESCRIPTION + ","
                + DbHelper.KEY_CATEGORY_COLOR
                + " FROM " + DbHelper.TABLE_CATEGORY
                + " WHERE " + DbHelper.KEY_CATEGORY_ID + " = " + id;

        try (Cursor cursor = getDatabase().rawQuery(sql, null)) {

            if (cursor.moveToFirst()) {
                category = new Category(cursor.getLong(0), cursor.getString(1),
                        cursor.getString(2), cursor.getString(3));
            }

        }
        return category;
    }

    public int getCategorizedCount(Category category) {
        int count = 0;
        String sql = "SELECT COUNT(*)"
                + " FROM " + DbHelper.TABLE_NOTES
                + " WHERE " + DbHelper.KEY_CATEGORY + " = " + category.getId();

        try (Cursor cursor = getDatabase().rawQuery(sql, null)) {
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        }
        return count;
    }

    /**
     * Retrieves statistics data based on app usage
     */
    public Stats getStats() {
        Stats mStats = new Stats();

        // Categories
        mStats.setCategories(getCategories().size());

        // Everything about notes and their text stats
        int notesActive = 0;
        int notesArchived = 0;
        int notesTrashed = 0;
        int reminders = 0;
        int remindersFuture = 0;
        int checklists = 0;
        int notesMasked = 0;
        int tags = 0;
        int locations = 0;
        int totalWords = 0;
        int totalChars = 0;
        int maxWords = 0;
        int maxChars = 0;
        int avgWords;
        int avgChars;
        int words;
        int chars;
        List<Note> notes = getAllNotes(false);
        for (Note note : notes) {
            if (note.isTrashed()) {
                notesTrashed++;
            } else if (note.isArchived()) {
                notesArchived++;
            } else {
                notesActive++;
            }
            if (note.getAlarm() != null && Long.parseLong(note.getAlarm()) > 0) {
                if (Long.parseLong(note.getAlarm()) > Calendar.getInstance().getTimeInMillis()) {
                    remindersFuture++;
                } else {
                    reminders++;
                }
            }
            if (note.isChecklist()) {
                checklists++;
            }
            if (note.isLocked()) {
                notesMasked++;
            }
            tags += TagsHelper.retrieveTags(note).size();
            if (note.getLongitude() != null && note.getLongitude() != 0) {
                locations++;
            }
            words = NotesHelper.getWords(note);
            chars = NotesHelper.getChars(note);
            if (words > maxWords) {
                maxWords = words;
            }
            if (chars > maxChars) {
                maxChars = chars;
            }
            totalWords += words;
            totalChars += chars;
        }
        mStats.setNotesActive(notesActive);
        mStats.setNotesArchived(notesArchived);
        mStats.setNotesTrashed(notesTrashed);
        mStats.setReminders(reminders);
        mStats.setRemindersFutures(remindersFuture);
        mStats.setNotesChecklist(checklists);
        mStats.setNotesMasked(notesMasked);
        mStats.setTags(tags);
        mStats.setLocation(locations);
        avgWords = totalWords / (!notes.isEmpty() ? notes.size() : 1);
        avgChars = totalChars / (!notes.isEmpty() ? notes.size() : 1);

        mStats.setWords(totalWords);
        mStats.setWordsMax(maxWords);
        mStats.setWordsAvg(avgWords);
        mStats.setChars(totalChars);
        mStats.setCharsMax(maxChars);
        mStats.setCharsAvg(avgChars);

        // Everything about attachments
        int attachmentsAll = 0;
        int images = 0;
        int videos = 0;
        int audioRecordings = 0;
        int sketches = 0;
        int files = 0;

        List<Attachment> attachments = getAllAttachments();
        for (Attachment attachment : attachments) {
            if (MIME_TYPE_IMAGE.equals(attachment.getMime_type())) {
                images++;
            } else if (MIME_TYPE_VIDEO.equals(attachment.getMime_type())) {
                videos++;
            } else if (MIME_TYPE_AUDIO.equals(attachment.getMime_type())) {
                audioRecordings++;
            } else if (MIME_TYPE_SKETCH.equals(attachment.getMime_type())) {
                sketches++;
            } else if (MIME_TYPE_FILES.equals(attachment.getMime_type())) {
                files++;
            }
        }
        mStats.setAttachments(attachmentsAll);
        mStats.setImages(images);
        mStats.setVideos(videos);
        mStats.setAudioRecordings(audioRecordings);
        mStats.setSketches(sketches);
        mStats.setFiles(files);

        return mStats;
    }

    public void setReminderFired(long noteId, boolean fired) {
        ContentValues values = new ContentValues();
        values.put(DbHelper.KEY_REMINDER_FIRED, fired);
        getDatabase(true)
                .update(DbHelper.TABLE_NOTES, values, DbHelper.KEY_ID + " = ?", new String[]{String.valueOf(noteId)});
    }
}

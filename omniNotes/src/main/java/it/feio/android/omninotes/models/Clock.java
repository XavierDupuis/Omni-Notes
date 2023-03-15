package it.feio.android.omninotes.models;

public class Clock {
    private final String alarm;
    private final String recurrenceRule;
    private final Integer reminderFired;

    public Clock(String alarm, String recurrenceRule, Integer reminderFired) {
        this.alarm = alarm;
        this.recurrenceRule = recurrenceRule;
        this.reminderFired = reminderFired;
    }

    public String getAlarm() {
        return alarm;
    }

    public String getRecurrenceRule() {
        return recurrenceRule;
    }

    public Integer getReminderFired() {
        return reminderFired;
    }
}

package it.feio.android.omninotes.models;

public class Status {
    private final Integer archived;
    private final Integer trashed;
    private final Integer locked;

    public Status(Integer archived, Integer trashed, Integer locked) {
        this.archived = archived;
        this.trashed = trashed;
        this.locked = locked;
    }

    public Integer getArchived() {
        return archived;
    }

    public Integer getTrashed() {
        return trashed;
    }

    public Integer getLocked() {
        return locked;
    }
}

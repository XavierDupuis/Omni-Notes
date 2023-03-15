package it.feio.android.omninotes.models;

public class NoteInfos {
    private final String title;
    private final String content;
    private final Category category;
    private final Integer checklist;

    public NoteInfos(String title, String content, Category category, Integer checklist) {
        this.title = title;
        this.content = content;
        this.category = category;
        this.checklist = checklist;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Category getCategory() {
        return category;
    }

    public Integer getChecklist() {
        return checklist;
    }
}

package it.feio.android.omninotes.models;

public class Metadata {
    private final Long creation;
    private final Long lastModification;
    private final String latitude;
    private final String longitude;

    public Metadata(Long creation, Long lastModification, String latitude, String longitude) {
        this.creation = creation;
        this.lastModification = lastModification;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getCreation() {
        return creation;
    }

    public Long getLastModification() {
        return lastModification;
    }

    public String getLatitude() {
        return latitude;
    }

    public String getLongitude() {
        return longitude;
    }
}

package rs.raf.eventbooker.rsvp;

import java.util.Date;

public class Rsvp {
    private Long id;
    private String userEmail;
    private Long eventId;
    private Date createdAt;

    public Rsvp() {}
    public Rsvp(Long id, String userEmail, Long eventId, Date createdAt) {
        this.id = id; this.userEmail = userEmail; this.eventId = eventId; this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getUserEmail() { return userEmail; }
    public Long getEventId() { return eventId; }
    public Date getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}

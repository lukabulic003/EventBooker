package rs.raf.eventbooker.comments;

import java.util.Date;

public class Comment {
    private Long id;
    private Long eventId;
    private String authorName;
    private String body;
    private Date createdAt;
    private Integer likesCount;
    private Integer dislikesCount;

    public Comment() {}

    public Comment(Long id, Long eventId, String authorName, String body,
                   Date createdAt, Integer likesCount, Integer dislikesCount) {
        this.id = id;
        this.eventId = eventId;
        this.authorName = authorName;
        this.body = body;
        this.createdAt = createdAt;
        this.likesCount = likesCount;
        this.dislikesCount = dislikesCount;
    }

    public Long getId() { return id; }
    public Long getEventId() { return eventId; }
    public String getAuthorName() { return authorName; }
    public String getBody() { return body; }
    public Date getCreatedAt() { return createdAt; }
    public Integer getLikesCount() { return likesCount; }
    public Integer getDislikesCount() { return dislikesCount; }

    public void setId(Long id) { this.id = id; }
    public void setEventId(Long eventId) { this.eventId = eventId; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public void setBody(String body) { this.body = body; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public void setLikesCount(Integer likesCount) { this.likesCount = likesCount; }
    public void setDislikesCount(Integer dislikesCount) { this.dislikesCount = dislikesCount; }
}

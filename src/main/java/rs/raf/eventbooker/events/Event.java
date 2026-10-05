package rs.raf.eventbooker.events;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Event {
    private Long id;
    private String title;
    private String description;
    private Date createdAt;
    private Date scheduledAt;
    private String location;
    private Integer viewsCount;
    private Integer likesCount;
    private Integer dislikesCount;
    private Integer maxCapacity;
    private Long authorId;
    private Long categoryId;

    private List<String> tags = new ArrayList<>();

    public Event() {}

    public Event(Long id, String title, String description, Date createdAt, Date scheduledAt,
                 String location, Integer viewsCount, Integer likesCount, Integer dislikesCount,
                 Integer maxCapacity, Long authorId, Long categoryId) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
        this.scheduledAt = scheduledAt;
        this.location = location;
        this.viewsCount = viewsCount;
        this.likesCount = likesCount;
        this.dislikesCount = dislikesCount;
        this.maxCapacity = maxCapacity;
        this.authorId = authorId;
        this.categoryId = categoryId;
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Date scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getViewsCount() { return viewsCount; }
    public void setViewsCount(Integer viewsCount) { this.viewsCount = viewsCount; }

    public Integer getLikesCount() { return likesCount; }
    public void setLikesCount(Integer likesCount) { this.likesCount = likesCount; }

    public Integer getDislikesCount() { return dislikesCount; }
    public void setDislikesCount(Integer dislikesCount) { this.dislikesCount = dislikesCount; }

    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(Integer maxCapacity) { this.maxCapacity = maxCapacity; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}

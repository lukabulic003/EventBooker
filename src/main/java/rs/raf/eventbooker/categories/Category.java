package rs.raf.eventbooker.categories;

public class Category {
    private Long id;
    private String name;
    private String description;
    private Long createdBy;
    private Integer eventsCount;


    public Category(Long id, String name, String description) {
        this(id, name, description, null, null);
    }

    public Category(Long id, String name, String description, Long createdBy, Integer eventsCount) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.eventsCount = eventsCount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Integer getEventsCount() { return eventsCount; }
    public void setEventsCount(Integer eventsCount) { this.eventsCount = eventsCount; }
}

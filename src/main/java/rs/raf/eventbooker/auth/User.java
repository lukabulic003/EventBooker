package rs.raf.eventbooker.auth;

public class User {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String userType;      // "ADMIN" ili "CREATOR"
    private String status;        // "ACTIVE" ili "INACTIVE"

    private transient String passwordHash;

    public User() {}

    public User(Long id, String email, String firstName, String lastName,
                String userType, String status, String passwordHash) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.userType = userType;
        this.status = status;
        this.passwordHash = passwordHash;
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}

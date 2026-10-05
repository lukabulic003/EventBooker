package rs.raf.eventbooker.util;

public class HashOnce {
    public static void main(String[] args) {
        System.out.println(PasswordUtil.hash("admin123"));
        System.out.println(PasswordUtil.hash("lozinka123"));
    }
}

package org.example.domain.entitiesAssociatedWithUser;

import org.example.domain.Entity;

public class User extends Entity<Long> {
    private String firstName;
    private String lastName;
    private String password;
    private String occupation;

    public User() {}
    public User(Long id, String firstName, String lastName, String password, String occupation) {
        setId(id);
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.password   = password;
        this.occupation = occupation;
    }

    public User(String firstName, String lastName, String password, String occupation) {
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.password   = password;
        this.occupation = occupation;
    }

    public User(String firstName, String lastName, String password) {
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.password   = password;
    }

    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    public String getOccupation() {
        return occupation;
    }
    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + getId() +
                ", firstName='" + firstName + '\'' +
                ", lastName='"  + lastName  + '\'' +
                ", occupation='" + occupation + '\'' +
                '}';
    }
}

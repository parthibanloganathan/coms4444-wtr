package wtr.g2;

public class Person {
    public enum Status{US,STRANGER, FRIEND, SOULMATE};

    public int id;
    public int wisdom;
    public Status status; // stranger, friend or soul mate
    public boolean known; // have we met them before
    public int remaining_wisdom; // how long we've spoken with them
    public boolean has_left; // have they left a conversation with us
}

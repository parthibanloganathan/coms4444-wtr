package wtr.g2;

import wtr.sim.Point;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Person {
    public static Set<Integer> valid_wisdoms = new HashSet <Integer> (Arrays.asList(new Integer[]{0,10,20,50,200}));
    public enum Status{US,STRANGER, FRIEND, SOULMATE};

    public int id;
    public int wisdom = -1;
    public Status status; // stranger, friend or soul mate
    public Point last_known_position;
    public boolean chatted; // have we chatted with them before
    public int last_seen; // time when we last saw them
    public int remaining_wisdom; // how long we've spoken with them
    public boolean has_left; // have they left a conversation with us
}

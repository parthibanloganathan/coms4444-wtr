package wtr.g2;

import wtr.sim.Point;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Person {
    public static Set<Integer> valid_wisdoms = new HashSet(Arrays.asList(new int[]{0,10,20,50,200}));
    public static enum Status{STRANGER, FRIEND, SOULMATE};

    public int id;
    public int weight = -1;
    public Status status; // stranger, friend or soul mate
    public Point last_known_position;
    public boolean chatted; // have we chatted with them before
    public int last_seen; // time when we last saw them
    public int time_spoken; // how long we've spoken with them
}

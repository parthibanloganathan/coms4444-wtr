package wtr.g2;

import wtr.sim.Point;

public class Utils {
    public static double dist(Point a, Point b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static void printChatInitiation(Point a, Point b) {
        System.out.println(a.id + " initiating chat with: " + b.id);
    }
}

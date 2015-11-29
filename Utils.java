package wtr.g2;

import wtr.sim.Point;

public class Utils {
    public static int BOARD_SIDE = 20;

    public static double dist(Point a, Point b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static void printChatInitiation(Point a, Point b) {
        System.out.println(a.id + " initiating chat with: " + b.id);
    }

    public static boolean pointsAreSame(Point a, Point b) {
        return a.x == b.x && a.y == b.y;
    }

    public static boolean pointOutOfRange(Point p, double dx, double dy) {
        if (p.x + dx > BOARD_SIDE || p.y + dy > BOARD_SIDE
                || p.x + dx < 0 || p.y + dy < 0) {
            return true;
        }
        return false;
    }
}

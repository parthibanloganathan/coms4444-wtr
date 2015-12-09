package wtr.g2;

import wtr.sim.Point;

import java.util.PriorityQueue;
import java.util.Random;

/**
 * Created by dhruvpurushottam on 12/8/15.
 */
public class SimpleStrategyUtils {

    private static Random random = new Random();

    public static Point bestTarget(Point[] players, int[] chat_ids, Person[] people, Point selfPlayer) {
        PriorityQueue<Point> potentialTargets = new PriorityQueue<>(new TargetComparator(selfPlayer));
        for (Point p : players) {
            if (p.id == selfPlayer.id || people[p.id].remaining_wisdom == 0)
                continue;

            if (Utils.inRange(selfPlayer, p)) {
                potentialTargets.add(p);
            }
        }

        while (!potentialTargets.isEmpty()) {
            Point nextTarget = potentialTargets.poll();
            if (isAvailable(nextTarget.id, players, chat_ids) && people[nextTarget.id].remaining_wisdom != 0) {
                return new Point(0.0, 0.0, nextTarget.id);
            }
        }
        return null;
    }

    /**
     * Go to player with maximum expected remaining wisdom
     */
    public static Point bestTargetToMoveTo(Point[] players, Person[] people, int self_id) {
        Point bestPlayer = null;
        int maxWisdom = 0;
        for (Point p : players) {
            if (p.id == self_id)
                continue;
            int curPlayerRemWisdom = people[p.id].remaining_wisdom;
            if (curPlayerRemWisdom > maxWisdom) {
                maxWisdom = curPlayerRemWisdom;
                bestPlayer = p;
            }
        }
        return bestPlayer;
    }

    /**
     * Is this person already talking to someone
     */
    public static boolean isAvailable(int id, Point[] players, int[] chat_ids){
        int i = 0, j = 0;
        while (players[i].id != id)
            i++;
        while (players[j].id != chat_ids[i])
            j++;
        return i == j;
    }
}

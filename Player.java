package wtr.g2;

import wtr.sim.Point;

import java.util.*;

public class Player implements wtr.sim.Player {

    // Constants
    public static final int PLAYER_RANGE = 6;
    public static final double OUTER_RADIUS = 2;
    public static final double INNER_RADIUS = 0.5;
    public static final double RADIUS_TO_MAINTAIN = 0.6;
    public static final int FRIEND_WISDOM = 50;
    public static final int SOUL_MATE_WISDOM = 400;
    public static final int AVG_STRANGER_WISDOM = 10; // (0n/3 + 10n/3 + 20n/3)/n = 10
    public static final int TOTAL_TIME = 1800;
    public static final double MIN_RADIUS_FOR_CONVERSATION = 0.505; // slight offset for floating point stuff
    public static final double MAX_RADIUS_FOR_CONVERSATION = 2.005;

    private int num_strangers;
    private int num_friends;
    private int n; // total number of people
    private int self_id = -1;
    private int time;
    private Person[] people;
    private int total_wisdom;
    private int total_strangers;
    private int expected_wisdom;

    private Random random = new Random();
    private Point selfPlayer;
    private int soulmateID;
    private int last_time_wisdom_gained;

    private void println(String s) {
        System.out.println(self_id + " : " +"  |  " + s);
    }

    public void init(int id, int[] friend_ids, int strangers) {
        time = 0;
        self_id = id;
        num_strangers = strangers;
        num_friends = friend_ids.length;
        n = num_friends + num_strangers + 2; // people = friends + strangers + soul mate + us
        people = new Person[n];
        total_strangers = num_strangers + 1;
        total_wisdom = AVG_STRANGER_WISDOM*num_strangers + SOUL_MATE_WISDOM; // total wisdom amongst strangers and soul mate
        expected_wisdom = total_wisdom / total_strangers;

        // Initialize strangers and soul mate
        for (int i = 0; i < people.length; i++) {
            Person stranger = new Person();
            stranger.status = Person.Status.STRANGER;
            stranger.id = i;
            stranger.remaining_wisdom = expected_wisdom;
            stranger.wisdom = expected_wisdom;
            stranger.has_left = false;
            stranger.chatted = false;
            people[i] = stranger;
        }

        // Initialize us
        Person us = people[self_id];
        us.status = Person.Status.US;
        us.wisdom = 0;
        us.remaining_wisdom = 0;

        // Initialize friends
        for (int friend_id : friend_ids) {
            Person friend = people[friend_id];
            friend.id = friend_id;
            friend.status = Person.Status.FRIEND;
            friend.wisdom = FRIEND_WISDOM;
            friend.remaining_wisdom = FRIEND_WISDOM;
            friend.has_left = false;
            friend.chatted = false;
        }

        soulmateID = -1;
    }

    public Point play(Point[] players, int[] chat_ids, boolean wiser, int more_wisdom) {
        time++;

        int i = 0;
        int j = 0;
        while (players[i].id != self_id) {
            i++;
        }
        while (players[j].id != chat_ids[i]) {
            j++;
        }

        Point self = players[i];
        Point chat = players[j];

        boolean chatting = (i != j);

        selfPlayer = self;

        // Identify soul mate
        if (more_wisdom > FRIEND_WISDOM) {
            soulmateID = chat.id;
        }

        people[chat.id].remaining_wisdom = more_wisdom;

        // Attempt to continue chatting if there is more wisdom
        if (chatting) {
            // Move closer to prevent others form interrupting
            if (Utils.dist(self, chat) > RADIUS_TO_MAINTAIN) {
                return getCloserToTarget(self, chat);
            }

            // Either continue chatting or wait for some time to continue conversation before leaving
            if (wiser && people[chat.id].remaining_wisdom > 0) {
                last_time_wisdom_gained = time;
                return new Point(0.0, 0.0, chat.id);
            } else if (!wiser && time - last_time_wisdom_gained < wisdomDependentWaitTime(chat)) {
                return new Point(0.0, 0.0, chat.id);
            }
        }
        else { // Try to initiate chat if previously not chatting
            Point closestTarget = bestTarget(players, chat_ids);
            if (closestTarget != null) {
                return closestTarget;
            }

            Point bestTargetToMoveTo = bestTargetToMoveTo(players);
            if (bestTargetToMoveTo != null) {
                return getCloserToTarget(selfPlayer, bestTargetToMoveTo);
            }

            return randomMove(self);
        }

        // Return a random move in the worst case
        return randomMove(self);
    }

    public Point bestTarget(Point[] players, int[] chat_ids) {
        PriorityQueue<Point> potentialTargets = new PriorityQueue<>(new TargetComparator(selfPlayer));
        for (Point p : players) {
            if (p.id == self_id || people[p.id].remaining_wisdom == 0)
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
    private Point bestTargetToMoveTo(Point[] players) {
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
    private boolean isAvailable(int id, Point[] players, int[] chat_ids){
        int i = 0, j = 0;
        while (players[i].id != id)
            i++;
        while (players[j].id != chat_ids[i])
            j++;
        return i == j;
    }

    private Point randomMove(Point self) {
        double theta, dx, dy;
        Point move;
        do {
            theta = random.nextDouble() * 2 * Math.PI;
            dx = PLAYER_RANGE * Math.cos(theta);
            dy = PLAYER_RANGE * Math.sin(theta);
            move = new Point(dx, dy, self_id);
        } while (Utils.pointOutOfRange(self, dx, dy));
        return move;
    }

    public Point getCloserToTarget(Point self, Point target){
        double targetDis = MIN_RADIUS_FOR_CONVERSATION;
        double dis = Utils.dist(self, target);
        double x = (dis - targetDis) * (target.x - self.x) / dis;
        double y = (dis - targetDis) * (target.y - self.y) / dis;
        return new Point(x, y, self_id);
    }

    public int wisdomDependentWaitTime(Point chat) {
        return Math.round(2 + people[chat.id].remaining_wisdom / 20);
    }
}

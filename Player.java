package wtr.g2;

import wtr.sim.Point;

import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;

public class Player implements wtr.sim.Player {

    // Constants
    public static final int PLAYER_RANGE = 6;
    public static final int PATIENCE_IN_TICS = 5;
    public static final double MIN_RADIUS_FOR_CONVERSATION = 0.5;

    // Static vars
    private static int num_strangers;
    private static int num_friends;
    private static int n; // total number of people
    private static Random random = new Random();

    // Player specific variables
    private int self_id = -1;
    private int time;
    private Person[] people;
    private boolean stationaryLastTurn;
    private Point prevPos;
    private int last_chatted;
    private int last_time_chatted;
    private double expected_wisdom;

    private Queue<Point> prevLocations;
    private Point locationXTicsAgo;

    public void init(int id, int[] friend_ids, int strangers) {
        time = 0;
        self_id = id;
        stationaryLastTurn = true;
        num_strangers = strangers;
        num_friends = friend_ids.length;
        n = num_friends + num_strangers + 2; // people = friends + strangers + soul mate + us
        people = new Person[n];
        expected_wisdom = 10;
        prevLocations = new LinkedList<>();

        for (int i = 0; i < people.length; i++) {
            Person p = new Person();
            p.status = Person.Status.STRANGER;
            p.id = i;
            p.remaining_wisdom = -1;
            p.wisdom = -1;
            p.has_left = false;
            p.chatted = false;
            people[i] = p;
        }

        Person us = people[self_id];
        us.status = Person.Status.US;
        us.wisdom = 0;
        us.remaining_wisdom = 0;

        for (int friend_id : friend_ids) {
            Person friend = people[friend_id];
            friend.id = friend_id;
            friend.status = Person.Status.FRIEND;
            //TODO: may not need both wisdom and remaining_wisdom
            friend.wisdom = 50;
            friend.remaining_wisdom = 50;
            last_chatted = -1;
        }
    }

    public boolean blocked(Point[] players, int target_id, double threshold) {
        for (int i=0; i<players.length; i++) {
            if (players[i].id == target_id) {continue;}
            if ( Math.pow(players[i].x - players[target_id].x,2) + Math.pow(players[i].y - players[target_id].y,2) < threshold*threshold) {
                return true;
            }
        }
        return false;
    }

    public Point play(Point[] players, int[] chat_ids, boolean wiser, int more_wisdom) {
        time++;
        // find where you are and who you chat with
        int i = 0, j = 0;
        while (players[i].id != self_id)
            i++;
        while (players[j].id != chat_ids[i])
            j++;
        Point self = players[i];
        Point chat = players[j];
        people[chat.id].remaining_wisdom = more_wisdom;
        boolean chatting = (i != j);

        if (prevLocations.size() == 4) {
            prevLocations.add(self);
            locationXTicsAgo = prevLocations.poll();
        }

        if (chatting) {
            // attempt to continue chatting if there is more wisdom
            if (wiser) {
                last_chatted = chat.id;
                last_time_chatted = time;
                return new Point(0.0, 0.0, chat.id);
            }
            else { //wait some time before leaving conversation
                if (time - last_time_chatted < PATIENCE_IN_TICS) {
                    return new Point(0.0, 0.0, chat.id);
                }
            }
        }
        else {
            // See if other player left because we have no wisdom remaining to give
            if (last_chatted != -1 && (people[last_chatted].remaining_wisdom == 9 || people[last_chatted].remaining_wisdom == 19) ) {
                people[last_chatted].has_left = true;
                last_chatted = -1;
            }

            // try to initiate chat if previously not chatting
            // Use a pq to sort potential targets by closest distance
            PriorityQueue<Point> potentialTargets = new PriorityQueue<>(new TargetComparator(self));
            for (Point p : players) {
                if (people[p.id].remaining_wisdom == 0)
                    continue;

                double dis = Math.sqrt(Utils.dist(self, p));
                if (dis <= 2.0 && dis >= 0.5) {
                    potentialTargets.add(p);
                }
            }

            while (!potentialTargets.isEmpty()) {
                Point nextTarget = potentialTargets.poll();
                if (isAvailable(nextTarget.id, players, chat_ids)) {
                    Utils.printChatInitiation(self, nextTarget);
                    return new Point(0.0, 0.0, nextTarget.id);
                }
            }

            //Could not find a chat, so plan next move
            Point bestPlayer = chooseBestPlayer(players);
            if (bestPlayer != null) {
                return moveToOtherPlayer(self, bestPlayer);
            }
        }

//        If we haven't moved in some time, initiate random move
//        if (time % 3 == 0) {
//            if (prevPos != null && prevPos.x == self.x && prevPos.y == self.y) {
//                return randomMove(PLAYER_RANGE);
//            }
//            prevPos = self;
//        }

        //If all else fails
        return randomMove(PLAYER_RANGE);
    }

    private Point chooseBestPlayer(Point[] players) {
        Point bestPlayer = null;
        int maxWisdom = 0;
        for (Point p : players) {
            if (p.id == self_id)
                continue;
            int curPlayerRemWisdom = people[p.id].remaining_wisdom;
            if (curPlayerRemWisdom > maxWisdom && !people[p.id].has_left) {
                maxWisdom = curPlayerRemWisdom;
                bestPlayer = p;
            }
        }
        return bestPlayer;
    }

    private Point moveToOtherPlayer(Point us, Point them) {
        double dis = Utils.dist(us, them)/2 - MIN_RADIUS_FOR_CONVERSATION;
        double dx = them.x - us.x;
        double dy = them.y - us.y;
        double theta = Math.atan2(dy, dx);
        return new Point(us.x + dis * Math.cos(theta), us.y + dis * Math.sin(theta), self_id);
    }

    private Point randomMove(int maxDist) {
        stationaryLastTurn = !stationaryLastTurn;
        double dir = random.nextDouble() * 2 * Math.PI;
        double dx = maxDist * Math.cos(dir);
        double dy = maxDist * Math.sin(dir);
        return new Point(dx, dy, self_id);
    }

    //From g5. Throws exceptions once in a while for some reason
    private boolean isAvailable(int id, Point[] players, int[] chat_ids){
        int i = 0, j = 0;
        try {
            while (players[i].id != id)
                i++;
            while (players[j].id != chat_ids[i])
                j++;
        }
        catch (IndexOutOfBoundsException e) {
            return true;
        }
        return i == j;
    }

}

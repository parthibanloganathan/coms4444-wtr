package wtr.g2;

import wtr.sim.Point;

import java.util.*;

public class Player implements wtr.sim.Player {

    // Constants
    public static final int PLAYER_RANGE = 6;
    public static final int PATIENCE_TIME = 5;
    public static final double OUTER_RADIUS = 2;
    public static final double INNER_RADIUS = 0.5;
    public static final int FRIEND_WISDOM = 50;
    public static final int SOUL_MATE_WISDOM = 400;
    public static final int AVG_STRANGER_WISDOM = 10; // (0n/3 + 10n/3 + 20n/3)/n = 10
    public static final int GIVE_UP_TIME = 4; // time before giving up and moving on from a non-productive situation
    public static final int TOTAL_TIME = 1800;
    public static final double MIN_RADIUS_FOR_CONVERSATION = 0.505; // slight offset for floating point stuff
    public static final double MAX_RADIUS_FOR_CONVERSATION = 2.005;

    // Static vars

    private int num_strangers;
    private int num_friends;
    private int n; // total number of people
    private int self_id = -1;
    private int time;
    private Person[] people;
    private int total_wisdom;
    private int total_strangers;
    private int expected_wisdom;

    // random generator
    private Random random = new Random();

    private HashSet<Integer> friendSet;
    private int interfereThreshold = 5;
    private int interfereCount = 0;
    private Integer preChatId;
    private Point selfPlayer;
    private Integer numberOfStrangers;
    private Integer totalNumber;
    private int soulmateID;

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

        // From g5
        friendSet = new HashSet<Integer>();
        // initialize the wisdom array
        int N = friend_ids.length + strangers + 2;
        totalNumber = N;
        // initialize strangers' wisdom to 5.5 (avg wisdom for 1/3 + 1/3 + 1/3 configuration)
        for (int friend_id : friend_ids){
            friendSet.add(friend_id);
        }
        preChatId = self_id;
        numberOfStrangers = strangers + 1;
        soulmateID = -1;
    }

    public Point play(Point[] players, int[] chat_ids, boolean wiser, int more_wisdom) {
        System.out.println("G5");
        time++;
        int i = 0, j = 0;
        while (players[i].id != self_id) i++;
        while (players[j].id != chat_ids[i]) j++;
        Point self = players[i];
        Point chat = players[j];
        boolean chatting = (i != j);

        selfPlayer = self;
        //soul mate
        if (more_wisdom > 50 && !friendSet.contains(chat.id) && soulmateID < 0) {
            soulmateID = chat.id;
            friendSet.add(chat.id);
        }

        // record known wisdom
        people[chat.id].remaining_wisdom = more_wisdom;
        // attempt to continue chatting if there is more wisdom

        if (chat.id != preChatId)
            interfereCount = 0;
        if (!wiser && (friendSet.contains(chat.id) && people[chat.id].remaining_wisdom > 0)) {
            interfereCount++;
        }
        if (wiser || (friendSet.contains(chat.id) && people[chat.id].remaining_wisdom > 0)) {
            if (!wiser && interfereCount >= interfereThreshold) {
                //If two friends has been interfered more than 5 times, then move away
                return randomMove(self);
            } else {
                preChatId = chat.id;
                if (Utils.dist(self, chat) > 0.6) {
                    Point ret = getCloserToTarget(self, chat);
                    return ret;
                }
                return new Point(0.0, 0.0, chat.id);
            }
        }
        // try to initiate chat if previously not chatting
        if (!chatting) {
            Point closestTarget = pickTarget1(players, chat_ids);
            if (closestTarget == null) {
                Point maxWisdomTarget = pickTarget2(players, chat_ids);
                if (maxWisdomTarget == null) {
                    // jump to random position
                    return randomMove(self);
                } else {
                    // get closer to maxWisdomTarget
                    return getCloserToTarget(selfPlayer, maxWisdomTarget);
                }
            } else {
                return closestTarget;
            }

        }
        // return a random move
        return randomMove(self);
    }

    /*
        Tried using this instead of pickTarget1 but it decreased our score by 100 pts
     */
    public Point bestTarget(Point[] players, int[] chat_ids) {
        PriorityQueue<Point> potentialTargets = new PriorityQueue<>(new TargetComparator(selfPlayer));
        for (Point p : players) {
            if (people[p.id].remaining_wisdom == 0)
                continue;

            if (Utils.inRange(selfPlayer, p)) {
                potentialTargets.add(p);
            }
        }

        while (!potentialTargets.isEmpty()) {
            Point nextTarget = potentialTargets.poll();
            if (isAvailable(nextTarget.id, players, chat_ids)) {
                if (!(people[nextTarget.id].remaining_wisdom == 0)){
                    return new Point(0.0, 0.0, nextTarget.id);
                }
            }
        }
        return null;
    }

    public Point pickTarget1(Point[] players, int[] chat_ids){
        double minDis = Double.MAX_VALUE;
        int targetId = 0;
        boolean find = false;
        for (Point p : players) {
            if(p.id == selfPlayer.id)
                continue;
            // compute squared distance
            double dx = selfPlayer.x - p.x;
            double dy = selfPlayer.y - p.y;
            double dd = dx * dx + dy * dy;
            if(dd < .25)
                return null;
            if (dd >= 0.25 && dd <= 4.0 && dd < minDis){
                find = true;
                targetId = p.id;
                minDis = dd;
            }
        }
        if(find && isAvailable(targetId, players, chat_ids) && people[targetId].remaining_wisdom != 0){
            preChatId = targetId;
            return new Point(0.0, 0.0, targetId);
        }
        return null;
    }

    public Point pickTarget2(Point[] players, int[] chat_ids){
        int maxWisdom = 0;
        Point maxTarget = null;

        for (int i = 0; i < players.length; i++){
            Point player = players[i];
            // not conversing with anyone
            if (player.id != chat_ids[i])
                continue;
            // swap with maxWisdom and maxTarget if wiser
            if (people[player.id].remaining_wisdom > maxWisdom) {
                maxWisdom = people[player.id].remaining_wisdom;
                maxTarget = player;
            }
        }
        return maxTarget;
    }

    private boolean isAvailable(int id, Point[] players, int[] chat_ids){
        int i = 0, j = 0;
        while (players[i].id != id)
            i++;
        while (players[j].id != chat_ids[i])
            j++;
        return i == j;
    }

    private Point randomMove(Point self) {
        double dir, dx, dy;
        Point rand;
        do {
            dir = random.nextDouble() * 2 * Math.PI;
            dx = PLAYER_RANGE * Math.cos(dir);
            dy = PLAYER_RANGE * Math.sin(dir);
            rand = new Point(dx, dy, self_id);
        } while (Utils.pointOutOfRange(self, dx, dy));
        return rand;
    }

    private Point moveToOtherPlayer(Point us, Point them) {
        double dis = Utils.dist(us, them)/2 - MIN_RADIUS_FOR_CONVERSATION;
        double dx = them.x - us.x;
        double dy = them.y - us.y;
        double theta = Math.atan2(dy, dx);
        return new Point(us.x + dis * Math.cos(theta), us.y + dis * Math.sin(theta), self_id);
    }

    public Point getCloserToTarget(Point self, Point target){
        //can't set to 0.5, if 0.5 the result distance may be 0.49
        double targetDis = MIN_RADIUS_FOR_CONVERSATION;
        double dis = Utils.dist(self, target);
        double x = (dis - targetDis) * (target.x - self.x) / dis;
        double y = (dis - targetDis) * (target.y - self.y) / dis;
        return new Point(x, y, self_id);
    }
}

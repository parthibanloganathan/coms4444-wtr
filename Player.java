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

    // the remaining wisdom per player
    private int[] W = null;

    // random generator
    private Random random = new Random();

    private HashSet<Integer> friendSet;

    private int interfereThreshold = 5;

    private int interfereCount = 0;
    private Integer preChatId;
    private Point selfPlayer;
    private HashSet<Integer> alreadyTalkedStrangers;
    private Double strangerUnknowWisdom;
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
        W = new int [N];
        // initialize strangers' wisdom to 5.5 (avg wisdom for 1/3 + 1/3 + 1/3 configuration)
        int stranger_wisdom = (int) (5.5*strangers + 200)/(strangers+1);
        // debug("strangerWisdom: "+stranger_wisdom);
        for (int i = 0 ; i != N ; ++i)
            W[i] = i == self_id ? 0 : stranger_wisdom;
        for (int friend_id : friend_ids){
            friendSet.add(friend_id);
            W[friend_id] = 50;
        }
        preChatId = self_id;
        alreadyTalkedStrangers = new HashSet<Integer>();
        strangerUnknowWisdom = strangers * 5.5 + 200;
        numberOfStrangers = strangers + 1;
        soulmateID = -1;
    }

    public Point play(Point[] players, int[] chat_ids, boolean wiser, int more_wisdom) {
        time++;
        // find where you are and who you chat with
        // for(int i = 0; i < players.length; ++i) {
        // 	if(players[i].id != i)
        // 		System.out.println("ID NOT I: "+players[i].id+"\t"+i);
        // }
        int i = 0, j = 0;
        while (players[i].id != self_id) i++;
        while (players[j].id != chat_ids[i]) j++;

        Point self = players[i];
        Point chat = players[j];

        selfPlayer = self;
        //soul mate
        if (more_wisdom > 50 && !friendSet.contains(chat.id) && soulmateID < 0) {
            alreadyTalkedStrangers.add(chat.id);
            soulmateID = chat.id;
            friendSet.add(chat.id);
            // Keep track of soulmate, so you only subtract the 200 one time.
            strangerUnknowWisdom -= 200;
        } else if (chat.id != self_id && !friendSet.contains(chat.id) && !alreadyTalkedStrangers.contains(chat.id)) {
            alreadyTalkedStrangers.add(chat.id);
            // If they have 20 wisdom to share with us, on average this conversation will give us 10 wisdom
            // (since they may have less than 20 to get from us!)
            if (more_wisdom > 10)
                strangerUnknowWisdom -= 10;
                // If they have 10 wisdom to give us, on average, this conversation will give us 6.6666 wisdom
                // (since they have 1/3 chance to get nothing from us!)
            else if (more_wisdom > 0)
                strangerUnknowWisdom -= 6.6666;
        }

        // record known wisdom
        W[chat.id] = more_wisdom;
        //TODO remove from blacklist
        // attempt to continue chatting if there is more wisdom
        // System.out.println("wise: " + wiser + " selfid " + self_id + " chatid " + chat.id + " W " + W[chat.id]);
        updateStrangerWisdom();

        if (chat.id != preChatId)
            interfereCount = 0;
        if (!wiser && (friendSet.contains(chat.id) && W[chat.id] > 0)) {
            interfereCount++;
        }
        if (wiser || (friendSet.contains(chat.id) && W[chat.id] > 0)) {
            if (!wiser && interfereCount >= interfereThreshold) {
                //If two friends has been interfered more than 5 times, then move away
                return randomMoveInRoom(self);
            } else {
                preChatId = chat.id;
                System.out.println("DIST: " + distance(self, chat));
                if (distance(self, chat) > 0.6) {
                    Point ret = getCloserWithID(self, chat, self.id);
                    return ret;
                }
                return new Point(0.0, 0.0, chat.id);
            }
        }
        // try to initiate chat if previously not chatting
        if (i == j) {
            Point closestTarget = pickTarget1(players, chat_ids);
            if (closestTarget == null) {

                Point maxWisdomTarget = pickTarget2(players, 6, chat_ids);
                if (maxWisdomTarget == null) {
                    // jump to random position
                    return randomMoveInRoom(self);
                } else {
                    // get closer to maxWisdomTarget
                    return getCloser(selfPlayer, maxWisdomTarget);
                }
            } else {
                return closestTarget;
            }

        }
        // return a random move
        return randomMoveInRoom(self);
    }

    public void updateStrangerWisdom(){

        int cur_stranger_wisdom = (int) (strangerUnknowWisdom / numberOfStrangers);
        for(int i = 0; i < totalNumber; i++){
            if(friendSet.contains(i) || alreadyTalkedStrangers.contains(i) || i == self_id)
                continue;
            W[i] = cur_stranger_wisdom;
        }
    }

    public Point randomMoveInRoom(Point current) {
        Point move = randomMove();
        while(move.x + current.x > 20 || move.y + current.y > 20 || move.x + current.x < 0 || move.y + current.y < 0) {
            move = randomMove();
        }
        // System.out.println("Self " + self_id + " Moving");
        return move;
    }

    private Point randomMove(){
        double dir = random.nextDouble() * 2 * Math.PI;
        double dx = 6 * Math.cos(dir);
        double dy = 6 * Math.sin(dir);
        preChatId = self_id;
        return new Point(dx, dy, self_id);
    }

    public boolean isAlone(Integer id, Point[] players, int[] chat_ids){
        int i = 0, j = 0;
        while (players[i].id != id) i++;
        while (players[j].id != chat_ids[i]) j++;
        return i == j;

    }

    public Point pickTarget1(Point[] players, int[] chat_ids){
        Point self = selfPlayer;
        double minDis = Double.MAX_VALUE;
        int targetId = 0;
        boolean find = false;
        for (Point p : players) {
            if(p.id == self.id)
                continue;
            // compute squared distance
            double dx = self.x - p.x;
            double dy = self.y - p.y;
            double dd = dx * dx + dy * dy;
            if(dd < .25)
                return null;
            if (dd >= 0.25 && dd <= 4.0 && dd < minDis){
                find = true;
                targetId = p.id;
                minDis = dd;
            }
        }
        if(find && isAlone(targetId, players, chat_ids) && W[targetId] != 0){
            preChatId = targetId;
            return new Point(0.0, 0.0, targetId);
        }
        return null;
    }

    public Point pickTarget2(Point[] players, double distance, int[] chat_ids){
        int maxWisdom = 0;
        Point maxTarget = null;

        if (distance > 6.0)
            distance = 6.0;

        for (int i = 0; i < players.length; i++){

            // not conversing with anyone
            if (players[i].id != chat_ids[i])
                continue;
            // swap with maxWisdom and maxTarget if wiser
            if (W[players[i].id] > maxWisdom) {

                maxWisdom = W[players[i].id];
                maxTarget = players[i];
            }
        }


        return maxTarget;
    }

    public Point getCloser(Point self, Point target){
        //can't set to 0.5, if 0.5 the result distance may be 0.49
        double targetDis = 0.52;
        double dis = distance(self, target);
        double x = (dis - targetDis) * (target.x - self.x) / dis;
        double y = (dis - targetDis) * (target.y - self.y) / dis;
        System.out.println("self pos: " + self.x + ", " + self.y);
        System.out.println("target pos: " + target.x + ", " + target.y);
        System.out.println("move pos: " + x + ", " + y);
        return new Point(x, y, self_id);
    }

    public Point getCloserWithID(Point self, Point target, int id) {
        double targetDis = 0.6;
        double dis = distance(self, target);
        double x = (dis - targetDis) * (target.x - self.x) / dis;
        double y = (dis - targetDis) * (target.y - self.y) / dis;
        return new Point(x, y, id);
    }

    public double distance(Point p1, Point p2){
        double dx = p1.x - p2.x;
        double dy = p1.y - p2.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}

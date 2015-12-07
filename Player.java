package wtr.g2;

import wtr.sim.Point;

import java.util.*;

public class Player implements wtr.sim.Player {

    // Constants
    public static final int PLAYER_RANGE = 6;
    public static final double GAP_BETWEEN_PAIRS = 0.515;
    public static final double OUTER_RADIUS = 1.995; // 2 but account for floating point error
    public static final double INNER_RADIUS = 0.505; // 0.5 but account for floating point error
    public static final double RADIUS_TO_MAINTAIN = 0.6;
    public static final int FRIEND_WISDOM = 50;
    public static final int SOUL_MATE_WISDOM = 400;
    public static final int AVG_STRANGER_WISDOM = 10; // (0n/3 + 10n/3 + 20n/3)/n = 10
    public static final double MARGIN = INNER_RADIUS * 0.5 + GAP_BETWEEN_PAIRS * 0.5;
    private static final int salt = 3456203; //change to a different random integer for every submission

    // Static vars
    private Random jointRandom = null;
    private Random thisRandom = null;
    private Point offset;
    private Point offsetPerp;

    private int num_strangers;
    private int num_friends;
    private int n; // total number of people
    private int self_id = -1;
    private int time;
    private Person[] people;
    private int total_wisdom;
    private int total_strangers;
    private int total_unknowns;
    private int expected_wisdom;
    private boolean coop = true;
    private int numInstances = 0;
    private Point stackPoint = null;
    private int migrationStartTime = 0;
    private int numSlots;
    private boolean initialized = false;
    private boolean nextToWall = true;

    private Random random = new Random();
    private Point selfPlayer;
    private int soul_mate_id = -1;
    private int last_person_chatted_id = -1;
    private int last_time_wisdom_gained;
    private int times_nothing_to_do = 0;

    private HashSet<Integer> avoid_list = new HashSet<>();

    private void println(String s) {
        System.out.println(self_id + " : " + "  |  " + s);
    }

    public void init(int id, int[] friend_ids, int strangers) {
        time = 0;
        self_id = id;
        jointRandom = new Random(System.currentTimeMillis() / 1000 + salt);
        thisRandom = new Random(id * System.currentTimeMillis());
        num_strangers = strangers;
        num_friends = friend_ids.length;
        n = num_friends + num_strangers + 2; // people = friends + strangers + soul mate + us
        people = new Person[n];
        total_strangers = num_strangers + 1;
        total_unknowns = total_strangers;
        total_wisdom = AVG_STRANGER_WISDOM*num_strangers + SOUL_MATE_WISDOM; // total wisdom amongst unknowns
        expected_wisdom = total_wisdom / total_unknowns;

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
    }

    private Point generateRandomWallPoint() {
        int wall = jointRandom.nextInt(4);
        double cutoff = 6;
        switch (wall) {
            case 0:
                offset = new Point(GAP_BETWEEN_PAIRS,0, self_id);
                offsetPerp = new Point(0,INNER_RADIUS,self_id);
                return new Point(jointRandom.nextDouble() * cutoff, 0.001, self_id);
            case 1:
                offset = new Point(0,GAP_BETWEEN_PAIRS,self_id);
                offsetPerp = new Point(-INNER_RADIUS,0,self_id);
                return new Point(19.999, jointRandom.nextDouble() * cutoff, self_id);
            case 2:
                offset = new Point(GAP_BETWEEN_PAIRS,0,self_id);
                offsetPerp = new Point(0,-INNER_RADIUS,self_id);
                return new Point(jointRandom.nextDouble() * cutoff, 19.999, self_id);
            case 3:
                offset = new Point(0,GAP_BETWEEN_PAIRS,self_id);
                offsetPerp = new Point(INNER_RADIUS,0,self_id);
                return new Point(0.001, jointRandom.nextDouble() * cutoff, self_id);
            default:
                return null;
        }
    }

    public Point coopPlay(Point[] players, int[] chat_ids, boolean wiser, int more_wisdom) {
	if (wiser) {last_time_wisdom_gained = time;}
        int i = 0, j = 0;
        while (players[i].id != self_id)
            i++;
        while (players[j].id != chat_ids[i])
            j++;
        Point self = players[i];
        Point chat = players[j];
        people[chat.id].remaining_wisdom = more_wisdom;
        if (numInstances == 0) {
            if (stackPoint == null) {
                migrationStartTime = time;
                stackPoint = generateRandomWallPoint();
                return migrateTo(stackPoint, self);
            }
            if (time - migrationStartTime < 6) {
                Point next = migrateTo(stackPoint, self);
                //return migrateTo(stackPoint, self);
                return next;
            }
            for (Point p : players) {
                if (Utils.dist(p, self) < 0.01) {
                    numInstances++;
                }
            }
            numSlots = (int)Math.ceil(3.0 * (double)numInstances / 4.0);
        }
        //count number of people with range
        int count = 0;
        int wisdom = 0;
        int id = 0;
        int stackedCount = 0;
        int stackedID = 0;
        for (Point p: players) {
            if (p.id != self_id && Utils.dist(self, p) < MARGIN) {
                count++;
                if (people[p.id].remaining_wisdom != 0) {
                    id = p.id;
                    wisdom += people[id].remaining_wisdom;
                }
            }
            if (p.id != self_id && Utils.dist(self, p) < 0.2) {
                stackedCount++;
                stackedID = p.id;
            }
        }
        // if only one person in range and has wisdom, talk to person. if stacked, person with lower ID moves farther away to talk
        if (count == 1 && wisdom > 0) {
	    times_nothing_to_do = 0;	    
            if (stackedCount == 0) {
                println("time:" + time + " - id:" + self_id + ": talk to " + id);
                return new Point(0,0,id);
            }
            else {
                if (self_id < stackedID) {
                    println("time:" + time + " - id:" + self_id + ": moving to talk to " + stackedID);
                    if (nextToWall) {
                        nextToWall = false;
                        return offsetPerp;
                    }
                    else {
                        nextToWall = true;
                        return add(new Point(0,0,0), offsetPerp, -1);
                    }
                }
                else {
                    return new Point(0,0, stackedID);
                }
            }
        }
        // if alone in slot, wait for someone else to come
        if (count == 0 && thisRandom.nextDouble() > 0.11111111) {
            println("time:" + time + " - id:" + self_id + ": alone, waiting");
            return new Point(0,0,id);
        }
        // if have wisdom in range, move with probability (num people - )2 / num people
        if (wisdom > 0 && count > 1) {
	    times_nothing_to_do = 0;	    
            if (thisRandom.nextDouble() > ((double)count - 1.0) / (double)count + 1.0) {
                println("time:" + time + " - id:" + self_id + ": too many people, chosen to stay");
                return new Point(0,0,id);
            }
        }
        // if no wisdom in range, or if chosen to stay by above, leave
        ArrayList<Point> slots = new ArrayList<Point>();
        ArrayList<Point> emptySlots = new ArrayList<Point>();
        for (int k=0; k<numSlots; k++) {
            Point kslot = add(stackPoint, offset, k);
            if (Utils.dist(kslot, self) > 5.9999999) {
                continue;
            }
            count = 0;
            wisdom = 0;
            for (Point p:players) {
                if (Utils.dist(kslot,p) < MARGIN) {
                    if (p.id == self_id) {count = 2;}
                    count++;
                    if (people[id].remaining_wisdom != 0) {
                        id = p.id;
                        wisdom += people[id].remaining_wisdom;
                    }
                }
            }
            //println();("time:" + time + " - id:" + self_id + " slot " + k + " # " + count);
            if ((count == 1) && wisdom > 0) {
                slots.add(kslot);
            }
            else if (count == 0) {
                emptySlots.add(kslot);
            }
        }
        if (emptySlots.size() > 0 && (slots.size() == 0 || thisRandom.nextDouble() < 0.111111111)) { // if no available slots, or with probability 1/9, create a new slot
	    times_nothing_to_do = 0;	    
            println("time:" + time + " - id:" + self_id + ": moving to empty slot");
            nextToWall = true;
            return migrateTo(emptySlots.get(thisRandom.nextInt(emptySlots.size())),self);
        }
        if (slots.size() == 0) {
            println("time:" + time + " - id:" + self_id + ": nothing to do");
	    if (++times_nothing_to_do > 10) {coop = false;}
            return new Point(0,0,self_id);
        }
	times_nothing_to_do = 0;	
        int r = thisRandom.nextInt(slots.size());
        Point target = slots.get(r);
        for (Point p:players) {
            if (Utils.dist(target,p) < 0.1) {
                println("time:" + time + " - id:" + self_id + ": move perpendicular to " + (target.x + offsetPerp.x) + ", " + (target.y + offsetPerp.y));
                nextToWall = false;
                return migrateTo(add(target, offsetPerp, 1), self);
            }
        }
        println("time:" + time + " - id:" + self_id + ": move to " + target.x + ", " + target.y);
        nextToWall = true;
        return migrateTo(target, self);
    }

    public Point add(Point start, Point vector, double scalar) {
        return new Point(start.x + vector.x*scalar, start.y + vector.y*scalar, self_id);
    }

    public Point migrateTo(Point target, Point current) {
        double distance = Utils.dist(target, current);
        return new Point( (target.x-current.x)*5.999 / Math.max(distance,5.999), (target.y-current.y)*5.999/Math.max(distance,5.999), self_id);
    }

    public Point play(Point[] players, int[] chat_ids, boolean wiser, int more_wisdom) {
        time++;
        if (coop) {return coopPlay(players, chat_ids, wiser, more_wisdom);}
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
            people[chat.id].status = Person.Status.SOULMATE;
            soul_mate_id = chat.id;
        }

        // Note if we've chatted with them to update expected values
        Person person_chatting_with = people[chat.id];
        if (!person_chatting_with.chatted) {
            person_chatting_with.chatted = true;

            // If it's a stranger, update the expected wisdom for all unknowns
            if (person_chatting_with.status == Person.Status.STRANGER) {
                updateExpectedWisdom(more_wisdom);
            }
        }

        // Update remaining wisdom
        people[chat.id].remaining_wisdom = more_wisdom;

        // Attempt to continue chatting if there is more wisdom
        if (chatting) {
            last_person_chatted_id = chat.id;

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
        else {
            // See if other player left because we have no wisdom remaining to give
            if (last_person_chatted_id != -1 &&
                    (people[last_person_chatted_id].remaining_wisdom == 9 ||
                            people[last_person_chatted_id].remaining_wisdom == 19) ) {
                println("stopped talking to us with: " + people[last_person_chatted_id].remaining_wisdom);
                people[last_person_chatted_id].has_left = true;
                last_person_chatted_id = -1;
            }

            // Try to initiate chat with person in range if previously not chatting
            Point closestTarget = bestTarget(players, chat_ids);
            if (closestTarget != null) {
                return closestTarget;
            }

            // Else move to some one else to talk with
            Point bestTargetToMoveTo = bestTargetToMoveTo(players);
            if (bestTargetToMoveTo != null) {
                return getCloserToTarget(selfPlayer, bestTargetToMoveTo);
            }
        }

        // Return a random move in the worst case
        return randomMove(self);
        }

        /**
         * Pick best person to talk to who's in range
         */
    public Point bestTarget(Point[] players, int[] chat_ids) {
        PriorityQueue<Point> potentialTargets = new PriorityQueue<>(new TargetComparator(selfPlayer));
        for (Point p : players) {
            if (p.id == self_id || people[p.id].remaining_wisdom == 0) {
                continue;
            }

            if (Utils.inRange(selfPlayer, p)) {
                // If soul mate is in range, always attempt to speak with them.
                if (p.id == soul_mate_id) {
                    return new Point(0.0, 0.0, p.id);
                }
                potentialTargets.add(p);
            }
        }

        Point ignored_person = null;

        while (!potentialTargets.isEmpty()) {
            Point nextTarget = potentialTargets.poll();
            if (isAvailable(nextTarget.id, players, chat_ids) && people[nextTarget.id].remaining_wisdom != 0) {

                // If this is a person who has walked away from us, we will only attempt to talk to them
                // if there is no one better.
                if (people[nextTarget.id].has_left) {
                    ignored_person = nextTarget;
                } else {
                    return new Point(0.0, 0.0, nextTarget.id);
                }
            }
        }

        // Go to ignored person if no one else is available
        if (ignored_person != null) {
            return new Point(0.0, 0.0, ignored_person.id);
        } else {
            return null;
        }
    }

    /**
     * Go to player with maximum expected remaining wisdom keeping in mind ignored players
     */
    private Point bestTargetToMoveTo(Point[] players) {
        Point bestPlayer = null;
        Point bestIgnoredPlayer = null;
        int maxWisdom = 0;
        for (Point p : players) {
            if (p.id == self_id)
                continue;
            int curPlayerRemWisdom = people[p.id].remaining_wisdom;
            if (curPlayerRemWisdom > maxWisdom) {
                maxWisdom = curPlayerRemWisdom;

                // If this is a person who has walked away from us, we will only move to them
                // if there is no one better.
                if (people[p.id].has_left) {
                    bestIgnoredPlayer = p;
                } else {
                    bestPlayer = p;
                }
            }
        }

        if (maxWisdom > 0) {
            return bestPlayer;
        } else {
            return bestIgnoredPlayer;
        }
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
        double targetDis = INNER_RADIUS;
        double dis = Utils.dist(self, target);
        double x = (dis - targetDis) * (target.x - self.x) / dis;
        double y = (dis - targetDis) * (target.y - self.y) / dis;
        return new Point(x, y, self_id);
    }

    public int wisdomDependentWaitTime(Point chat) {
        return Math.round(2 + people[chat.id].remaining_wisdom / 20);
    }

    public void updateExpectedWisdom(int new_found_wisdom) {
        total_wisdom -= new_found_wisdom;
        total_unknowns--;
        expected_wisdom = total_wisdom / total_unknowns;

        for (Person p : people) {
            if (p.status == Person.Status.STRANGER && p.chatted == false) {
                p.remaining_wisdom = expected_wisdom;
            }
        }
    }
}

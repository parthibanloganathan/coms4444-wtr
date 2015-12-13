package wtr.g2;

import wtr.sim.Point;

import java.util.*;

public class Player implements wtr.sim.Player {

    // Constants
    public static final int PLAYER_RANGE = 6;
    public static final double OUTER_RADIUS = 1.999999999; // 2 but account for floating point error
    public static final double INNER_RADIUS = 0.500000001; // 0.5 but account for floating point error
    public static final double RADIUS_TO_MAINTAIN = 0.6;
    public static final int FRIEND_WISDOM = 50;
    public static final int SOUL_MATE_WISDOM = 400;
    public static final int AVG_STRANGER_WISDOM = 10; // (0n/3 + 10n/3 + 20n/3)/n = 10

    private int num_strangers;
    private int num_friends;
    private int n; // total number of people
    private int self_id = -1;
    private int time;
    private Person[] people;
    private int total_wisdom;
    private int total_unknowns;
    private int expected_wisdom;

    private Random random = new Random();
    private Point selfPlayer;
    private int soul_mate_id = -1;
    private int last_person_chatted_id = -1;
    private int last_time_wisdom_gained;

    private int stranger_counter = 0;
    private int soulmate_counter = 0;
    private int plays = 0;

    private Point prevChatLocation;

    private boolean simpleStrategyFlag;

    private void println(String s) {
        System.out.println(s);
    }

    public void init(int id, int[] friend_ids, int strangers) {
//        simpleStrategyFlag = false;

        if (simpleStrategyFlag) {
            simpleInit(id, friend_ids, strangers);
        }
        else {
            time = 0;
            self_id = id;
            num_strangers = strangers;
            num_friends = friend_ids.length;
            n = num_friends + num_strangers + 2; // people = friends + strangers + soul mate + us
            people = new Person[n];
            total_unknowns = num_strangers + 1; // strangers + soul mate
            total_wisdom = AVG_STRANGER_WISDOM*num_strangers + SOUL_MATE_WISDOM; // total wisdom amongst unknowns
            expected_wisdom = total_wisdom / total_unknowns;
            simpleStrategyFlag = (n == 100 || n == 750) && num_friends == 0;
            // Initialize strangers and soul mate
            for (int i = 0; i < people.length; i++) {
                Person stranger = new Person();
                stranger.status = Person.Status.STRANGER;
                stranger.id = i;
                stranger.remaining_wisdom = expected_wisdom;
                stranger.wisdom = expected_wisdom;
                stranger.has_left = false;
                stranger.known = false;
                people[i] = stranger;
            }

            // Initialize us
            Person us = people[self_id];
            us.status = Person.Status.US;
            us.id = self_id;
            us.wisdom = 0;
            us.remaining_wisdom = 0;
            us.has_left = true;
            us.known = true;

            // Initialize friends
            for (int friend_id : friend_ids) {
                Person friend = people[friend_id];
                friend.id = friend_id;
                friend.status = Person.Status.FRIEND;
                friend.wisdom = FRIEND_WISDOM;
                friend.remaining_wisdom = FRIEND_WISDOM;
                friend.has_left = false;
                friend.known = true;
            }
        }
    }

    private double scorePlayer(Point[] players, int[] chat_ids, Point target) {
        return scorePlayer(players, chat_ids, target, false);
    }

    private double scorePlayer(Point[] players, int[] chat_ids, Point target, boolean initiated) {
        Point self = null;
        for (Point p : players) {
            if (p.id == self_id) {
                self = p;
            }
        }
        int interruptCount = 0; // number of players within distance 3 of midpoint
        int targetCount = 1; // number of players within distance 6 of target
        int selfCount = 1; // number of players within distance 6 of self
        double distance = Utils.dist(self,target);
        Point midpoint = new Point(self.x + (target.x-self.x)*(distance - INNER_RADIUS)/distance, self.y + (target.y-self.y)*(distance - INNER_RADIUS)/distance, self_id);
        for (Point p : players) {
            if (p.id == target.id || p.id == self.id) {
                continue;
            }
            boolean available = isAvailable(p.id,players,chat_ids);
            if (Utils.dist(p,midpoint) < 0.75 && !available) {
                return 0;
            }
            if (Utils.dist(p,midpoint) < 3 && available) {
                interruptCount++;
            }
            if (Utils.dist(p,target) < 6 && available) {
                targetCount++;
            }
            if (Utils.dist(p,self) < 6 && people[p.id].remaining_wisdom > 0 && !people[p.id].has_left && available) {
                selfCount++;
            }
        }
        double Pr_no_interrupt = Math.pow( 1 - 1.5 / (9*Math.PI), interruptCount); // assume other players move randomly up to 3 units
        double wisdomRate = 1.0;
        if (people[target.id].status == Person.Status.SOULMATE) {
            wisdomRate = 2.0;
        }
        double time_to_acquire_wisdom = (double)people[target.id].remaining_wisdom / wisdomRate;
        double time_to_initiate_conversation = (double)targetCount * 2.0 * (double)selfCount / (double) (players.length - 1); // assume symmetry and other players choose a desirable player at random to talk to
        double time_spent_acquiring_wisdom = time_to_acquire_wisdom / Pr_no_interrupt;
        double time_spent = time_to_initiate_conversation + time_spent_acquiring_wisdom;
        if (!initiated) {
            return people[target.id].remaining_wisdom / time_spent;
        }
        else {
            return people[target.id].remaining_wisdom / time_spent_acquiring_wisdom;
        }
    }

    public Point play(Point[] players, int[] chat_ids, boolean wiser, int more_wisdom) {
        time++;
        if (simpleStrategyFlag) {
            return simplePlay(players, chat_ids, wiser, more_wisdom);
        }
        int i = 0;
        int j = 0;
        int k = 0;
        while (players[i].id != self_id) {
            i++;
        }
        while (players[j].id != chat_ids[i]) {
            j++;
        }
        Point lastChatted = null;
        for (Point p : players) {
            if (p.id == last_person_chatted_id) {
                lastChatted = p;
            }
        }
        Point self = players[i];
        Point chat = players[j];

        boolean chatting = (i != j);

        selfPlayer = self;

        if (chatting) {
            last_person_chatted_id = chat.id;
            prevChatLocation = chat;
            // Note if we've chatted with them to update expected values
            Person person_chatting_with = people[chat.id];

            // Can only be stranger or soul mate. Friends are already known.
            if (!person_chatting_with.known) {
                stranger_counter++;
                person_chatting_with.known = true;

                // Identify if soul mate
                if (more_wisdom > FRIEND_WISDOM) {
                    people[chat.id].status = Person.Status.SOULMATE;
                    soul_mate_id = chat.id;
                    soulmate_counter++;
                }

                /*
                println(stranger_counter + " strangers");
                println(soulmate_counter + " soulmate(s)");
*/
                // Update the expected wisdom for all unknowns
                updateExpectedWisdom(more_wisdom);
            }

            // Update remaining wisdom
            people[chat.id].remaining_wisdom = more_wisdom;

            // Attempt to continue chatting if there is more wisdom

            // Move closer to prevent others form interrupting
            if (Utils.dist(self, chat) > RADIUS_TO_MAINTAIN) {
                return getCloserToTarget(self, chat);
            }

            // Either continue chatting or wait for some time to continue conversation before leaving
            if (wiser && people[chat.id].remaining_wisdom > 0) {
                last_time_wisdom_gained = time;
                return new Point(0.0, 0.0, chat.id);
            } else if (!wiser && time - last_time_wisdom_gained < wisdomDependentWaitTime(chat)) {
                //else if (shouldWait(players, chat_ids, chat)){
                return new Point(0.0, 0.0, chat.id);
            }
        }
        else {
            // See if other player left because we have no wisdom remaining to give
            if (last_person_chatted_id != -1 && people[last_person_chatted_id].status != Person.Status.FRIEND &&
                    (people[last_person_chatted_id].remaining_wisdom == 9 ||
                            people[last_person_chatted_id].remaining_wisdom == 19) ) {
                // See if other player moved closer
                if ( lastChatted == null || Math.abs(Utils.dist(prevChatLocation, lastChatted) - Utils.dist(lastChatted,self)) > 0.1)  {
                    people[last_person_chatted_id].has_left = true;
                    last_person_chatted_id = -1;
                }
            }
        }
        last_person_chatted_id = -1;
        prevChatLocation = null;
        // Try to initiate chat with person in range if previously not chatting
        Point closestTarget = bestTarget(players, chat_ids);
        if (closestTarget != null) {
            return closestTarget;
        }

        // Else move to some one else to talk with
        Point bestTargetToMoveTo = bestTargetToMoveTo(players,chat_ids);
        if (bestTargetToMoveTo != null) {
            return getCloserToTarget(selfPlayer, bestTargetToMoveTo);
        }

        // Return a random move in the worst case
        return randomMove(self);
    }

    public Point simplePlay(Point[] players, int[] chat_ids, boolean wiser, int more_wisdom) {
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
            Point closestTarget = simpleBestTarget(players, chat_ids);
            if (closestTarget != null) {
                return closestTarget;
            }

            Point bestTargetToMoveTo = simpleBestTargetToMoveTo(players);
            if (bestTargetToMoveTo != null) {
                return getCloserToTarget(selfPlayer, bestTargetToMoveTo);
            }

            return randomMove(self);
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
                // If soul mate is in range and if they are more valuable than a friend, always attempt to speak
                // with them.
                /*if (p.id == soul_mate_id && people[soul_mate_id].remaining_wisdom >= 50) {
                    return new Point(0.0, 0.0, p.id);
		    }*/
                potentialTargets.add(p);
            }
        }

        Point ignored_person = null;

        while (!potentialTargets.isEmpty()) {
            Point nextTarget = potentialTargets.poll();
            if (isAvailable(nextTarget.id, players, chat_ids)) {
                if (people[nextTarget.id].remaining_wisdom != 0) {
                    // If this is a person who has walked away from us, we will only attempt to talk to them
                    // if there is no one better.
                    if (people[nextTarget.id].has_left) {
                        ignored_person = nextTarget;
                    } else {
                        return new Point(0.0, 0.0, nextTarget.id);
                    }
                }
            }
            else { // chatting player blocking us from talking to anyone further away
                return null;
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
    private Point bestTargetToMoveTo(Point[] players, int[] chat_ids) {
        Point self;
        for (Point p : players) {
            if (p.id == self_id) {
                self = p;
            }
        }
        Point bestPlayer = null;
        Point bestIgnoredPlayer = null;
        double maxScore = 0;
        for (Point p : players) {
            if (p.id == self_id || !isAvailable(p.id,players,chat_ids))
                continue;
            //int curPlayerRemWisdom = people[p.id].remaining_wisdom;
            double curScore;
            curScore = scorePlayer(players, chat_ids,p);
            if (curScore > maxScore) {
                //maxWisdom = curPlayerRemWisdom;
                maxScore = curScore;
                // If this is a person who has walked away from us, we will only move to them
                // if there is no one better.
                if (people[p.id].has_left) {
                    bestIgnoredPlayer = p;
                } else {
                    bestPlayer = p;
                }
            }
        }

        if (maxScore > 0) {
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
        while (players[j].id != chat_ids[i]) {
            if (++j > players.length-1) {
                return false;
            }
        }
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

    public boolean shouldWait(Point[] players, int[] chat_ids, Point chat) {
        double currScore = scorePlayer(players, chat_ids, chat, true);
        for (Point p : players) {
            if (p.id == self_id || p.id == chat.id) {continue;}
            if (scorePlayer(players,chat_ids,p) > currScore) {
                return false;
            }
        }
        return true;
    }

    public void updateExpectedWisdom(int new_found_wisdom) {
        if (new_found_wisdom > 60) {
            total_wisdom -= 400;
        } else if (new_found_wisdom > 18) {
            total_wisdom -= 20;
        } else if (new_found_wisdom > 8) {
            total_wisdom -= 10;
        }

        total_unknowns--;
        if (total_unknowns > 0) {
            expected_wisdom = total_wisdom / total_unknowns;

            for (Person p : people) {
                if (p.status == Person.Status.STRANGER && p.known == false) {
                    p.remaining_wisdom = expected_wisdom;
                }
            }
        }
    }

    private void simpleInit(int id, int[] friend_ids, int strangers) {
        time = 0;
        self_id = id;
        num_strangers = strangers;
        num_friends = friend_ids.length;
        n = num_friends + num_strangers + 2; // people = friends + strangers + soul mate + us
        people = new Person[n];
        total_unknowns = num_strangers + 1;
        total_wisdom = AVG_STRANGER_WISDOM*num_strangers + SOUL_MATE_WISDOM; // total wisdom amongst strangers and soul mate
        expected_wisdom = total_wisdom / total_unknowns;
        // Initialize strangers and soul mate
        for (int i = 0; i < people.length; i++) {
            Person stranger = new Person();
            stranger.status = Person.Status.STRANGER;
            stranger.id = i;
            stranger.remaining_wisdom = expected_wisdom;
            stranger.wisdom = expected_wisdom;
            stranger.has_left = false;
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
        }
    }

    private Point simpleBestTarget(Point[] players, int[] chat_ids) {
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
    private Point simpleBestTargetToMoveTo(Point[] players) {
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
}

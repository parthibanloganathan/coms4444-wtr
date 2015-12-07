package wtr.g2;

import wtr.sim.Point;

import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.ArrayList;
import java.security.SecureRandom;

public class Player implements wtr.sim.Player {

    // Constants
    public static final int PLAYER_RANGE = 6;
    public static final int PATIENCE_IN_TICS = 2;
    public static final int XTICKS = 4;
    public static final double MIN_RADIUS_FOR_CONVERSATION = 0.505; // slight offset for floating point stuff
    public static final double MAX_RADIUS_FOR_CONVERSATION = 1.995;
    public static final double GAP_BETWEEN_PAIRS = 0.515;
    public static final double MARGIN = MIN_RADIUS_FOR_CONVERSATION * 0.5 + GAP_BETWEEN_PAIRS * 0.5;
    private static final int salt = 3456203; //change to a different random integer for every submission
    
    // Static vars
    private static int num_strangers;
    private static int num_friends;
    private static int n; // total number of people
    private Random jointRandom = null;
    private Random thisRandom = null;
    private Point offset;
    private Point offsetPerp;

    // Player specific variables
    private int self_id = -1;
    private int time;
    private Person[] people;
    private int last_chatted;
    private int last_time_chatted;
    private int total_wisdom;
    private int total_strangers;
    private int expected_wisdom;
    private boolean coop = true;    
    private int numInstances = 0;
    private Point stackPoint = null;
    private int migrationStartTime = 0;
    private int numSlots;
    private boolean initialized = false;
    private boolean nextToWall = true;

    private Queue<Point> prevLocations;
    private Point locationXTicksAgo;

    private void println(String s) {
        System.out.println(self_id + "\t" +"\t|\t" + s);
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
        total_strangers = strangers+1;
        total_wisdom = 10*strangers + 200;
        prevLocations = new LinkedList<>();
        expected_wisdom = total_wisdom / total_strangers;

        for (int i = 0; i < people.length; i++) {
            Person p = new Person();
            p.status = Person.Status.STRANGER;
            p.id = i;
            p.remaining_wisdom = expected_wisdom;
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

    private void updateExpectation(int wisdom) { // expected wisdom value of a stranger
        total_strangers--;
        total_wisdom -= wisdom;
        if (total_strangers == 0)
            expected_wisdom = 0;
        else
            expected_wisdom = total_wisdom / total_strangers;
    }

    private Point generateRandomWallPoint() {
	int wall = jointRandom.nextInt(4);
	double cutoff = 6;
	switch (wall) {
	case 0:
	    offset = new Point(GAP_BETWEEN_PAIRS,0, self_id);
	    offsetPerp = new Point(0,MIN_RADIUS_FOR_CONVERSATION,self_id);
	    return new Point(jointRandom.nextDouble() * cutoff, 0.001, self_id);
	case 1:
	    offset = new Point(0,GAP_BETWEEN_PAIRS,self_id);
	    offsetPerp = new Point(-MIN_RADIUS_FOR_CONVERSATION,0,self_id);
	    return new Point(19.999, jointRandom.nextDouble() * cutoff, self_id);
	case 2:
	    offset = new Point(GAP_BETWEEN_PAIRS,0,self_id);
	    offsetPerp = new Point(0,-MIN_RADIUS_FOR_CONVERSATION,self_id);
	    return new Point(jointRandom.nextDouble() * cutoff, 19.999, self_id);
	case 3:
	    offset = new Point(0,GAP_BETWEEN_PAIRS,self_id);
	    offsetPerp = new Point(MIN_RADIUS_FOR_CONVERSATION,0,self_id);
	    return new Point(0.001, jointRandom.nextDouble() * cutoff, self_id);
	default:
	    return null;
	}
    }
    
    public Point coopPlay(Point[] players, int[] chat_ids, boolean wiser, int more_wisdom) {
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
	    numSlots = (int)Math.round(2.0 * (double)numInstances / 3.0);
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
	    if (stackedCount == 0) {
		System.out.println("time:" + time + " - id:"  + self_id + ": talk to " + id);
		return new Point(0,0,id);
	    }
	    else {
		if (self_id < stackedID) {
		    System.out.println("time:" + time + " - id:" + self_id + ": moving to talk to " + stackedID);
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
	    System.out.println("time:" + time + " - id:" + self_id + ": alone, waiting");
	    return new Point(0,0,id);	    
	}
	// if have wisdom in range, move with probability (num people - )2 / num people
	if (wisdom > 0 && count > 1) {
	    if (thisRandom.nextDouble() > ((double)count - 1.0) / (double)count + 1.0) {
		System.out.println("time:" + time + " - id:" + self_id + ": too many people, chosen to stay");
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
	    //System.out.println("time:" + time + " - id:" + self_id + " slot " + k + " # " + count);
	    if ((count == 1) && wisdom > 0) {		
		slots.add(kslot);
	    }
	    else if (count == 0) {
		emptySlots.add(kslot);
	    }
	}
	if (emptySlots.size() > 0 && (slots.size() == 0 || thisRandom.nextDouble() < 0.111111111)) { // if no available slots, or with probability 1/9, create a new slot
	    System.out.println("time:" + time + " - id:" + self_id + ": moving to empty slot");
	    nextToWall = true;
	    return migrateTo(emptySlots.get(thisRandom.nextInt(emptySlots.size())),self);
	}
	if (slots.size() == 0) {
	    System.out.println("time:" + time + " - id:" + self_id+ ": nothing to do");
	    return new Point(0,0,self_id);
	}
	int r = thisRandom.nextInt(slots.size());
	Point target = slots.get(r);
	for (Point p:players) {
	    if (Utils.dist(target,p) < 0.1) {		
		System.out.println("time:" + time + " - id:" + self_id + ": move perpendicular to " + (target.x + offsetPerp.x) + ", " + (target.y + offsetPerp.y));
		nextToWall = false;
		return migrateTo(add(target, offsetPerp, 1), self);
	    }
	}
	System.out.println("time:" + time + " - id:" + self_id + ": move to " + target.x + ", " + target.y);
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
        boolean prevLocationsFull = (prevLocations.size() == XTICKS);

        if (chat.id != self_id && people[chat.id].remaining_wisdom == -1) {
            if (wiser)
                updateExpectation(more_wisdom + 1);
            else
                updateExpectation(more_wisdom);
        }

        // Find location x ticks ago
        prevLocations.add(self);
        if (prevLocationsFull) {
            locationXTicksAgo = prevLocations.poll();
        }

        if (chatting) {
            if (Utils.dist(self, chat) > 0.52) {
                return getCloser(self, chat);
            }
            // attempt to continue chatting if there is more wisdom
            if (wiser) {
                last_chatted = chat.id;
                last_time_chatted = time;
                return new Point(0.0, 0.0, chat.id);
            }
            else { //wait some time before leaving conversation
                if (time - last_time_chatted < PATIENCE_IN_TICS && Utils.dist(self, chat) < MAX_RADIUS_FOR_CONVERSATION && Utils.dist(self, chat) > MIN_RADIUS_FOR_CONVERSATION) {
                    return new Point(0.0, 0.0, chat.id);
                }
            }
        }
        else {
            // If player is stationery too long, move
            if (prevLocationsFull && Utils.pointsAreSame(locationXTicksAgo, self)) {
                return randomMove(self, PLAYER_RANGE);
            }
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
                    if (!(people[nextTarget.id].remaining_wisdom == 0)){// && !people[nextTarget.id].has_left) {
                        Utils.printChatInitiation(self, nextTarget);
                        return new Point(0.0, 0.0, nextTarget.id);
                    }
                }
                else { // if closest person is not available (is chatting), then he is unlikely to move,
                // so we can't talk to anyone farther away
                    break;
                }
            }

            //Could not find a chat, so plan next move
	    Point bestPlayer = chooseBestPlayer(players, chat_ids);
            if (bestPlayer != null) {
                return moveToOtherPlayer(self, bestPlayer);
	    }
        }

        //If all else fails
        return randomMove(self, PLAYER_RANGE);
    }

    private Point chooseBestPlayer(Point[] players, int[] chat_ids) {
        Point bestPlayer = null;
        int maxWisdom = 0;
	Point us = null;
	for (Point p : players) {
	    if (p.id == self_id)
		us = p;
	}
        for (Point p : players) {
            if (p.id == self_id)
                continue;
            int curPlayerRemWisdom = people[p.id].remaining_wisdom;
            if (curPlayerRemWisdom == -1) {
                curPlayerRemWisdom = expected_wisdom;
            }
            if (curPlayerRemWisdom > maxWisdom && !people[p.id].has_left && isAvailable(p.id, players, chat_ids) && emptyRect(us, p, players, chat_ids)) {
                maxWisdom = curPlayerRemWisdom;
                bestPlayer = p;
            }
        }
        return bestPlayer;
    }

    // check if point c is inside the rectangle given by a and b
    private boolean insideRect(Point a, Point b, Point c) {
	return (Math.abs(a.x - c.x) + Math.abs(c.x - b.x) == Math.abs(a.x - b.x) && Math.abs(a.y - c.y) + Math.abs(c.y - b.y) == Math.abs(a.y - b.y));
    }

    // check if the path to a target player is unobstructed
    private boolean emptyRect(Point a, Point b, Point[] players, int[] chat_ids) {
	Point median = new Point(0.5*a.x + 0.5*b.x, 0.5*a.y + 0.5*b.y, 0);
	for (int i=0; i<players.length; i++){ 
	    if (players[i].id == a.id || players[i].id == b.id) {continue;}
	    if (insideRect(a, b, players[i]) && isAvailable(players[i].id, players, chat_ids) || Utils.dist(median, players[i]) < 2*MIN_RADIUS_FOR_CONVERSATION) {
		return false;
	    }
	}
	return true;
    }

    private Point moveToOtherPlayer(Point us, Point them) {
        double dis = Utils.dist(us, them)/2 - MIN_RADIUS_FOR_CONVERSATION/2;
        double dx = them.x - us.x;
        double dy = them.y - us.y;
        double theta = Math.atan2(dy, dx);
        return new Point(dis * Math.cos(theta), dis * Math.sin(theta), self_id);
    }

    private Point randomMove(Point self, int maxDist) {
        double dir, dx, dy;
        Point rand;
        do {
            dir = thisRandom.nextDouble() * 2 * Math.PI;
            dx = maxDist * Math.cos(dir); 
            dy = maxDist * Math.sin(dir);
            rand = new Point(dx, dy, self_id);
        } while (Utils.pointOutOfRange(self, dx, dy));
        return rand;
    }

    //From g5. Throws exceptions once in a while for some reason // probably because the player with id chat_ids[i] is not in visible range
    private boolean isAvailable(int id, Point[] players, int[] chat_ids){
        int i = 0, j = 0;
        try {
            while (players[i].id != id)
                i++;
            while (players[j].id != chat_ids[i])
                j++;
        }
        catch (IndexOutOfBoundsException e) {
            return false;
        }
        return i == j;
    }

    public Point getCloser(Point self, Point target){
        //can't set to 0.5, if 0.5 the result distance may be 0.49
        double targetDis = 0.52;
        double dis = Utils.dist(self, target);
        double x = (dis - targetDis) * (target.x - self.x) / dis;
        double y = (dis - targetDis) * (target.y - self.y) / dis;
	//        System.out.println("self pos: " + self.x + ", " + self.y);
	//        System.out.println("target pos: " + target.x + ", " + target.y);
	//        System.out.println("move pos: " + x + ", " + y);
        return new Point(x, y, self_id);
    }
}

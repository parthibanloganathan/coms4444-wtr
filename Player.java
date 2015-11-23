package wtr.g2;

import java.util.Arrays;
import java.util.Random;

import wtr.sim.Point;

public class Player implements wtr.sim.Player {

	//constants
	public static final int PLAYER_RANGE = 6;
	
	//static vars
    private static int num_strangers;
    private static int num_friends;
    private static int n; // total number of people
	private static Random random = new Random();
    
    //Cannot be static
	private int self_id = -1;
	private int time;
    private Person[] people;
	private boolean stationaryLastTurn;
	private Point prevPos;

	public void init(int id, int[] friend_ids, int strangers) {
		time = 0;
		self_id = id;
		stationaryLastTurn = true;
		
        num_strangers = strangers;
        num_friends = friend_ids.length;
        n = num_friends + num_strangers + 2; // people = friends + strangers + soul mate + us
		people = new Person[n];
		for (int i = 0; i < people.length; i++) {
			Person p = new Person();
			p.status = Person.Status.STRANGER;
			p.id = i;
			p.remaining_wisdom = -1;
			p.wisdom = -1;
			people[i] = p;
		}
		
        Person us = people[self_id];
        us.status = Person.Status.US;
        us.wisdom = 0;

		for (int friend_id : friend_ids) {
            Person friend = people[friend_id];
            friend.id = friend_id;
            friend.status = Person.Status.FRIEND;
            //TODO: may not need both wisdom and remaining_wisdom
            friend.wisdom = 50;
            friend.remaining_wisdom = 50;
        }
	}

	// play function
	public Point play(Point[] players, int[] chat_ids,
	                  boolean wiser, int more_wisdom)
	{
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
//		System.out.println(chat.id + " to " + self.id + " has rem wisdom " + more_wisdom);
		
		// attempt to continue chatting if there is more wisdom
		if (wiser)
			return new Point(0.0, 0.0, chat.id);
		
		if (time % 3 == 0) {
			if (prevPos != null && prevPos.x == self.x && prevPos.y == self.y) {
//				System.out.println("Player has been still too long. Make him move");
				return randomMove(PLAYER_RANGE);
			}
			prevPos = self;
		}
		
		// try to initiate chat if previously not chatting
		if (i == j) {
			for (Point p : players) {
				// skip if no more wisdom to gain
				if (people[p.id].remaining_wisdom == 0) {
					continue;
				}
				// compute squared distance
				double dx = self.x - p.x;
				double dy = self.y - p.y;
				double dd = dx * dx + dy * dy;
				// start chatting if in range
				if (dd >= 0.25 && dd <= 4.0) {
//					System.out.println(self.id + " close enough to chat to player: " + p.id);
					return new Point(0.0, 0.0, p.id);
				}
			}
		}
		
		//Could not find a chat, so plan next move
		int maxWisdom = 0;
		Point bestPlayer = null;
		for (Point p : players) {
			int curPlayerRemWisdom = people[p.id].remaining_wisdom;
			if (curPlayerRemWisdom > maxWisdom) {
				maxWisdom = curPlayerRemWisdom;
				bestPlayer = p;
			}
		}
		
		if (bestPlayer != null) {
			//Move a fraction of the way to other player's known position
			double dx = bestPlayer.x - self.x;
			double dy = bestPlayer.y - self.y;
			System.out.println(self.id + " moving towards: " + bestPlayer.id);
			return new Point(dx/3, dy/3, self_id);
		}

		//else alternate between staying still and random move
//		if (stationaryLastTurn) {
			return randomMove(PLAYER_RANGE);
//		}
//		else {
//			return randomMove(0);
//		}
//		stationaryLastTurn = !stationaryLastTurn;
	}
	
	public Point randomMove(int maxDist) {
		stationaryLastTurn = !stationaryLastTurn;
		double dir = random.nextDouble() * 2 * Math.PI;
		double dx = maxDist * Math.cos(dir);
		double dy = maxDist * Math.sin(dir);
		System.out.println("Random move");
		return new Point(dx, dy, self_id);
	}
}

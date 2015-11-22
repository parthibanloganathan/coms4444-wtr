package wtr.g2;

import java.util.Arrays;
import java.util.Random;

import wtr.sim.Point;

public class Player implements wtr.sim.Player {

	//static vars
    private static int num_strangers;
    private static int num_friends;
    private static int n; // total number of people
	private static Random random = new Random();
    
    //Cannot be static
	private int time;
    private Person[] people;
	private int self_id = -1;

	public void init(int id, int[] friend_ids, int strangers) {
		time = 0;
		self_id = id;
		
        num_strangers = strangers;
        num_friends = friend_ids.length;
        n = num_friends + num_strangers + 2; // people = friends + strangers + soul mate + us
		people = new Person[n];
		for (int i = 0; i < people.length; i++) {
			people[i] = new Person();
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
		
		// attempt to continue chatting if there is more wisdom
		if (wiser)
			return new Point(0.0, 0.0, chat.id);
		
		// try to initiate chat if previously not chatting
		int a = 0;
		int b = 0;
		if (i == j) {
			for (Point p : players) {
				a++;
				// skip if no more wisdom to gain
				if (people[p.id].remaining_wisdom == 0) {
					b++;
					continue;
				}
				// compute squared distance
				double dx = self.x - p.x;
				double dy = self.y - p.y;
				double dd = dx * dx + dy * dy;
				// start chatting if in range
				if (dd >= 0.25 && dd <= 4.0) {
					return new Point(0.0, 0.0, p.id);
				}
			}
		}
		
		double dir = random.nextDouble() * 2 * Math.PI;
		double dx = 6 * Math.cos(dir);
		double dy = 6 * Math.sin(dir);
		return new Point(dx, dy, self_id);
	}
}

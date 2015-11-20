package wtr.g2;

import wtr.sim.Point;

public class Player implements wtr.sim.Player {

	// your own id
	private int self_id = -1;

	private static int time;

    private static int num_strangers;
    private static int num_friends;
    private static int n; // total number of people

    private static Person[] people;

	// init function called once
	public void init(int id, int[] friend_ids, int strangers) {
		time = 0;
		self_id = id;

        num_strangers = strangers;
        num_friends = friend_ids.length;
        n = num_friends + num_strangers + 2; // people = friends + strangers + soul mate + us

        Person us = people[self_id];
        us.status = Person.Status.US;
        us.wisdom = 0;

        people = new Person[n];
		for (int friend_id : friend_ids) {
            Person friend = people[friend_id];
            friend.id = friend_id;
            friend.status = Person.Status.FRIEND;
            friend.wisdom = 50;
        }
	}

	// play function
	public Point play(Point[] players, int[] chat_ids,
	                  boolean wiser, int more_wisdom)
	{
		time++;
		// find where you are and who you chat with
       return null;
	}
}

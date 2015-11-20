package wtr.g2;

import wtr.sim.Point;

import java.util.Random;

public class Player implements wtr.sim.Player {

	// your own id
	private int self_id = -1;

	// the remaining wisdom per player
	private int[] W = null;

	// random generator
	private Random random = new Random();

	// init function called once
	public void init(int id, int[] friend_ids, int strangers)
	{
		self_id = id;
		// initialize the wisdom array
		int N = friend_ids.length + strangers + 2;
		W = new int [N];
		for (int i = 0 ; i != N ; ++i)
			W[i] = i == self_id ? 0 : -1;
		for (int friend_id : friend_ids)
			W[friend_id] = 50;
	}

	// play function
	public Point play(Point[] players, int[] chat_ids,
	                  boolean wiser, int more_wisdom)
	{
		// find where you are and who you chat with
		int i = 0, j = 0;
		while (players[i].id != self_id) i++;
		while (players[j].id != chat_ids[i]) j++;
		Point self = players[i];
		Point chat = players[j];
		// record known wisdom
		W[chat.id] = more_wisdom;
		// attempt to continue chatting if there is more wisdom
		if (wiser) return new Point(0.0, 0.0, chat.id);
		// try to initiate chat if previously not chatting
		if (i == j)
			for (Point p : players) {
				// skip if no more wisdom to gain
				if (W[p.id] == 0) continue;
				// compute squared distance
				double dx = self.x - p.x;
				double dy = self.y - p.y;
				double dd = dx * dx + dy * dy;
				// start chatting if in range
				if (dd >= 0.25 && dd <= 4.0)
					return new Point(0.0, 0.0, p.id);
			}
		// return a random move
		double dir = random.nextDouble() * 2 * Math.PI;
		double dx = 6 * Math.cos(dir);
		double dy = 6 * Math.sin(dir);
		return new Point(dx, dy, self_id);
	}
}

package dev.hoot.api.movement;

import dev.hoot.api.commons.Rand;
import dev.hoot.api.entities.Players;
import dev.hoot.api.game.Game;
import dev.hoot.api.game.Vars;
import dev.hoot.api.movement.pathfinder.BankLocation;
import dev.hoot.api.movement.pathfinder.Walker;
import dev.hoot.api.scene.Tiles;
import dev.hoot.api.widgets.Widgets;
import net.runelite.api.Client;
import net.runelite.api.Locatable;
import net.runelite.api.MenuAction;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class Movement
{
	private static final Logger logger = LoggerFactory.getLogger(Movement.class);

	private static final int STAMINA_VARBIT = 25;
	private static final int RUN_VARP = 173;

	public static void setDestination(int sceneX, int sceneY)
	{
		Game.getClient().setSelectedSceneTileX(sceneX);
		Game.getClient().setSelectedSceneTileY(sceneY);
		Game.getClient().setViewportWalking(true);
	}

	public static WorldPoint getDestination()
	{
		Client client = Game.getClient();
		return new WorldPoint(
				client.getDestinationX() + client.getBaseX(),
				client.getDestinationY() + client.getBaseY(),
				client.getPlane()
		);
	}

	public static boolean isWalking()
	{
		Player local = Players.getLocal();
		LocalPoint destination = Game.getClient().getLocalDestinationLocation();
		return local.isMoving()
				&& destination != null
				&& destination.distanceTo(local.getLocalLocation()) > 4;
	}

	public static void walk(WorldPoint worldPoint)
	{
		Client client = Game.getClient();
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}

		WorldPoint walkPoint = worldPoint;
		Tile destinationTile = Tiles.getAt(worldPoint);
		// Check if tile is in loaded client scene
		if (destinationTile == null)
		{
			logger.debug("Destination {} is not in scene", worldPoint);
			Tile nearestInScene = Tiles.getAll()
					.stream()
					.min(Comparator.comparingInt(x -> x.getWorldLocation().distanceTo(local.getWorldLocation())))
					.orElse(null);
			if (nearestInScene == null)
			{
				logger.debug("Couldn't find nearest walkable tile");
				return;
			}

			walkPoint = nearestInScene.getWorldLocation();
		}

		int sceneX = walkPoint.getX() - client.getBaseX();
		int sceneY = walkPoint.getY() - client.getBaseY();
		Point canv = Perspective.localToCanvas(client, LocalPoint.fromScene(sceneX, sceneY), client.getPlane());
		int x = canv != null ? canv.getX() : -1;
		int y = canv != null ? canv.getY() : -1;

		client.interact(
				0,
				MenuAction.WALK.getId(),
				sceneX,
				sceneY,
				x,
				y
		);
	}

	public static void walk(WorldArea worldArea)
	{
		Player local = Players.getLocal();

		if (worldArea.contains(local.getWorldLocation()))
		{
			return;
		}

		List<WorldPoint> walkPointList = worldArea.toWorldPointList();
		List<WorldPoint> losPoints = new ArrayList<>();

		for (WorldPoint point : walkPointList)
		{
			if (!Reachable.isWalkable(point))
			{
				continue;
			}
			losPoints.add(point);
		}
		WorldPoint walkPoint = losPoints.get(Rand.nextInt(0, walkPointList.size() - 1));
		Movement.walk(walkPoint);
	}

	public static void walk(Locatable locatable)
	{
		walk(locatable.getWorldLocation());
	}

	public static boolean walkTo(WorldPoint worldPoint, int radius)
	{
		WorldPoint wp = new WorldPoint(
				worldPoint.getX() + Rand.nextInt(-radius, radius),
				worldPoint.getY() + Rand.nextInt(-radius, radius),
				worldPoint.getPlane());
		return Walker.walkTo(wp, false);
	}

	public static boolean walkTo(WorldArea worldArea)
	{
		List<WorldPoint> wpList = worldArea.toWorldPointList();
		WorldPoint wp = wpList.get(Rand.nextInt(0, wpList.size() - 1));
		return Walker.walkTo(wp, false);
	}

	public static boolean walkTo(WorldPoint worldPoint)
	{
		return Walker.walkTo(worldPoint, false);
	}

	public static boolean walkTo(Locatable locatable)
	{
		return walkTo(locatable.getWorldLocation());
	}

	public static boolean walkTo(BankLocation bankLocation)
	{
		return walkTo(bankLocation.getArea().toWorldPoint());
	}

	public static boolean walkTo(int x, int y)
	{
		return walkTo(x, y, Game.getClient().getPlane());
	}

	public static boolean walkTo(int x, int y, int plane)
	{
		return walkTo(new WorldPoint(x, y, plane));
	}

	public static boolean isRunEnabled()
	{
		return Vars.getVarp(RUN_VARP) == 1;
	}

	public static void toggleRun()
	{
		Widget widget = Widgets.get(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB);
		if (widget != null)
		{
			widget.interact("Toggle Run");
		}
	}

	public static boolean isStaminaBoosted()
	{
		return Vars.getBit(STAMINA_VARBIT) == 1;
	}

	public static int getRunEnergy()
	{
		return Game.getClient().getEnergy();
	}

	public static int calculateDistance(WorldPoint destination)
	{
		return calculateDistance(destination, false);
	}

	public static int calculateDistance(WorldPoint destination, boolean localRegion)
	{
		List<WorldPoint> path = Walker.buildPath(destination, localRegion);

		if (path.size() < 2)
		{
			return 0;
		}

		Iterator<WorldPoint> it = path.iterator();
		WorldPoint prev = it.next();
		WorldPoint current;
		int distance = 0;

		// WorldPoint#distanceTo() returns max int when planes are different, but since the pathfinder can traverse
		// obstacles, we just add one to the distance to account for whatever obstacle is in between the current point
		// and the next.
		while (it.hasNext())
		{
			current = it.next();
			if (prev.getPlane() != current.getPlane())
			{
				distance += 1;
			}
			else
			{
				distance += Math.max(Math.abs(prev.getX() - current.getX()), Math.abs(prev.getY() - current.getY()));
			}
		}
		return distance;
	}

	/**
	 * Uses the regional collisionmap
	 */
	public static class Local
	{
		public static boolean walkTo(WorldPoint worldPoint)
		{
			return Walker.walkTo(worldPoint, true);
		}

		public static boolean walkTo(Locatable locatable)
		{
			return walkTo(locatable.getWorldLocation());
		}

		public static boolean walkTo(BankLocation bankLocation)
		{
			return walkTo(bankLocation.getArea().toWorldPoint());
		}

		public static boolean walkTo(int x, int y)
		{
			return walkTo(x, y, Game.getClient().getPlane());
		}

		public static boolean walkTo(int x, int y, int plane)
		{
			return walkTo(new WorldPoint(x, y, plane));
		}
	}
}

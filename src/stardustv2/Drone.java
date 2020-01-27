package stardustv2;
import battlecode.common.*;

import java.util.*;

public strictfp class Drone {
    static RobotController rc;
    static Utility ut;
    static int mapHeight;
    static int mapWidth;
    static boolean reversed = false;
    static Direction[] directions = Utility.getDirections();

    enum ObstacleDir {
        UNASSIGNED,
        LEFT,
        RIGHT
    }

    public Drone(RobotController rc, MapLocation localHQ) {
        Drone.rc = rc;
        Drone.mapHeight = rc.getMapHeight();
        Drone.mapWidth = rc.getMapWidth();
        Drone.ut = new Utility(rc, mapHeight, mapWidth, localHQ);

    }

    public boolean circleAround(MapLocation dest, int radiusSquared) throws GameActionException {
        if (!rc.isReady()) {
            return false;
        }

        Direction moveTowards = rc.getLocation().directionTo(dest);
        if (rc.canMove(moveTowards) && !rc.adjacentLocation(moveTowards).isWithinDistanceSquared(dest, radiusSquared)) {
            rc.move(moveTowards);
            return true;
        } else {
            // Build alternate direction queue (clockwise)
            ArrayList<Direction> queue = new ArrayList<>();
            if (!reversed) {
                queue.add(Utility.rotateXTimesLeft(moveTowards, 1));
                queue.add(Utility.rotateXTimesLeft(moveTowards, 2));
                queue.add(Utility.rotateXTimesLeft(moveTowards, 3));
            } else {
                queue.add(Utility.rotateXTimesRight(moveTowards, 1));
                queue.add(Utility.rotateXTimesRight(moveTowards, 2));
                queue.add(Utility.rotateXTimesRight(moveTowards, 3));
            }

            int hitMapEdge = 0;
            for (Direction d : queue) {
                if (!rc.onTheMap(rc.adjacentLocation(d))) {
                    hitMapEdge++;
                    continue;
                }
                if (rc.canMove(d) && !rc.adjacentLocation(d).isWithinDistanceSquared(dest, radiusSquared)) {
                    rc.move(d);
                    return true;
                }
            }
            if (hitMapEdge >= 1) {
                reversed = !reversed;
            }
        }
        return false;
    }



}

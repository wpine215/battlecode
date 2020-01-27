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

    public boolean locationWithinNetgunRange(MapLocation loc, ArrayList<MapLocation> knownNetguns) {
        for (MapLocation ng : knownNetguns) {
            System.out.println("NETGUN AT " + ng);
            if (loc.isWithinDistanceSquared(ng, 15)) {
                System.out.println("Location " + loc + "IS WITHIN NETGUN RANGE OF " + ng);
                return true;
            }
        }
        System.out.println("Location " + loc + "IS NOT WITHIN ANY NETGUN RANGE");
        return false;
    }

    public boolean circleAround(MapLocation dest, int radiusSquared, ArrayList<MapLocation> knownNetguns) throws GameActionException {
        if (!rc.isReady()) {
            return false;
        }

        Direction moveTowards = rc.getLocation().directionTo(dest);
        if (rc.canMove(moveTowards)
                && !rc.adjacentLocation(moveTowards).isWithinDistanceSquared(dest, radiusSquared)
                && !locationWithinNetgunRange(rc.adjacentLocation(moveTowards), knownNetguns)) {
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
                if (rc.canMove(d)
                        && !rc.adjacentLocation(d).isWithinDistanceSquared(dest, radiusSquared)
                        && !locationWithinNetgunRange(rc.adjacentLocation(d), knownNetguns)) {
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

    public boolean getInFormation(MapLocation dest, int squareRadius, ArrayList<MapLocation> knownNetguns) throws GameActionException {
        int lowerRadiusLimit;
        int upperRadiusLimit;
        switch(squareRadius) {
            case 3:
                lowerRadiusLimit = 8;
                upperRadiusLimit = 18;
                break;
            default:
                lowerRadiusLimit = 3;
                upperRadiusLimit = 8;
                break;
        }
        if (rc.getLocation().isWithinDistanceSquared(dest, upperRadiusLimit)
                && !rc.getLocation().isWithinDistanceSquared(dest, lowerRadiusLimit)) {
            return true;
        }
        circleAround(dest, lowerRadiusLimit, knownNetguns);
        return false;
    }

}

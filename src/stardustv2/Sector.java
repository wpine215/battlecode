package stardustv2;

import battlecode.common.*;
import java.util.*;

public strictfp class Sector {
    public static int getFromLocation(MapLocation loc) throws GameActionException {
        int sectorX = (loc.x / 8);
        int sectorY = (loc.y / 8);
        return (sectorX * 10) + sectorY;
    }

    public static boolean isInSector(MapLocation loc, int sector) throws GameActionException {
        int sectorXComp = (sector / 10) * 8;
        int sectorYComp = (sector % 10) * 8;
        if (loc.x >= sectorXComp && loc.x < sectorXComp + 8) {
            return loc.y >= sectorYComp && loc.y < sectorYComp + 8;
        }
        return false;
    }

    public static MapLocation getCenter(int sector, int mapHeight, int mapWidth) throws GameActionException {
        int centerX = (sector / 10)*8 + 4;
        int centerY = (sector % 10)*8 + 4;

        if (centerX >= mapWidth) {
            centerX = mapWidth - 1;
        }

        if (centerY >= mapHeight) {
            centerY = mapHeight - 1;
        }

        return new MapLocation(centerX, centerY);
    }

    public static boolean isNull(int sector) {
        return (sector / 10) == 9 || (sector % 10) == 9;
    }

    public static int getClosestSector(int currentLoc, Set<Integer> locations) throws GameActionException {
        int closestSector = 99;
        int closestDistanceSquared = 10000;
        for (Integer i : locations) {
            int currDSQ = getDistanceSquared(currentLoc, i);
            if (currDSQ < closestDistanceSquared) {
                closestDistanceSquared = currDSQ;
                closestSector = i;
            }
        }
        return closestSector;
    }

    private static int getDistanceSquared(int origin, int dest) throws GameActionException {
        int deltaX = getX(dest) - getX(origin);
        int deltaY = getY(dest) - getY(origin);
        return (int)Math.abs(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));
    }

    private static int getX(int sector) throws GameActionException {
        return sector / 10;
    }

    private static int getY(int sector) throws GameActionException {
        return sector % 10;
    }
}

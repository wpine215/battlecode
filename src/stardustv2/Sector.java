package stardustv2;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Set;

public strictfp class Sector {
    static Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };

    public static int getFromLocation(MapLocation loc) throws GameActionException {
        int sectorX = (loc.x / 8);
        int sectorY = (loc.y / 8);
        return (sectorX * 10) + sectorY;
    }

    public static boolean isInSector(MapLocation loc, int sector) throws GameActionException {
        int sectorXComp = (sector / 10) * 8;
        int sectorYComp = (sector % 10) * 8;
        if (loc.x >= sectorXComp && loc.x < sectorXComp + 8) {
            if (loc.y >= sectorYComp && loc.y < sectorYComp + 8) {
                return true;
            }
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

    public static boolean randomTravelWithinSector(RobotController rc, int sector) throws GameActionException {
        ArrayList<Direction> randomQueue = new ArrayList<>();
        for (Direction dir : directions) {
            rc.setIndicatorDot(rc.getLocation(), 255, 255, 255);
            rc.setIndicatorDot(rc.adjacentLocation(dir), 0, 0, 0);
            System.out.println("randomTravelWithinSector: sector is " + sector);
            if (isInSector(rc.adjacentLocation(dir), sector)) {
                randomQueue.add(dir);
            }
        }
        System.out.println(randomQueue);
        if (randomQueue.size() < 1) return false;
        if (!rc.isReady()) return true;
        Direction chosenDir = randomQueue.get((int) (Math.random() * randomQueue.size()));
        System.out.println("randomTravelWithinSector: chosenDir is " + chosenDir);
        while (randomQueue.size() > 1) {
            if (tryMove(rc, chosenDir)) {
                return true;
            } else {
                randomQueue.remove(chosenDir);
                chosenDir = randomQueue.get((int) (Math.random() * randomQueue.size()));
            }
        }
        return false;
    }

    private static boolean tryMove(RobotController rc, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }
}

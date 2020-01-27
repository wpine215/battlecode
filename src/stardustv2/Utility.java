package stardustv2;

import battlecode.common.*;

import java.util.HashMap;
import java.util.Map;

public strictfp class Utility {
    static RobotController rc;
    static int mapHeight;
    static int mapWidth;
    static MapLocation localHQ;

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

    static Map<Integer, Integer> elevationToRoundFlooded = new HashMap<Integer, Integer>() {{
        put(1, 256);
        put(2, 464);
        put(3, 677);
        put(4, 931);
        put(5, 1210);
        put(6, 1413);
        put(7, 1546);
        put(8, 1640);
        put(9, 1713);
        put(10, 1771);
        put(11, 1819);
        put(12, 1861);
        put(13, 1897);
        put(14, 1929);
        put(15, 1957);
        put(16, 1983);
        put(17, 2007);
        put(18,2028);
        put(19, 2048);
        put(20, 2067);
    }};

    public Utility(RobotController rc, int mapHeight, int mapWidth, MapLocation localHQ) {
        Utility.rc = rc;
        Utility.mapHeight = mapHeight;
        Utility.mapWidth = mapWidth;
        Utility.localHQ = localHQ;
    }

    static public Direction[] getDirections() {
        return directions;
    }

    static public Direction[] getBestMinerSpawns(MapLocation localHQ, int mapHeight, int mapWidth) {
        Direction[] result = new Direction[8];
        Direction dirToMidpoint = localHQ.directionTo(getMidpoint(mapHeight, mapWidth));
        if (dirToMidpoint == Direction.NORTH
                || dirToMidpoint == Direction.SOUTH
                || dirToMidpoint == Direction.EAST
                || dirToMidpoint == Direction.WEST) {
            result[0] = rotateXTimesLeft(dirToMidpoint, 1);
            result[1] = rotateXTimesRight(dirToMidpoint, 1);
            result[2] = dirToMidpoint;
            result[3] = rotateXTimesLeft(dirToMidpoint, 2);
            result[4] = rotateXTimesRight(dirToMidpoint, 2);
        } else {
            result[0] = dirToMidpoint;
            result[1] = rotateXTimesLeft(dirToMidpoint, 2);
            result[2] = rotateXTimesRight(dirToMidpoint, 2);
            result[3] = rotateXTimesLeft(dirToMidpoint, 1);
            result[4] = rotateXTimesRight(dirToMidpoint, 1);
        }
        result[5] = rotateXTimesLeft(dirToMidpoint, 3);
        result[6] = rotateXTimesRight(dirToMidpoint, 3);
        result[7] = dirToMidpoint.opposite();
        return result;
    }

    static public MapLocation getMidpoint(int mapHeight, int mapWidth) {
        return new MapLocation((mapWidth-1)/2, (mapHeight-1)/2);
    }

    public Direction randomDirection() throws GameActionException {
        Direction temp = directions[(int) (Math.random() * directions.length)];
        int maxTries = 8;
        while ((rc.onTheMap(rc.adjacentLocation(temp)) && rc.senseFlooding(rc.adjacentLocation(temp))) && maxTries > 0) {
            temp = directions[(int) (Math.random() * directions.length)];
            maxTries--;
        }
        return directions[(int) (Math.random() * directions.length)];
    }

    public boolean tryMove(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMove(dir) && !rc.senseFlooding(rc.adjacentLocation(dir))) {
            rc.move(dir);
            return true;
        } else return false;
    }

    public boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        int costNeeded = 1;
        switch(type) {
            case MINER:
                costNeeded += 75;
                break;
            case REFINERY:
                costNeeded += 200;
                break;
            case NET_GUN:
                costNeeded += 250;
                break;
            case VAPORATOR:
                costNeeded += 500;
                break;
            default:
                costNeeded += 150;
                break;
        }
        if (rc.isReady() && rc.canBuildRobot(type, dir) && rc.getTeamSoup() >= costNeeded) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    public boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    public boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }

    public boolean dirtDifferenceAboveX(MapLocation a, MapLocation b) throws GameActionException {
        if (!rc.canSenseLocation(a) || !rc.canSenseLocation(b)) return true; // should it return false if it can't detect?
        int a_elev = rc.senseElevation(a);
        int b_elev = rc.senseElevation(b);
        // "X" is currently set to max permissible elevation difference of 6, can be changed if needed
        return Math.abs(a_elev - b_elev) > 6;
    }

    public static Direction rotateXTimesLeft(Direction dir, int times) {
        Direction result = dir;
        for (int i = 0; i < times; i++) {
            result = result.rotateLeft();
        }
        return result;
    }

    public static Direction rotateXTimesRight(Direction dir, int times) {
        Direction result = dir;
        for (int i = 0; i < times; i++) {
            result = result.rotateRight();
        }
        return result;
    }

    public boolean locationIsWallDeadzone(Direction dir) {
        if (localHQ.x == 1) {
            if (localHQ.y == 1) {
                if (dir == Direction.SOUTH || dir == Direction.SOUTHWEST || dir == Direction.WEST) {
                    return true;
                }
            } else if (localHQ.y == mapHeight - 2) {
                if (dir == Direction.NORTH || dir == Direction.NORTHWEST || dir == Direction.WEST) {
                    return true;
                }
            } else {
                if (dir == Direction.WEST) {
                    return true;
                }
            }
        } else if (localHQ.x == mapWidth - 2) {
            if (localHQ.y == 1) {
                if (dir == Direction.SOUTH || dir == Direction.SOUTHEAST || dir == Direction.EAST) {
                    return true;
                }
            } else if (localHQ.y == mapHeight - 2) {
                if (dir == Direction.NORTH || dir == Direction.NORTHEAST || dir == Direction.EAST) {
                    return true;
                }
            } else {
                if (dir == Direction.EAST) {
                    return true;
                }
            }
        }

        if (localHQ.y == 1) {
            return dir == Direction.SOUTH;
        } else if (localHQ.y == mapHeight - 2) {
            return dir == Direction.NORTH;
        }
        return false;
    }

    public int HQEffectiveElevation() throws GameActionException {
        int ringRadius;
        int rad2 = rc.getCurrentSensorRadiusSquared();
        if (rad2 >= 8) {
            // can sense 2 rings away
        } else {
            // just check immediate
        }
        // TODO: finish this (not working)
        return 0;
    }
}

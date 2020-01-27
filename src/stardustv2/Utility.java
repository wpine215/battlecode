package stardustv2;

import battlecode.common.*;

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

    public Utility(RobotController rc, int mapHeight, int mapWidth, MapLocation localHQ) {
        Utility.rc = rc;
        Utility.mapHeight = mapHeight;
        Utility.mapWidth = mapWidth;
        Utility.localHQ = localHQ;
    }

    static public Direction[] getDirections() {
        return directions;
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
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
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
        if (!rc.canSenseLocation(a) || !rc.canSenseLocation(b)) return true;
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
}

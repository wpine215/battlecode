package stardustv2;

import battlecode.common.*;

import java.util.*;

public strictfp class Pathfinding {
    static RobotController rc;
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

    enum ObstacleDir {
        UNASSIGNED,
        LEFT,
        RIGHT
    }
    static int mapHeight;
    static int mapWidth;
    static boolean followingObstacle;
    static boolean alreadyHitMapEdge;
    static boolean rewindingToObstacle;
    static MapLocation obstacleEncounteredAt;
    static ObstacleDir obstacleDir;
    static Direction currentDirection;
    static ArrayList<MapLocation> currentMLine;
    static Set<MapLocation> currentMLineSet;
    static Set<MapLocation> locationHistory;
    static LinkedList<MapLocation> lastEightLocations;
    static int resetRounds;

    public Pathfinding(RobotController rc) {
        Pathfinding.rc = rc;
        mapHeight = rc.getMapHeight();
        mapWidth = rc.getMapWidth();
        followingObstacle = false;
        alreadyHitMapEdge = false;
        rewindingToObstacle = false;
        obstacleEncounteredAt = new MapLocation(-1, -1);
        obstacleDir = ObstacleDir.UNASSIGNED;
        currentDirection = Direction.CENTER;
        currentMLine = new ArrayList<>();
        currentMLineSet = new HashSet<>();
        locationHistory = new HashSet<>();
        lastEightLocations = new LinkedList<>();
        resetRounds = 0;
    }

    private void locationPushBack(MapLocation loc) throws GameActionException {
        if (lastEightLocations.size() >= 8) {
            lastEightLocations.removeLast();
        }
        lastEightLocations.addFirst(loc);
    }

    private boolean iskDoubleRepeating() throws GameActionException {
        if (lastEightLocations.size() >= 4) {
            return lastEightLocations.get(0).equals(lastEightLocations.get(2))
                    && lastEightLocations.get(1).equals(lastEightLocations.get(3));
        }
        return false;
    }

    private boolean isQuadRepeating() throws GameActionException {
        if (lastEightLocations.size() == 8) {
            return lastEightLocations.get(0).equals(lastEightLocations.get(4))
                    && lastEightLocations.get(1).equals(lastEightLocations.get(5))
                    && lastEightLocations.get(2).equals(lastEightLocations.get(6))
                    && lastEightLocations.get(3).equals(lastEightLocations.get(7));
        }
        return false;
    }

    public void reset() throws GameActionException {
        currentMLine.clear();
        currentMLineSet.clear();
        locationHistory.clear();
        currentDirection = Direction.CENTER;
        obstacleDir = ObstacleDir.UNASSIGNED;
        obstacleEncounteredAt = new MapLocation(-1, -1);
        followingObstacle = false;
        alreadyHitMapEdge = false;
        rewindingToObstacle = false;
        lastEightLocations.clear();
    }

    static Direction randomDirection() throws GameActionException {
        Direction temp = directions[(int) (Math.random() * directions.length)];
        int maxTries = 8;
        while (rc.senseFlooding(rc.adjacentLocation(temp)) && maxTries > 0) {
            temp = directions[(int) (Math.random() * directions.length)];
            maxTries--;
        }
        return directions[(int) (Math.random() * directions.length)];
    }

    static boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir) && !rc.senseFlooding(rc.adjacentLocation(dir))) {
            rc.move(dir);
            return true;
        } else return false;
    }

    public boolean travelTo(MapLocation dest) throws GameActionException {
        // Returns true if movement in progress
        // Returns false if journey complete or obstacle encountered
        Random rand = new Random();

        if (resetRounds > 0) {
            tryMove(randomDirection());
            resetRounds--;
            return true;
        }

        if (iskDoubleRepeating()) {
            System.out.println(">>>>> Double repeating! Resetting...");
            reset();
            resetRounds = 2;
            return true;
        }

        if (isQuadRepeating()) {
            System.out.println(">>>>> Quad repeating! Resetting...");
            reset();
            resetRounds = 2;
            return true;
        }

        // If no m-line exists, we haven't performed path calculations yet
        if (currentMLine.isEmpty()) {
            getMLine(rc.getLocation(), dest);
            currentDirection = rc.getLocation().directionTo(dest);
        }

        // Already at destination
        if (rc.getLocation().equals(dest)) {
            System.out.println(">>>>> Already at destination!");
            currentMLine.clear();
            currentMLineSet.clear();
            locationHistory.clear();
            return false;
        }

        // We encountered obstacle point again
        if (rc.getLocation().equals(obstacleEncounteredAt) && !alreadyHitMapEdge) {
            System.out.println(">>>>> Encountered obstacle point again! Resetting...");
            reset();
            resetRounds = 3;
            return true; // used to be false btw
        }

        // We encountered a map edge
        if (isLocationMapEdge(rc.getLocation())) {
            if (alreadyHitMapEdge) {
                System.out.println(">>>>> Hit two map edges!");
                alreadyHitMapEdge = false;
                currentMLine.clear();
                currentMLineSet.clear();
                locationHistory.clear();
                return false;
            } else {
                alreadyHitMapEdge = true;
                currentDirection = currentDirection.opposite();
                if (obstacleDir == ObstacleDir.LEFT) {
                    obstacleDir = ObstacleDir.RIGHT;
                } else {
                    obstacleDir = ObstacleDir.LEFT;
                }
            }
        }

        // If we're on m-line, try moving directly to target
        if (locationOnMLine(rc.getLocation()) && !alreadyHitMapEdge) {
            // We have found the m-line again after following obstacle
            if (!locIsNull(obstacleEncounteredAt)) {
                obstacleEncounteredAt = new MapLocation(-1, -1);
                obstacleDir = ObstacleDir.UNASSIGNED;
            }

            // Get next point on m-line and try moving to it
            MapLocation next = getNextPointOnMLine(rc.getLocation());
            Direction nextDir = rc.getLocation().directionTo(next);
            if (rc.canMove(nextDir) && !rc.senseFlooding(next)) {
                locationHistory.add(rc.getLocation());
                locationPushBack(rc.getLocation());
                rc.move(nextDir);
                currentDirection = nextDir;
                return true;
            } else {


                // Obstacle at next point on m-line, so do some following
                locationHistory.add(rc.getLocation());
                obstacleEncounteredAt = rc.getLocation();
                int initialDir;
                if (rc.canMove(nextDir.rotateLeft())
                        && !rc.senseFlooding(rc.adjacentLocation(nextDir.rotateLeft()))) {
                    initialDir = 0;
                } else if (rc.canMove(nextDir.rotateRight())
                        && !rc.senseFlooding(rc.adjacentLocation(nextDir.rotateRight()))) {
                    initialDir = 1;
                } else {
                    initialDir = rand.nextInt(2);
                }

                if (initialDir == 0) {
                    System.out.println(">>>>> Starting to follow obstacle left!");
                    obstacleDir = ObstacleDir.LEFT;
                    return followObstacleLeft(true);
                } else {
                    System.out.println(">>>>> Starting to follow obstacle right!");
                    obstacleDir = ObstacleDir.RIGHT;
                    return followObstacleRight(true);
                }

            }
        } else {
            // Still following obstacle
            if (obstacleDir == ObstacleDir.LEFT) {
                System.out.println(">>>>> STILL following obstacle left!");
                return followObstacleLeft(false);
            } else if (obstacleDir == ObstacleDir.RIGHT) {
                System.out.println(">>>>> STILL following obstacle right!");
                return followObstacleRight(false);
            }
        }
        return false;
    }

    public static boolean isLocationMapEdge(MapLocation loc) throws GameActionException {
        return loc.x == 0 || loc.x == mapWidth-1 || loc.y == 0 || loc.y == mapHeight-1;
    }

    public static boolean locIsNull(MapLocation loc) throws GameActionException {
        return loc.x < 0 || loc.y < 0 || loc.x > 64 || loc.y > 64;
    }

    public void drawPersistentMLine() throws GameActionException {
        for (MapLocation point : currentMLine) {
            rc.setIndicatorDot(point, 0, 0, 0);
        }
        if (!locIsNull(obstacleEncounteredAt)) {
            rc.setIndicatorDot(obstacleEncounteredAt, 255, 255, 255);
        }
    }

    private void getMLine(MapLocation src, MapLocation dest) throws GameActionException {
        currentMLine.add(src);
        currentMLineSet.add(src);
        MapLocation temp = src;
        while(!temp.equals(dest)) {
            currentMLine.add(temp.add(temp.directionTo(dest)));
            currentMLineSet.add(temp.add(temp.directionTo(dest)));
            temp = temp.add(temp.directionTo(dest));
        }
        currentMLine.add(dest);
        currentMLineSet.add(dest);
    }

    private MapLocation getNextPointOnMLine(MapLocation loc) throws GameActionException {
        int resultIndex = currentMLine.indexOf(loc) + 1;
        if (resultIndex < currentMLine.size()) {
            return currentMLine.get(resultIndex);
        }
        return new MapLocation(-1, -1);
    }

    private boolean locationOnMLine(MapLocation loc) throws GameActionException {
        return currentMLineSet.contains(loc);
    }

    private boolean locationAlreadyVisited(MapLocation loc) throws GameActionException {
        return locationHistory.contains(loc);
    }

    private boolean followObstacleLeft(boolean firstTime) throws GameActionException {
        if (currentDirection == null) {
            return false;
        }
        Direction[] moveQueue = new Direction[7];
        moveQueue[2] = currentDirection;
        moveQueue[1] = moveQueue[2].rotateRight();
        moveQueue[0] = moveQueue[1].rotateRight();
        moveQueue[3] = moveQueue[2].rotateLeft();
        moveQueue[4] = moveQueue[3].rotateLeft();
        moveQueue[5] = moveQueue[4].rotateLeft();
        moveQueue[6] = moveQueue[5].rotateLeft();

        if (firstTime) {
            // override opposite diagonal if first time
            moveQueue[0] = currentDirection;
            moveQueue[1] = currentDirection;
        }

        for (Direction dir : directions) {
            if (rc.canMove(dir)
                    && !rc.senseFlooding(rc.adjacentLocation(dir))
                    && locationOnMLine(rc.adjacentLocation(dir))
                    && !locationAlreadyVisited(rc.adjacentLocation(dir))) {
                locationPushBack(rc.getLocation());
                rc.move(dir);
                currentDirection = dir;
                return true;
            }
        }

        for (Direction dir : moveQueue) {
            if (rc.canMove(dir) && !rc.senseFlooding(rc.adjacentLocation(dir))) {
                locationPushBack(rc.getLocation());
                rc.move(dir);
                currentDirection = dir;
                rc.setIndicatorDot(rc.adjacentLocation(dir), 0, 100, 255);
                return true;
            } else {
                rc.setIndicatorDot(rc.adjacentLocation(dir), 255, 100, 50);
            }
        }
        return false;
    }

    private boolean followObstacleRight(boolean firstTime) throws GameActionException {
        if (currentDirection == null) {
            return false;
        }
        Direction[] moveQueue = new Direction[7];
        moveQueue[2] = currentDirection;
        moveQueue[1] = moveQueue[2].rotateLeft();
        moveQueue[0] = moveQueue[1].rotateLeft();
        moveQueue[3] = moveQueue[2].rotateRight();
        moveQueue[4] = moveQueue[3].rotateRight();
        moveQueue[5] = moveQueue[4].rotateRight();
        moveQueue[6] = moveQueue[5].rotateRight();

        if (firstTime) {
            // override opposite diagonal if first time
            moveQueue[0] = currentDirection;
            moveQueue[1] = currentDirection;
        }

        for (Direction dir : directions) {
            if (rc.canMove(dir)
                    && !rc.senseFlooding(rc.adjacentLocation(dir))
                    && locationOnMLine(rc.adjacentLocation(dir))
                    && !locationAlreadyVisited(rc.adjacentLocation(dir))) {
                locationPushBack(rc.getLocation());
                rc.move(dir);
                currentDirection = dir;
                return true;
            }
        }

        for (Direction dir: moveQueue) {
            if (rc.canMove(dir) && !rc.senseFlooding(rc.adjacentLocation(dir))) {
                locationPushBack(rc.getLocation());
                rc.move(dir);
                currentDirection = dir;
                rc.setIndicatorDot(rc.adjacentLocation(dir), 0, 100, 255);
                return true;
            } else {
                rc.setIndicatorDot(rc.adjacentLocation(dir), 255, 100, 50);
            }
        }
        return false;
    }
}

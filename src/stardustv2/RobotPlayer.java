package stardustv2;
import battlecode.common.*;

import java.util.Deque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

enum ObstacleDir {
    UNASSIGNED,
    LEFT,
    RIGHT
}

enum MinerState {
    SCOUTING,
    MINING,
    BUILDING
}

enum LandscaperState {
    UNASSIGNED,
    OFFENSE,
    DEFENSE
}

enum DroneState {
    PASSIVE,
    TRANSPORT,
    OFFENSE
}

public strictfp class RobotPlayer {
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
    static RobotType[] spawnedByMiner = {
        RobotType.REFINERY,
        RobotType.VAPORATOR,
        RobotType.DESIGN_SCHOOL,
        RobotType.FULFILLMENT_CENTER,
        RobotType.NET_GUN
    };

    // Common variables
    static int mapHeight;
    static int mapWidth;
    static MapLocation localHQ;
    static MapLocation enemyHQ;
    static Deque<MapLocation> travelQueue;

    static Set<MapLocation> locationHistory = new HashSet<MapLocation>(); // only location history on ML line

    static int sequentialID;

    // Pathfinding variables
    static Direction currentDirection;
    static ArrayList<MapLocation> currentMLine;
    static Set<MapLocation> currentMLineSet = new HashSet<MapLocation>();
    static boolean followingObstacle;
    static MapLocation obstacleEncounteredAt;
    static ObstacleDir obstacleDir;
    static boolean alreadyHitMapEdge;
    static boolean rewindingToObstacle;

    // HQ-specific variables
    static int HQHealth = 50;
    static int HQElevation;
    static int minerCount = 0;

    // Miner-specific variables
    static MinerState minerState;
    static ArrayList<MapLocation> refineries;
    static ArrayList<MapLocation> soup;

    // Landscaper-specific variables
    static boolean wallBuilt;
    static Direction wallDirection;
    static Direction[] wallQueue = {
        Direction.EAST,
        Direction.WEST,
        Direction.NORTH, 
        Direction.SOUTH,
        Direction.NORTHEAST,
        Direction.NORTHWEST,
        Direction.SOUTHEAST,
        Direction.SOUTHWEST
    };
    static LandscaperState landscaperState = LandscaperState.UNASSIGNED;

    // Some constants
    final static int landscaperRound = 160;
    final static int refineryRound = 140;
    final static int droneRound = 500;

    static int turnCount;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        turnCount = 0;

        // Initialize some variables defined above
        mapHeight = rc.getMapHeight();
        mapWidth = rc.getMapWidth();
        refineries = new ArrayList<>();
        soup = new ArrayList<>();
        travelQueue = new LinkedList<>();


        currentMLine = new ArrayList<>();
        followingObstacle = false;
        alreadyHitMapEdge = false;
        rewindingToObstacle = false;
        obstacleEncounteredAt = new MapLocation(-1, -1);
        obstacleDir = ObstacleDir.UNASSIGNED;

        // TEMPORARY
        minerState = MinerState.SCOUTING;
        sequentialID = rc.getRobotCount();

        System.out.println("I'm a " + rc.getType() + " and I just got created!");

        while (true) {
            turnCount += 1;
            try {
                System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
                switch (rc.getType()) {
                    case HQ:                 runHQ();                break;
                    case MINER:              runMiner();             break;
                    case REFINERY:           runRefinery();          break;
                    case VAPORATOR:          runVaporator();         break;
                    case DESIGN_SCHOOL:      runDesignSchool();      break;
                    case FULFILLMENT_CENTER: runFulfillmentCenter(); break;
                    case LANDSCAPER:         runLandscaper();        break;
                    case DELIVERY_DRONE:     runDeliveryDrone();     break;
                    case NET_GUN:            runNetGun();            break;
                }

                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    static void runHQ() throws GameActionException {
        // Broadcast genesis message
        // Listen in on refinery/soup locations to include in rebroadcast
        // Calculate own health
        // Handle rebroadcast
        // Handle netgun
        // Build and assign miners
        // Send commands to build drone centers/refineries/etc
        for (Direction dir : directions) {
            if (minerCount < 1 && tryBuild(RobotType.MINER, dir)) {
                minerCount++;
            }
        }
    }

    static void runMiner() throws GameActionException {
        drawPersistentMLine();
        if (minerState == MinerState.SCOUTING && rc.isReady()) {
            MapLocation scoutTo;
            switch (sequentialID) {
                case 2:
                    scoutTo = new MapLocation(1, 1);
                    break;
                case 3:
                    scoutTo = new MapLocation(1, mapHeight - 1);
                    break;
                case 4:
                    scoutTo = new MapLocation(mapWidth - 1, 1);
                    break;
                default:
                    scoutTo = new MapLocation(mapWidth - 1, mapHeight - 1);
                    break;
            }
            System.out.println("Miner " + sequentialID + " attempting to move to location " + scoutTo);
            if (!moveToTarget(scoutTo)) {
                System.out.println("Miner " + sequentialID + " movement unsuccessful");
                minerState = MinerState.MINING;
            }
        }
    }

    static void drawPersistentMLine() throws GameActionException {
        for (MapLocation point : currentMLine) {
            rc.setIndicatorDot(point, 0, 0, 0);
        }
        if (!locIsNull(obstacleEncounteredAt)) {
            rc.setIndicatorDot(obstacleEncounteredAt, 255, 255, 255);
        }
    }

    static void runRefinery() throws GameActionException {

    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {

    }

    static void runFulfillmentCenter() throws GameActionException {

    }

    static void runLandscaper() throws GameActionException {
        switch(landscaperState) {
            case UNASSIGNED:    assignLandscaper();     break;
            case OFFENSE:       runOffenseLandscaper(); break;
            case DEFENSE:       runDefenseLandscaper(); break;
        }
    }

    static void runDeliveryDrone() throws GameActionException {

    }

    static void runNetGun() throws GameActionException {

    }

    // Provided utility functions
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    static boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }

    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    static boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    static boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }

    // Custom Utility Functions

    static int serializeLoc(MapLocation loc) throws GameActionException {
        int serialized = loc.y;
        serialized += loc.x * 100;
        return serialized;
    }

    static MapLocation deserializeLoc(int loc) throws GameActionException {
        return new MapLocation(loc / 100, loc % 100);
    }

    static boolean locIsNull(MapLocation loc) throws GameActionException {
        return loc.x < 0 || loc.y < 0;
    }

    static boolean txSend(int[] msg, int cost) throws GameActionException {
        if (cost < 1) return false;

        if (rc.canSubmitTransaction(msg, cost)) {
            rc.submitTransaction(msg, cost);
            return true;
        }
        return false;
    }

//    static int[] txRecv(int code, int round) {
//        // Returns transactions that had given message code in given round
//    }

    static void retrieveHQCoordinates() throws GameActionException {

    }

    static void retrieveSoupLocations() throws GameActionException {

    }

    //////////////////////////////////////////////////
    // PATHFINDING FUNCTIONS
    //////////////////////////////////////////////////

    /*
    private static class vector2D {
        MapLocation point;
        Direction dir;

        vector2D(MapLocation p, Direction d) {
            point = p;
            dir = d;
        }

        static MapLocation getLocation() {
            return point;
        }

        static Direction getDirection() {
            return dir;
        }
    }
    */

    static boolean moveToTarget(MapLocation dest) throws GameActionException {
        // Returns true if movement in progress
        // Returns false if journey complete or obstacle encountered
        Random rand = new Random();

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
            System.out.println(">>>>> Encountered obstacle point again!");
            currentMLine.clear();
            currentMLineSet.clear();
            locationHistory.clear();
            return false;
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
                if (obstacleDir == ObstacleDir.LEFT) {
                    obstacleDir = ObstacleDir.RIGHT;
                } else {
                    obstacleDir = ObstacleDir.LEFT;
                }
            }
        }

        // If we're on m-line, try moving directly to target
        if (locationOnMLine(rc.getLocation())) {
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

    static void getMLine(MapLocation src, MapLocation dest) throws GameActionException {
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

    static MapLocation getNextPointOnMLine(MapLocation loc) throws GameActionException {
        int resultIndex = currentMLine.indexOf(loc) + 1;
        if (resultIndex < currentMLine.size()) {
            return currentMLine.get(resultIndex);
        }
        return new MapLocation(-1, -1);
    }

    static boolean locationOnMLine(MapLocation loc) throws GameActionException {
        // TODO: OPTIMIZE
        return currentMLineSet.contains(loc);
    }

    static boolean locationAlreadyVisited(MapLocation loc) throws GameActionException {
        // TODO: OPTIMIZE
        return locationHistory.contains(loc);
    }

    static boolean isLocationMapEdge(MapLocation loc) throws GameActionException {
        return loc.x == 0 || loc.x == mapWidth || loc.y == 0 || loc.y == mapHeight;
    }

    static boolean followObstacleLeft(boolean firstTime) throws GameActionException {
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

        // TODO: OPTIMIZE (uses 88 + 8 * (size of currentMLine + locationHistory)
        for (Direction dir : directions) {
            if (rc.canMove(dir)
                    && !rc.senseFlooding(rc.adjacentLocation(dir))
                    && locationOnMLine(rc.adjacentLocation(dir))
                    && !locationAlreadyVisited(rc.adjacentLocation(dir))) {
                rc.move(dir);
                currentDirection = dir;
                return true;
            }
        }
        // TODO: OPTIMIZE (uses 88 + 8 * (size of currentMLine))
        for (Direction dir : moveQueue) {
            if (rc.canMove(dir) && !rc.senseFlooding(rc.adjacentLocation(dir))) {
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

    static boolean followObstacleRight(boolean firstTime) throws GameActionException {
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

        // TODO: OPTIMIZE (uses 88 + 8 * (size of currentMLine + locationHistory)
        for (Direction dir : directions) {
            if (rc.canMove(dir)
                    && !rc.senseFlooding(rc.adjacentLocation(dir))
                    && locationOnMLine(rc.adjacentLocation(dir))
                    && !locationAlreadyVisited(rc.adjacentLocation(dir))) {
                rc.move(dir);
                currentDirection = dir;
                return true;
            }
        }

        // TODO: OPTIMIZE (uses 88 + 8 * (size of currentMLine))
        for (Direction dir: moveQueue) {
            if (rc.canMove(dir) && !rc.senseFlooding(rc.adjacentLocation(dir))) {
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




    //////////////////////////////////////////////////
    // MINER FUNCTIONS
    //////////////////////////////////////////////////



    //////////////////////////////////////////////////
    // LANDSCAPER FUNCTIONS
    //////////////////////////////////////////////////

    static void assignLandscaper() throws GameActionException {

    }

    static void runOffenseLandscaper() throws GameActionException {

    }

    static void runDefenseLandscaper() throws GameActionException {

    }


}
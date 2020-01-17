package stardustv2;
import battlecode.common.*;

import java.util.Deque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;

enum Quadrant {
    NORTHWEST,
    SOUTHWEST,
    NORTHEAST,
    SOUTHEAST
}

enum MinerState {
    UNASSIGNED,
    SCOUTING,
    RANDOM,
    MINING,
    BUILDING
}

enum MiningState {
    ENROUTE,
    IN_PROGRESS,
    REFINING
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

    // HQ-specific variables
    static int HQHealth = 50;
    static int HQElevation;
    static int minerCount = 0;

    // Pathfinding-specific variables
    static Pathfinding pathfinding;
    static Deque<MapLocation> travelQueue;
    static int nextMiningSector;

    // Blockchain
    static Communication communication;

    // Miner-specific variables
    static MinerState minerState;
    static MiningState miningState;
    static ArrayList<MapLocation> refineries;
    static Set<Integer> soupSectors;
    static MapLocation lastSoupDeposit;
    static int refineryAccessAttempts = 0;
    static int unfruitfulRounds = 0;

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

        // Instantiate pathfinding
        pathfinding = new Pathfinding(rc);
        travelQueue = new LinkedList<>();
        nextMiningSector = 99;

        // Instantiate communications
        communication = new Communication(rc);

        // Initialize some variables defined above
        mapHeight = rc.getMapHeight();
        mapWidth = rc.getMapWidth();
        localHQ = communication.getHQCoordinates();

        refineries = new ArrayList<>();
        soupSectors = new HashSet<>();
        lastSoupDeposit = new MapLocation(-1, -1);

        if (rc.getType() == RobotType.MINER) {
            refineries.add(localHQ);
        }

        // Assign initial miner state
        if (rc.getRoundNum() <= 20) {
            minerState = MinerState.SCOUTING;
        } else {
            minerState = MinerState.UNASSIGNED;
        }

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
        communication.trySendGenesisBlock(rc.getLocation(), 3);
        // Listen in on refinery/soup locations to include in rebroadcast
        // Calculate own health
        // Handle rebroadcast
        // Handle netgun
        // Build and assign miners
        // Send commands to build drone centers/refineries/etc
        for (Direction dir : directions) {
            if (minerCount < 4 && tryBuild(RobotType.MINER, dir)) {
                minerCount++;
            }
        }
    }

    static void runMiner() throws GameActionException {
        ArrayList<Integer> newSoupSectors = communication.checkSoupBroadcast();
        if (newSoupSectors.size() > 0) {
            soupSectors.addAll(newSoupSectors);
        }

        if (minerState == MinerState.SCOUTING
                || minerState == MinerState.RANDOM
                || minerState == MinerState.UNASSIGNED) {
            if (soupSectors.size() > 0) {
                travelQueue.clear();
                pathfinding.reset();
                minerState = MinerState.MINING;
                int closestSector = Sector.getClosestSector(Sector.getFromLocation(rc.getLocation()), soupSectors);
                System.out.println("Closest sector is " + closestSector + " with center at " + Sector.getCenter(closestSector, mapHeight, mapWidth));
                lastSoupDeposit = Sector.getCenter(closestSector, mapHeight, mapWidth);
                if (nextMiningSector == closestSector) {
                    miningState = MiningState.IN_PROGRESS;
                } else {
                    miningState = MiningState.ENROUTE;
                }
            }
        }

        System.out.println(">>> MINER STATE: " + minerState);
        System.out.println(">>> MINING STATE: " + miningState);

        switch(minerState) {
            case UNASSIGNED:
                break;
            case SCOUTING:
                scoutLocalQuadrant();
                break;
            case RANDOM:
                scoutRandom();
                break;
            case MINING:
                goMine();
                break;
            case BUILDING:
                goBuild();
                break;
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

    //////////////////////////////////////////////////
    // COMMUNICATION FUNCTIONS - also see Communication.java
    //////////////////////////////////////////////////

    static void retrieveSoupLocations() throws GameActionException {

    }

    //////////////////////////////////////////////////
    // PATHFINDING FUNCTIONS - see Pathfinding.java
    //////////////////////////////////////////////////


    //////////////////////////////////////////////////
    // MINER FUNCTIONS
    //////////////////////////////////////////////////

    static void scoutRandom() throws GameActionException {
        if (minerState == MinerState.RANDOM) {
            tryMove(randomDirection());
        }
    }

    static void scoutLocalQuadrant() throws GameActionException {
        if (travelQueue.isEmpty()) {
            MapLocation[] scoutTo = new MapLocation[4];
            MapLocation midpoint = new MapLocation((mapWidth-1)/2, (mapHeight-1)/2);
            Quadrant localQuadrant = getLocalQuadrant(localHQ, midpoint);
            int margin = 3;

            switch(localQuadrant) {
                case NORTHEAST:
                    scoutTo[0] = new MapLocation(midpoint.x + margin, midpoint.y + margin);
                    scoutTo[1] = new MapLocation(midpoint.x + margin, mapHeight - 1 - margin);
                    scoutTo[2] = new MapLocation(mapWidth - 1 - margin, midpoint.y + margin);
                    scoutTo[3] = new MapLocation(mapWidth - 1 - margin, mapHeight - 1 - margin);
                    break;
                case NORTHWEST:
                    scoutTo[0] = new MapLocation(margin, midpoint.y + margin);
                    scoutTo[1] = new MapLocation(margin, mapHeight - 1 - margin);
                    scoutTo[2] = new MapLocation(midpoint.x - margin, midpoint.y + margin);
                    scoutTo[3] = new MapLocation(midpoint.x - margin, mapHeight - 1 - margin);
                    break;
                case SOUTHEAST:
                    scoutTo[0] = new MapLocation(midpoint.x + margin, margin);
                    scoutTo[1] = new MapLocation(midpoint.x + margin, midpoint.y - margin);
                    scoutTo[2] = new MapLocation(mapWidth - 1 - margin, margin);
                    scoutTo[3] = new MapLocation(mapWidth - 1 - margin, midpoint.y - margin);
                    break;
                case SOUTHWEST:
                    scoutTo[0] = new MapLocation(margin, margin);
                    scoutTo[1] = new MapLocation(margin, midpoint.y - margin);
                    scoutTo[2] = new MapLocation(midpoint.x - margin, margin);
                    scoutTo[3] = new MapLocation(midpoint.x - margin, midpoint.y - margin);
                    break;
            }
            travelQueue.addLast(scoutTo[(int) (Math.random() * scoutTo.length)]);
        }

        if (rc.getLocation().equals(travelQueue.peekFirst())) {
            travelQueue.clear();
            pathfinding.reset();
            minerState = MinerState.RANDOM;
        }

        if (rc.isReady()) {
            if (!pathfinding.travelTo(travelQueue.peekFirst())) {
                travelQueue.clear();
                pathfinding.reset();
                minerState = MinerState.RANDOM;
            }
        }
        if (detectSoup()) {
            travelQueue.clear();
            pathfinding.reset();
        }
    }

    static Quadrant getLocalQuadrant(MapLocation loc, MapLocation midpoint) throws GameActionException {
        if (loc.x >= midpoint.x) {
            if (loc.y >= midpoint.y) {
                return Quadrant.NORTHEAST;
            } else {
                return Quadrant.SOUTHEAST;
            }
        } else {
            if (loc.y >= midpoint.y) {
                return Quadrant.NORTHWEST;
            } else {
                return Quadrant.SOUTHWEST;
            }
        }
    }

    static boolean detectSoup() throws GameActionException {
        // Try searching for nearby soup
        for (Direction dir : directions) {
            MapLocation temp = rc.adjacentLocation(dir);
            if (rc.canSenseLocation(temp) && rc.senseSoup(temp) > 0) {
                int currentSector = Sector.getFromLocation(temp);
                // Start mining it and add to set
                if (!soupSectors.contains(currentSector)) {
                    soupSectors.add(currentSector);
                    communication.broadcastSoup(currentSector);
                }
                nextMiningSector = currentSector;
                lastSoupDeposit = temp;
                minerState = MinerState.MINING;
                miningState = MiningState.IN_PROGRESS;
                tryMine(dir);
                return true;
            }
        }
        // Try searching entire vision radius
        MapLocation[] nearbySoup = rc.senseNearbySoup();
        if (nearbySoup.length > 0) {
            int currentSector = Sector.getFromLocation(nearbySoup[0]); // can optimize to find closest one
            lastSoupDeposit = nearbySoup[0];
            if (!soupSectors.contains(currentSector)) {
                soupSectors.add(currentSector);
                communication.broadcastSoup(currentSector);
            }
            nextMiningSector = currentSector;
            minerState = MinerState.MINING;
            miningState = MiningState.ENROUTE;
            return true;
        } else {
            return false;
        }
    }

    static void goMine() throws GameActionException {
        if (miningState == MiningState.ENROUTE) {
            if (travelQueue.isEmpty()) {
                if (Pathfinding.locIsNull(lastSoupDeposit)) {
                    lastSoupDeposit = Sector.getCenter(nextMiningSector, mapHeight, mapWidth);
                } travelQueue.add(lastSoupDeposit);
            }
            System.out.println("MINING ENROUTE TO: " + travelQueue.peekFirst());
            if (!travelQueue.isEmpty()) {
                if (rc.getLocation().isWithinDistanceSquared(travelQueue.peekFirst(), 2)) {
                    travelQueue.clear();
                    pathfinding.reset();
                    miningState = MiningState.IN_PROGRESS;
                } else {
                    if (rc.isReady()) {
                        // Add error checking in case it can't reach target
                        pathfinding.travelTo(travelQueue.peekFirst());
                    }
                }
            }
        }
        if (miningState == MiningState.IN_PROGRESS) {
            if (rc.isReady()) {
                performMining();
            }
        } else if (miningState == MiningState.REFINING) {
            MapLocation cLoc = rc.getLocation();
            if (travelQueue.isEmpty()) {
                refineryAccessAttempts = 0;
                MapLocation closestRefinery = refineries.get(0);
                int closestRefineryDist = cLoc.distanceSquaredTo(closestRefinery);
                for (int i = 1; i < refineries.size(); i++) {
                    if (cLoc.distanceSquaredTo(refineries.get(i)) < closestRefineryDist) {
                        closestRefinery = refineries.get(i);
                        closestRefineryDist = cLoc.distanceSquaredTo(refineries.get(i));
                    }
                }
                travelQueue.add(closestRefinery);
            }

            System.out.println("REFINING TRAVELQUEUE TOP IS " + travelQueue.peekFirst());
            if (!travelQueue.isEmpty()) {
                if (cLoc.isWithinDistanceSquared(travelQueue.peekFirst(), 2)) {
                    if (rc.canDepositSoup(cLoc.directionTo(travelQueue.peekFirst()))) {
                        rc.depositSoup(cLoc.directionTo(travelQueue.peekFirst()), rc.getSoupCarrying());
                        travelQueue.clear();
                        pathfinding.reset();
                        miningState = MiningState.ENROUTE;
                    }
                } else {
                    if (rc.isReady()) {
//                            pathfinding.travelTo(travelQueue.peekFirst());
//                            refineryAccessAttempts++;
//                            if (refineryAccessAttempts > 25) {
//                                travelQueue.clear();
//                                System.out.println("Can't reach refinery...");
//                            }
                        if (!pathfinding.travelTo(travelQueue.peekFirst())) {
                            travelQueue.clear();
                            pathfinding.reset();
                            System.out.println("Can't reach refinery...");
                            minerState = MinerState.RANDOM;
                        }
                    }
                }
            }
        }
    }

    static void performMining() throws GameActionException {
        if (!tryMine(Direction.CENTER)) {
            for (Direction dir : directions) {
                if (tryMine(dir)) {
                    lastSoupDeposit = rc.adjacentLocation(dir);
                    break;
                }
            }
            if (miningState == MiningState.IN_PROGRESS) {
                if (!detectSoup()) {
                    travelQueue.clear();
                    pathfinding.reset();
                    miningState = MiningState.ENROUTE;
                }
            }
        }

        if (rc.getSoupCarrying() == RobotType.MINER.soupLimit) {
            travelQueue.clear();
            pathfinding.reset();
            System.out.println("Reached miner limit. Soup holding: " + rc.getSoupCarrying());
            miningState = MiningState.REFINING;
        }
    }


    static void goBuild() throws GameActionException {

    }

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
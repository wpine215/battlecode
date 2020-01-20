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
    static MapLocation enemyHQ = new MapLocation(99,99); // assigned value is temporary
    static MapLocation[] possibleEnemyLocations;
    static int currentlyScoutingEnemyAt;

    // HQ-specific variables
    static int HQHealth = 50;
    static int HQElevation;
    static int minerCount = 0;
    static boolean hasBuiltDefensiveDesignSchool = false;
    static boolean hasBuildOffensiveDesignSchool = false;
    static int auxiliaryMinerRound = 1000;

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
    static boolean minerHasBuiltDefensiveDS = false;

    // Landscaper-specific variables
    static int landscapersBuilt = 0;
    static int offensiveLandscapersBuilt = 0;
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
    final static int soupNeededToBuildDesignSchool = 215;
    final static int landscaperRound = 40;
//    final static int refineryRound = 140;
//    final static int droneRound = 500;

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
        currentlyScoutingEnemyAt = 0;
        possibleEnemyLocations = new MapLocation[3];
        possibleEnemyLocations[0] = new MapLocation(mapWidth - 1 - localHQ.x, localHQ.y);
        possibleEnemyLocations[1] = new MapLocation(mapWidth - 1 - localHQ.x, mapHeight - 1 -localHQ.y);
        possibleEnemyLocations[2] = new MapLocation(localHQ.x, mapHeight - 1 - localHQ.y);

        refineries = new ArrayList<>();
        soupSectors = new HashSet<>();
        lastSoupDeposit = new MapLocation(-1, -1);

        if (rc.getType() == RobotType.MINER) {
            refineries.add(localHQ);
            // Assign initial miner state
            if (rc.getRoundNum() <= 20) {
                minerState = MinerState.SCOUTING;
            } else if (rc.getRoundNum() >= landscaperRound && rc.getTeamSoup() > 140) {
                minerState = MinerState.BUILDING;
            } else {
                minerState = MinerState.UNASSIGNED;
            }
        }

        if (rc.getType() == RobotType.DESIGN_SCHOOL || rc.getType() == RobotType.LANDSCAPER) {
            if (rc.getRoundNum() >= 6) {
                Transaction rb = communication.getLastRebroadcast();
                MapLocation tryEnemyHQ = communication.getEnemyHQFromRebroadcast(rb);
                if (!Pathfinding.locIsNull(tryEnemyHQ)) {
                    enemyHQ = tryEnemyHQ;
                }
            }
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
        // Broadcast genesis message if applicable
        if (rc.getRoundNum() == 1) {
            communication.trySendGenesisBlock(rc.getLocation(), 3);
        } else {
            // Get previous round block
            Transaction[] currentBlock = rc.getBlock(rc.getRoundNum() - 1);

            // Listen in on refinery/soup locations to include in rebroadcast
            ArrayList<Integer> newSoupSectors = communication.checkSoupBroadcast(currentBlock);
            ArrayList<MapLocation> newRefineries = communication.checkNewRefineries(currentBlock);
            if (newSoupSectors.size() > 0) {
                soupSectors.addAll(newSoupSectors);
            }
            if (newRefineries.size() > 0 ) {
                refineries.addAll(newRefineries);
            }

            // Check if any soup locations are empty
            ArrayList<Integer> emptySoupSectors = communication.checkEmptySoupBroadcast(currentBlock);
            if (emptySoupSectors.size() > 0) {
                soupSectors.removeAll(emptySoupSectors);
            }

            // Check for enemy HQ broadcast
            MapLocation tryEnemyHQ = communication.checkEnemyHQBroadcast(currentBlock);
            if (!Pathfinding.locIsNull(tryEnemyHQ)) {
                enemyHQ = tryEnemyHQ;
            }
        }

        // Calculate own health
        HQHealth = 50 - rc.getDirtCarrying();

        // Handle rebroadcast
        if (rc.getRoundNum() % 10 == 5) {
            communication.trySendRebroadcastBlock(1, soupSectors, refineries, enemyHQ, HQHealth);
        }

        // Handle netgun
        runNetGun();

        // Build and assign miners
        // TODO: Assign miner to scout enemy HQ, build offensive design school
        // TODO: Assign miner to build refinery + defensive design school near HQ
        for (Direction dir : directions) {
            if (minerCount < 4 && tryBuild(RobotType.MINER, dir)) {
                minerCount++;
            }
        }

        // Build auxiliary miner to build design school and scout enemy HQ
        if (!hasBuiltDefensiveDesignSchool && rc.getTeamSoup() >= soupNeededToBuildDesignSchool) {
            for (Direction dir : directions) {
                if (tryBuild(RobotType.MINER, dir)) {
                    hasBuiltDefensiveDesignSchool = true;
                    break;
                }
            }
        }

        // Send commands to build drone centers/refineries/etc
    }

    static void runMiner() throws GameActionException {
        // Check direct soup broadcasts
        Transaction[] currentBlock = rc.getBlock(rc.getRoundNum() - 1);
        ArrayList<Integer> newSoupSectors = communication.checkSoupBroadcast(currentBlock);
        if (newSoupSectors.size() > 0) {
            System.out.println("New soup sectors to be added from direct broadcast are: " + newSoupSectors);
            soupSectors.addAll(newSoupSectors);
        }

        System.out.println("soupSectors contents: " + soupSectors);

        // Check rebroadcast
        if (rc.getRoundNum() % 10 == 6) {
            Transaction rb = communication.getLastRebroadcast();
            ArrayList<Integer> newRbSoupSectors = communication.getSoupFromRebroadcast(rb);
            ArrayList<MapLocation> newRbRefineries = communication.getRefineriesFromRebroadcast(rb);
            MapLocation tryEnemyHQ = communication.getEnemyHQFromRebroadcast(rb);
            if (newRbSoupSectors.size() > 0) {
                System.out.println("New soup sectors to be added/replaced from rebroadcast are: " + newRbSoupSectors);
                soupSectors.clear(); // this line makes the rebroadcast replace, not just add, to soupSectors
                soupSectors.addAll(newRbSoupSectors);
            }
            if (newRbRefineries.size() > 0) {
                refineries.clear(); // this line makes the rebroadcast replace, not just add, to refineries
                refineries.addAll(newRbRefineries);
            }
            if (!Pathfinding.locIsNull(tryEnemyHQ)) {
                enemyHQ = tryEnemyHQ;
            }
        }

        // Assign a miner to get soup if there is some available
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
//                scoutLocalQuadrant();
                scoutGlobalQuadrant();
                break;
            case RANDOM:
                scoutRandom();
                break;
            case MINING:
                goMine();
                break;
            case BUILDING:
                if (!minerHasBuiltDefensiveDS) {
                    goBuild();
                } else {
                    goOffensiveMiner();
                }
                break;
        }
    }

    static void runRefinery() throws GameActionException {

    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
        // Check if offense design school
        if (!Pathfinding.locIsNull(enemyHQ) && rc.getLocation().isWithinDistanceSquared(enemyHQ, 50)) {
            for (Direction dir : directions) {
                if (rc.getRoundNum() > landscaperRound && offensiveLandscapersBuilt < 3) {
                    if (tryBuild(RobotType.LANDSCAPER, dir)) {
                        offensiveLandscapersBuilt++;
                        break;
                    }
                }
            }
            return;
        }

        // Generate landscaper in random direction if able
        for (Direction dir : directions) {
            if (rc.getRoundNum() > landscaperRound && landscapersBuilt < 8) {
                if (tryBuild(RobotType.LANDSCAPER, dir)) {
                    landscapersBuilt++;
                }
            }
        }
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
        RobotInfo[] allRobots = rc.senseNearbyRobots();
        for (RobotInfo ri : allRobots) {
            if (ri.team != rc.getTeam()) {
                if (rc.canShootUnit(ri.ID)) {
                    rc.shootUnit(ri.ID);
                }
            }
        }
    }

    //////////////////////////////////////////////////
    // UTILITY FUNCTIONS
    //////////////////////////////////////////////////

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

    static void scoutGlobalQuadrant() throws GameActionException {
        if (travelQueue.isEmpty()) {
            MapLocation[] scoutTo = new MapLocation[4];
            MapLocation midpoint = new MapLocation((mapWidth-1)/2, (mapHeight-1)/2);
            Quadrant localQuadrant = getLocalQuadrant(localHQ, midpoint);
            int margin = 5;

            scoutTo[0] = new MapLocation(margin, margin);
            scoutTo[1] = new MapLocation(margin, mapHeight - 1 - margin);
            scoutTo[2] = new MapLocation(mapWidth - 1 - margin, margin);
            scoutTo[3] = new MapLocation(mapWidth - 1 - margin, mapHeight - 1 - margin);

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
                    // No more soup found in this sector
                    int currentSector = Sector.getFromLocation(rc.getLocation());
                    soupSectors.remove(nextMiningSector);
                    soupSectors.remove(currentSector);
                    ArrayList<Integer> broadcastArgs = new ArrayList<>();
                    broadcastArgs.add(nextMiningSector);
                    broadcastArgs.add(currentSector);
                    communication.broadcastEmptySoup(broadcastArgs);
                    minerState = MinerState.SCOUTING;
                    System.out.println(" >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> No more soup found! :(");
                    return;
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

    // Build defensive design school
    static void goBuild() throws GameActionException {
        for (Direction dir : directions) {
            if (!rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 18)
                    && rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 72)) {
                if (tryBuild(RobotType.DESIGN_SCHOOL, dir)) {
                    minerHasBuiltDefensiveDS = true;
                    break;
                }
            }
        }
        tryMove(randomDirection());
    }

    static void goOffensiveMiner() throws GameActionException {
        if (Pathfinding.locIsNull(enemyHQ)) {
            pathfinding.travelTo(possibleEnemyLocations[currentlyScoutingEnemyAt]);
            RobotInfo[] nearby = rc.senseNearbyRobots();
            for (RobotInfo r : nearby) {
                if (r.type == RobotType.HQ && r.team !=rc.getTeam()) {
                    enemyHQ = r.location;
                    communication.announceEnemyHQ(r.location);
                    System.out.println(">>>> FOUND ENEMY HQ AT" + enemyHQ);
                }
            }

            if (Pathfinding.locIsNull(enemyHQ) && rc.canSenseLocation(possibleEnemyLocations[currentlyScoutingEnemyAt])) {
                if (currentlyScoutingEnemyAt < 2) {
                    currentlyScoutingEnemyAt++;
                } else {
                    minerState = MinerState.SCOUTING;
                }
                pathfinding.reset();
            }
        }
        if (!Pathfinding.locIsNull(enemyHQ)){
            if (rc.getLocation().isWithinDistanceSquared(enemyHQ, 20)) { // can change to larger value later
                if (!hasBuildOffensiveDesignSchool) {
                    for (Direction dir : directions) {
                        if (tryBuild(RobotType.DESIGN_SCHOOL, dir)) {
                            hasBuildOffensiveDesignSchool = true;
                            pathfinding.reset();
                            minerState = MinerState.SCOUTING;
                            break;
                        }
                    }
                }
            } else {
                pathfinding.travelTo(enemyHQ);
            }
        }
    }

    //////////////////////////////////////////////////
    // LANDSCAPER FUNCTIONS
    //////////////////////////////////////////////////

    static void assignLandscaper() throws GameActionException {
        if (rc.getCooldownTurns() < 2) {
            System.out.println("assignLandscaper: enemy HQ is " + enemyHQ);
            if (!Pathfinding.locIsNull(enemyHQ) && rc.getLocation().isWithinDistanceSquared(enemyHQ, 72)) {
                landscaperState = LandscaperState.OFFENSE;
            } else {
                landscaperState = LandscaperState.DEFENSE;
            }
        }
    }

    static void runOffenseLandscaper() throws GameActionException {
        /*
         * if (HQHealth < 50 or broadcast of death) {
         *      become defense
         *      recur?
         * }
         */
        /*
         * if enemyHQ not known:
         * 1. check blockchain for that info
         * 2. if not found, then go scouting
         */
        if (enemyHQ != null) {
            if (rc.getLocation().isWithinDistanceSquared(enemyHQ, 2)) {
                Direction enemyHQDir = rc.getLocation().directionTo(enemyHQ);
                Direction digHere = enemyHQDir.opposite();
                while (rc.canDepositDirt(enemyHQDir)) {
                    rc.depositDirt(enemyHQDir);
                }
                while (rc.canDigDirt(digHere)) {
                    rc.digDirt(digHere);
                }
            } else {
                System.out.println("TRAVELING TO ENEMY HQ AT: " + enemyHQ);
                pathfinding.travelTo(enemyHQ);
            }
        }
    }
/*
    need to add a check in HQ for wall being full, and if it is, broadcast that information
    */
    /**
     * decision tree:
     * 1. if part of the wall, just dig and contribute
     * 2. else:
     *      0. if you don't know HQ: get its location
     *      1. if wall is full, become offense
     *      2. if wall is not full as far as you know:
     *          1. if HQ in vision, try to embed yourself into the wall
     *          2. if HQ not in vision, move towards HQ
     */
    static void runDefenseLandscaper() throws GameActionException {
        System.out.println("RUNNING DEFENSE LANDSCAPER");
        System.out.println("COOLDOWN TURNS REMAINING:" + rc.getCooldownTurns());
        // if part of the wall, build the wall
        try {
            if (wallDirection != null) {
                while (rc.canDepositDirt(Direction.CENTER)) {
                    rc.depositDirt(Direction.CENTER);
                }
                while (rc.canDigDirt(wallDirection)) {
                    rc.digDirt(wallDirection);
                }
                return;
            }
        } catch (GameActionException e) {}

        /*
        if (false) {
            if (rc.canSenseLocation(localHQ)) {
                for (RobotInfo info : rc.senseNearbyRobots()) {
                    if (info.team == rc.getTeam().opponent()) {
                        // move towards it
                        // attack it
                        // break
                    }
                }
            } else {
                try { while (moveToTarget(localHQ)) {} } catch (GameActionException e) {}
            }
        }
        */

        // if wall is full, become offense
        if (rc.canSenseLocation(localHQ)) {
            if (!tryBuildWall()) {
                return;
            }
        } else {
            System.out.println("Trying to move to HQ!");
//            try {
//                while (pathfinding.travelTo(localHQ)) {
//
//                }
//            } catch (GameActionException e) {}
            pathfinding.travelTo(localHQ);
        }
    }

    // I want HQ to broadcast until wall is finished
    // corner case bug to consider: you haven't labeled yourself as the wall yet but you're standing where the wall would be
    static boolean tryBuildWall() {
        Direction empty = null;
        for (Direction dir: wallQueue) {
            try {
                if (!rc.isLocationOccupied(new MapLocation(localHQ.x + dir.dx, localHQ.y + dir.dy))) {
                    empty = dir;
                    break;
                }
            } catch (GameActionException e) {
                empty = dir;
                break;
            }
        }

        if (empty == null) {
            wallBuilt = true;
            return false;
        }

        for (Direction dir: directions) {
            if (rc.getLocation().x == localHQ.x + dir.dx &&  rc.getLocation().y == localHQ.y + dir.dy) {
                wallDirection = dir;
                return true;
            }
        }

        MapLocation chosenLocation = new MapLocation(localHQ.x + empty.dx, localHQ.y + empty.dy);
        try {
            pathfinding.travelTo(chosenLocation);
        } catch (GameActionException e) {}

        if (rc.getLocation() == chosenLocation) {
            System.out.println("We have been set into the wall");
            wallDirection = empty;
        }

        for (Direction dir: directions) {
            if (rc.getLocation().x == localHQ.x + dir.dx &&  rc.getLocation().y == localHQ.y + dir.dy) {
                wallDirection = dir;
                return true;
            }
        }
        return true;
    }

    //////////////////////////////////////////////////
    // OTHER FUNCTIONS
    //////////////////////////////////////////////////

}
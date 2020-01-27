package stardustv2;
import battlecode.common.*;

import javax.rmi.CORBA.Util;
import java.util.*;

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
    BUILDING,
    OFFENSIVE
}

enum MiningState {
    ENROUTE,
    IN_PROGRESS,
    REFINING
}

enum LandscaperState {
    UNASSIGNED,
    OFFENSE,
    DEFENSE,
    LATTICE
}

enum LatticeState {
    LATTICE_WALL,
    NOT_ON_LATTICE,
    ON_LATTICE
}

enum DroneState {
    PASSIVE,
    TRANSPORT,
    OFFENSE
}

public strictfp class RobotPlayer {
    static RobotController rc;

//    static Direction[] directions = {
//        Direction.NORTH,
//        Direction.NORTHEAST,
//        Direction.EAST,
//        Direction.SOUTHEAST,
//        Direction.SOUTH,
//        Direction.SOUTHWEST,
//        Direction.WEST,
//        Direction.NORTHWEST
//    };

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
    static int localHQID;
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

    // Utility/general-purpose functions
    static Utility ut;

    // Miner-specific variables
    static MinerState minerState;
    static MiningState miningState;
    static ArrayList<MapLocation> refineries;
    static Set<Integer> soupSectors;
    static MapLocation lastSoupDeposit;
    static int refineryAccessAttempts = 0;
    static int unfruitfulRounds = 0;
    static boolean minerHasBuiltDefensiveDS = false;
    static boolean minerHasBuiltAuxiliaryRefinery = false;
    static boolean minerHasBuiltDroneCenter = false;

    // Landscaper-specific variables
    static int landscapersBuilt = 0;
    static int offensiveLandscapersBuilt = 0;
    static boolean wallBuilt = false;
    static int roundsSinceWallBuilt = 0;
    static Direction wallDirection;
    static ArrayList<Direction> wallQueue;
    static LandscaperState landscaperState = LandscaperState.UNASSIGNED;

    static boolean latticeWallBuilt = false;
    static List<MapLocation> latticeWallCoordinates = new ArrayList<>();
    static int LATTICE_ELEVATION = 8;
    static MapLocation latticeWallLoc;
    static LatticeState latticeState = LatticeState.LATTICE_WALL;

    // Some constants
    final static int soupNeededToBuildDesignSchool = 215;
    final static int soupNeededToBuildDroneCenter = 230;
    final static int landscaperRound = 40;

    static int turnCount;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        RobotPlayer.rc = rc;
        turnCount = 0;

        // Instantiate pathfinding
        pathfinding = new Pathfinding(rc, localHQ);
        travelQueue = new LinkedList<>();
        nextMiningSector = 99;

        // Instantiate communications
        communication = new Communication(rc);

        // Initialize some variables defined above
        mapHeight = rc.getMapHeight();
        mapWidth = rc.getMapWidth();
        Transaction genesisBlock = communication.getGenesisBlock();
        if (genesisBlock != null) {
            localHQ = communication.getHQCoordinates(genesisBlock);
            localHQID = communication.getHQID(genesisBlock);
        } else {
            localHQ = rc.getLocation();
            localHQID = -1;
        }

        // Initialize utility class
        ut = new Utility(rc, mapHeight, mapWidth, localHQ);

        currentlyScoutingEnemyAt = 0;
        possibleEnemyLocations = new MapLocation[3];
        possibleEnemyLocations[0] = new MapLocation(mapWidth - 1 - localHQ.x, localHQ.y);
        possibleEnemyLocations[1] = new MapLocation(mapWidth - 1 - localHQ.x, mapHeight - 1 -localHQ.y);
        possibleEnemyLocations[2] = new MapLocation(localHQ.x, mapHeight - 1 - localHQ.y);

        refineries = new ArrayList<>();
        soupSectors = new HashSet<>();
        lastSoupDeposit = new MapLocation(-1, -1);

        if (rc.getType() == RobotType.MINER) {
            System.out.println("I WAS BUILT IN ROUND " + rc.getRoundNum());
            refineries.add(localHQ);
            // Assign initial miner state
            if (rc.getRoundNum() == 2) {
                minerState = MinerState.OFFENSIVE;
            }  else if (rc.getRoundNum() > 2 && rc.getRoundNum() <= 30) {
                minerState = MinerState.SCOUTING;
            } else if (rc.getRoundNum() > landscaperRound && rc.getTeamSoup() > 140) {
                // TODO: BUILD ORDER ERROR HERE: if offensive design school is built before this robot, it will never build defensive design school as team soup will be less than 140
                minerState = MinerState.BUILDING;
            } else {
                minerState = MinerState.UNASSIGNED;
            }
        }

        if (rc.getType() == RobotType.DESIGN_SCHOOL || rc.getType() == RobotType.LANDSCAPER) {
            if (rc.getRoundNum() >= 6) {
                Transaction rb = communication.getLastRebroadcast();
                MapLocation tryEnemyHQ = communication.getEnemyHQFromRebroadcast(rb);
                System.out.println("ENEMY HQ FROM BROADCAST IS " + tryEnemyHQ);
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
            communication.trySendGenesisBlock(rc.getLocation(), rc.getID(), 3);
        } else {
            // Get previous round block
            Transaction[] currentBlock = rc.getBlock(rc.getRoundNum() - 1);

            // Listen in on refinery/soup locations to include in rebroadcast
            ArrayList<Integer> newSoupSectors = communication.checkSoupBroadcast(currentBlock);
            ArrayList<MapLocation> newRefineries = communication.checkNewRefineries(currentBlock);
            if (newSoupSectors.size() > 0) {
                soupSectors.addAll(newSoupSectors);
            }
            if (newRefineries.size() > 0) {
                refineries.addAll(newRefineries);
                // temporary fix to remove HQ
                refineries.remove(localHQ);
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
        for (Direction dir : Utility.getDirections()) {
            if (minerCount < 4 && ut.tryBuild(RobotType.MINER, dir)) {
                minerCount++;
                break;
            }
        }

        // Build auxiliary miner to build design school and scout enemy HQ
        if (!hasBuiltDefensiveDesignSchool && rc.getTeamSoup() >= soupNeededToBuildDesignSchool) {
            for (Direction dir : Utility.getDirections()) {
                if (ut.tryBuild(RobotType.MINER, dir)) {
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
                soupSectors.clear();
                soupSectors.addAll(newRbSoupSectors);
            }
            if (newRbRefineries.size() > 0) {
                refineries.clear();
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
                    goBuildDefensiveDS();
                } else if (!minerHasBuiltAuxiliaryRefinery) {
                    goBuildAuxiliaryRefinery();
                } else if (!minerHasBuiltDroneCenter) {
                    goBuildDroneCenter();
                } else {
                    minerState = MinerState.SCOUTING;
                }
                break;
            case OFFENSIVE:
                goOffensiveMiner();
                break;
        }
    }

    static void runRefinery() throws GameActionException {

    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
        if (rc.getRoundNum() >= 6 && Pathfinding.locIsNull(enemyHQ)) {
            Transaction rb = communication.getLastRebroadcast();
            MapLocation tryEnemyHQ = communication.getEnemyHQFromRebroadcast(rb);
            System.out.println("ENEMY HQ FROM BROADCAST IS " + tryEnemyHQ);
            if (!Pathfinding.locIsNull(tryEnemyHQ)) {
                enemyHQ = tryEnemyHQ;
            }
        }

        // Check if offense design school
        System.out.println("DESIGN SCHOOL: offensive landscapers built: " + offensiveLandscapersBuilt);
        System.out.println("ENEMY HQ: " + enemyHQ);
        if (!Pathfinding.locIsNull(enemyHQ) && rc.getLocation().isWithinDistanceSquared(enemyHQ, 50)) {
            for (Direction dir : Utility.getDirections()) {
                if (rc.getRoundNum() > landscaperRound && offensiveLandscapersBuilt < 2) {
                    if (ut.tryBuild(RobotType.LANDSCAPER, dir)) {
                        System.out.println("Successfully built offensive landscaper!");
                        System.out.println("Count before : " + offensiveLandscapersBuilt);
                        offensiveLandscapersBuilt++;
                        System.out.println("Count after : " + offensiveLandscapersBuilt);
                    }
                }
            }
            return;
        }

        if (wallQueue == null) {
            wallQueue = new ArrayList<>();
            MapLocation DS = rc.getLocation();
            wallQueue.add(DS.directionTo(localHQ));
            wallQueue.add(Utility.rotateXTimesLeft(wallQueue.get(0), 1));
            wallQueue.add(Utility.rotateXTimesRight(wallQueue.get(0), 1));
            wallQueue.add(Utility.rotateXTimesLeft(wallQueue.get(0), 2));
            wallQueue.add(Utility.rotateXTimesRight(wallQueue.get(0), 2));
            wallQueue.add(Utility.rotateXTimesLeft(wallQueue.get(0), 3));
            wallQueue.add(Utility.rotateXTimesRight(wallQueue.get(0), 3));
            wallQueue.add(Utility.rotateXTimesLeft(wallQueue.get(0), 4));
        }

        // Generate landscaper in random direction if able
        for (Direction dir : wallQueue) {
            if (rc.getRoundNum() > landscaperRound && landscapersBuilt < 9) {
                if (ut.tryBuild(RobotType.LANDSCAPER, dir)) {
                    landscapersBuilt++;
                }
            }
        }
    }

    static void runFulfillmentCenter() throws GameActionException {
        // Generate drone in random direction if able
        for (Direction dir : Utility.getDirections()) {
            if (ut.tryBuild(RobotType.DELIVERY_DRONE, dir)) {
                break;
            }
        }
    }

    static void runLandscaper() throws GameActionException {
        if (rc.getRoundNum() >= 6) {
            Transaction rb = communication.getLastRebroadcast();
            MapLocation tryEnemyHQ = communication.getEnemyHQFromRebroadcast(rb);
            System.out.println("ENEMY HQ FROM BROADCAST IS " + tryEnemyHQ);
            if (!Pathfinding.locIsNull(tryEnemyHQ)) {
                enemyHQ = tryEnemyHQ;
            }
        }
        switch(landscaperState) {
            case UNASSIGNED:    assignLandscaper();     break;
            case OFFENSE:       runOffenseLandscaper(); break;
            case DEFENSE:       runDefenseLandscaper(); break;
            case LATTICE:       runLatticeLandscaper(); break;
        }
    }

    static void runDeliveryDrone() throws GameActionException {
        // ORIGINAL DRONE CODE //////////////////////////////////////////////////////////////////////////
        Team enemy = rc.getTeam().opponent();
        if (!rc.isCurrentlyHoldingUnit()) {
            // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
            RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED, enemy);
            if (robots.length > 0) {
                // Pick up a first robot within range
                rc.pickUpUnit(robots[0].getID());
                System.out.println("I picked up " + robots[0].getID() + "!");
            }
        } else {
            for (Direction dir : Utility.getDirections()) {
                if (rc.senseFlooding(rc.adjacentLocation(dir))) {
                    if (rc.canDropUnit(dir)) {
                        rc.dropUnit(dir);
                    }
                }
            }
        }
        ut.tryMove(ut.randomDirection());
        //////////////////////////////////////////////////////////////////////////////////////////////
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
    // MINER FUNCTIONS
    //////////////////////////////////////////////////

    static void scoutRandom() throws GameActionException {
        if (minerState == MinerState.RANDOM) {
            ut.tryMove(ut.randomDirection());
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
        for (Direction dir : Utility.getDirections()) {
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
                ut.tryMine(dir);
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

    static void staticDetectSoup() throws GameActionException {
        MapLocation[] nearbySoup = rc.senseNearbySoup();
        for (MapLocation loc : nearbySoup) {
            if (!soupSectors.contains(Sector.getFromLocation(loc))) {
                soupSectors.add(Sector.getFromLocation(loc));
                communication.broadcastSoup(Sector.getFromLocation(loc));
                return;
            }
        }
    }

    static void goMine() throws GameActionException {
        if (miningState == MiningState.ENROUTE) {
            // TODO: fix miners going in circles. This occurs when a miner is trying to reach the center of a soup sector that is inaccessible. Condition signal: ENROUTE & reaches obstacle point again
            // TODO: -> when this occurs, just mine any soup within range if available when circling, and if none found and reaches obstacle point again, then mark sector as inaccessible/empty?
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
                            // TODO: try reaching next closest refinery, if available
                            minerState = MinerState.RANDOM;
                        }
                    }
                }
            }
        }
    }

    static void performMining() throws GameActionException {
        if (!ut.tryMine(Direction.CENTER)) {
            for (Direction dir : Utility.getDirections()) {
                if (ut.tryMine(dir)) {
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
    static void goBuildDefensiveDS() throws GameActionException {
        for (Direction dir : Utility.getDirections()) {
            if (!rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 2)
                    && rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 50)) {
                if (ut.tryBuild(RobotType.DESIGN_SCHOOL, dir)) {
                    minerHasBuiltDefensiveDS = true;
                    break;
                }
            }
        }
        ut.tryMove(ut.randomDirection());
    }

    static void goBuildAuxiliaryRefinery() throws GameActionException {
        System.out.println("Trying to build extra refinery!");
        for (Direction dir : Utility.getDirections()) {
            if (!rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 25)) {
                if (ut.tryBuild(RobotType.REFINERY, dir)) {
                    minerHasBuiltAuxiliaryRefinery = true;
                    communication.announceNewRefinery(rc.adjacentLocation(dir));
                    break;
                }
            }
        }
        ut.tryMove(ut.randomDirection());
        staticDetectSoup();
    }

    static void goBuildDroneCenter() throws GameActionException {
        if (rc.getTeamSoup() >= soupNeededToBuildDroneCenter) {
            for (Direction dir : Utility.getDirections()) {
                if (!rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 25)) {
                    if (ut.tryBuild(RobotType.FULFILLMENT_CENTER, dir)) {
                        minerHasBuiltDroneCenter = true;
                        break;
                    }
                }
            }
        }
        ut.tryMove(ut.randomDirection());
        staticDetectSoup();
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
            if (rc.getLocation().isWithinDistanceSquared(enemyHQ, 8)) { // can change to larger value later
                if (!hasBuildOffensiveDesignSchool) {
                    for (Direction dir : Utility.getDirections()) {
                        if (ut.tryBuild(RobotType.DESIGN_SCHOOL, dir)) {
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
            if (!Pathfinding.locIsNull(enemyHQ) && rc.getLocation().isWithinDistanceSquared(enemyHQ, 18)) {
                landscaperState = LandscaperState.OFFENSE;
            } else {
                landscaperState = LandscaperState.DEFENSE;
            }
        }
    }

    static boolean isWallBuilt() throws GameActionException {
        Direction empty = null;
        // Rebuild wall queue with direction opposite design school first
        if (wallQueue == null) {
            wallQueue = new ArrayList<>();
            MapLocation DS = rc.getLocation();
            RobotInfo[] nearby = rc.senseNearbyRobots(18, rc.getTeam());
            for (RobotInfo ri : nearby) {
                if (ri.getType() == RobotType.DESIGN_SCHOOL) {
                    DS = ri.location;
                }
            }

            Direction temp;
            Direction origin = DS.directionTo(localHQ);

            temp = origin;
            if (rc.onTheMap(localHQ.add(temp))
                    && !ut.dirtDifferenceAboveX(localHQ, localHQ.add(temp))
                    && !ut.locationIsWallDeadzone(temp)) {
                wallQueue.add(temp);
            }

            for (int i = 1; i <= 4; i++) {
                temp = Utility.rotateXTimesLeft(origin, i);
                if (rc.onTheMap(localHQ.add(temp))
                        && !ut.dirtDifferenceAboveX(localHQ, localHQ.add(temp))
                        && !ut.locationIsWallDeadzone(temp)) {
                    wallQueue.add(temp);
                }

                if (i != 4) {
                    temp = Utility.rotateXTimesRight(origin, i);
                    if (rc.onTheMap(localHQ.add(temp))
                            && !ut.dirtDifferenceAboveX(localHQ, localHQ.add(temp))
                            && !ut.locationIsWallDeadzone(temp)) {
                        wallQueue.add(temp);
                    }
                }
            }
        }

        for (Direction dir: wallQueue) {
            System.out.println("IS WALL BUILT: TRYING WALLQUEUE DIRECTION: " + dir + " AT COORDINATES " + new MapLocation(localHQ.x + dir.dx, localHQ.y + dir.dy));
            MapLocation temp = new MapLocation(localHQ.x + dir.dx, localHQ.y + dir.dy);
            if (!rc.canSenseLocation(temp)
                    || !rc.isLocationOccupied(temp)
                    || (rc.isLocationOccupied(temp) && rc.senseRobotAtLocation(temp).getType() != RobotType.LANDSCAPER)) {
                empty = dir;
                break;
            }
        }

        return empty == null;
    }

    static void runOffenseLandscaper() throws GameActionException {
        if (enemyHQ != null) {
            if (rc.getLocation().isWithinDistanceSquared(enemyHQ, 2)) {
                Direction enemyHQDir = rc.getLocation().directionTo(enemyHQ);
//                Direction digHere = enemyHQDir.opposite();
                Direction digHere = Direction.CENTER;
                while (rc.canDepositDirt(enemyHQDir)) {
//                  TODO: COMMENT FOLLOWING LINE ONLY IN DEVELOPMENT!
//                    rc.depositDirt(enemyHQDir);
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

    static void runDefenseLandscaper() throws GameActionException {
        // if part of the wall, build the wall
        if (wallDirection != null) {
            if (!rc.onTheMap(new MapLocation(rc.getLocation().x + wallDirection.dx, rc.getLocation().y + wallDirection.dy))) {
                Direction[] queue = new Direction[4];
                queue[0] = wallDirection.rotateLeft();
                queue[1] = wallDirection.rotateRight();
                queue[2] = queue[0].rotateLeft();
                queue[3] = queue[1].rotateRight();

                for (Direction elem : queue) {
                    MapLocation option = new MapLocation(rc.getLocation().x + elem.dx, rc.getLocation().y + elem.dy);
                    if (rc.onTheMap(option) && rc.senseElevation(option) > 0) {
                        wallDirection = elem;
                        break;
                    }
                }
            }

            if (!wallBuilt && isWallBuilt()) {
                wallBuilt = true;
            }

            if (wallBuilt && roundsSinceWallBuilt < 10) {
                roundsSinceWallBuilt++;
            }

            // Generate alternate deposit queue
            ArrayList<MapLocation> alternateDeposit = new ArrayList<>();
            Direction oppositeHQ = rc.getLocation().directionTo(localHQ).opposite();
            MapLocation leftSideLoc;
            MapLocation rightSideLoc;
            if (oppositeHQ == Direction.NORTH
                    || oppositeHQ == Direction.SOUTH
                    || oppositeHQ == Direction.WEST
                    || oppositeHQ == Direction.EAST) {
                leftSideLoc = rc.adjacentLocation(Utility.rotateXTimesLeft(oppositeHQ, 2));
                rightSideLoc = rc.adjacentLocation(Utility.rotateXTimesRight(oppositeHQ, 2));
            } else {
                leftSideLoc = rc.adjacentLocation(Utility.rotateXTimesLeft(oppositeHQ, 3));
                rightSideLoc = rc.adjacentLocation(Utility.rotateXTimesRight(oppositeHQ, 3));
            }

            // Check if local design school is flooded/destroyed
            boolean localDSDestroyed = true;
            RobotInfo[] nearbyRI = rc.senseNearbyRobots(rc.getCurrentSensorRadiusSquared(), rc.getTeam());
            for (RobotInfo ri : nearbyRI) {
                if (ri.getType() == RobotType.DESIGN_SCHOOL) {
                    localDSDestroyed = false;
                    break;
                }
            }

            if ((wallBuilt && roundsSinceWallBuilt == 10) || localDSDestroyed
                    || rc.isLocationOccupied(rightSideLoc) && rc.senseRobotAtLocation(rightSideLoc).getTeam() != rc.getTeam()) {
                alternateDeposit.add(leftSideLoc);
            }
            if ((wallBuilt && roundsSinceWallBuilt == 10) || localDSDestroyed
                    || rc.isLocationOccupied(rightSideLoc) && rc.senseRobotAtLocation(rightSideLoc).getTeam() != rc.getTeam()) {
                alternateDeposit.add(rightSideLoc);
            }

            int minAltElevation = 9999;
            MapLocation minAltLocation = new MapLocation(-1, -1);
            if (alternateDeposit.size() == 2) {
                if (rc.senseElevation(alternateDeposit.get(0)) < rc.senseElevation(alternateDeposit.get(1))) {
                    minAltLocation = alternateDeposit.get(0);
                } else {
                    minAltLocation = alternateDeposit.get(1);
                }
                minAltElevation = rc.senseElevation(minAltLocation);
            } else if (alternateDeposit.size() == 1) {
                minAltLocation = alternateDeposit.get(0);
                minAltElevation = rc.senseElevation(minAltLocation);
            }

            if (minAltElevation != 9999
                    && !Pathfinding.locIsNull(minAltLocation)
                    && minAltElevation + 5 < rc.senseElevation(rc.getLocation())
                    && rc.canDepositDirt(rc.getLocation().directionTo(minAltLocation))) {
                rc.depositDirt(rc.getLocation().directionTo(minAltLocation));
            } else if (rc.canDepositDirt(Direction.CENTER)) {
                rc.depositDirt(Direction.CENTER);
            }

            // If HQ under attack, dig it out!
            if (rc.canSenseRobot(localHQID)) {
                if (rc.senseRobot(localHQID).dirtCarrying > 0) {
                    if (rc.canDigDirt(rc.getLocation().directionTo(localHQ))) {
                        rc.digDirt(rc.getLocation().directionTo(localHQ));
                    }
                }
            }

            RobotInfo otherBot = rc.senseRobotAtLocation(new MapLocation(rc.getLocation().x + wallDirection.dx, rc.getLocation().y + wallDirection.dy));

            if (rc.isReady() && rc.getDirtCarrying() < 25) {
                if (rc.canDigDirt(wallDirection)
                        && !(otherBot != null && otherBot.team == rc.getTeam() && otherBot.getType() == RobotType.DESIGN_SCHOOL)) {
                    rc.digDirt(wallDirection);
                } else if (rc.canDigDirt(wallDirection.rotateLeft())) {
                    RobotInfo temp = rc.senseRobotAtLocation(rc.adjacentLocation(wallDirection.rotateLeft()));
                    if (!(temp != null && temp.team == rc.getTeam() && temp.getType() == RobotType.DESIGN_SCHOOL)) {
                        rc.digDirt(wallDirection.rotateLeft());
                    }
                } else if (rc.canDigDirt(wallDirection.rotateRight())) {
                    RobotInfo temp = rc.senseRobotAtLocation(rc.adjacentLocation(wallDirection.rotateRight()));
                    if (!(temp != null && temp.team == rc.getTeam() && temp.getType() == RobotType.DESIGN_SCHOOL)) {
                        rc.digDirt(wallDirection.rotateRight());
                    }
                }
            }
            return;
        }

        // if wall is full, become offense
        // TODO: finish this!
        if (rc.canSenseLocation(localHQ)) {
            if (!tryBuildWall()) {
                System.out.println("WALL HAS BEEN FULLY BUILT!");
                return;
            }
        } else {
            System.out.println("Trying to move to HQ!");
            pathfinding.travelTo(localHQ);
        }
    }

    static void runLatticeLandscaper() {
        switch (latticeState) {
            case LATTICE_WALL:      buildLatticeWall();         break;
            case NOT_ON_LATTICE:    getOnLattice();             break;
            case ON_LATTICE:        lattice();                  break;
        }
    }

    // build the lattice wall around HQ
    static void buildLatticeWall() {
        // check re-broadcast if the lattice wall has already been built
        if (!latticeWallBuiltBroadcast()) {
            latticeState = LatticeState.NOT_ON_LATTICE;
            return;
        }

        // if you don't know where the wall is, define it
        if (latticeWallCoordinates.size() == 0) {
            initializeLatticeWall();
        }

        // check if the lattice wall has been built from what's visible to this landscaper
        if (!latticeWallBuilt) {
            latticeWallBuilt = isLatticeWallBuilt();
        }

        // if lattice wall not built:
        // 1. get a location from the wall with its index (maybe create a new class for this)
        // 2. if you're on it, then build it up
        //      - if you're finished building it up, find the closest un-built not obstructed wall location and move towards it
        //      - if there is none or its obstructed, then you're now on the lattice
        // 3. if you can't get on it, then get on the lattice, build yourself up, and start going
        //      - to get on the lattice, grab a lattice location and elevate yourself
        //      - make sure you're connected to the lattice—if you're not certain, then connect yourself
        // 4. if you're stuck inside the lattice wall, then you're gonna need to get picked up by drones
        //      - there should be a few designated drones who circle over HQ in the bounds of the lattice walls and pick up anybody not on the lattice and move them onto it

        // if lattice wall still not built, move on top of a lattice wall coordinate and build up
        if (!latticeWallBuilt) {
            MapLocation next_coordinate = nextLatticeWallCoordinate();
            // if no next coordinate, wall is finished building
            if (next_coordinate == null) {
                latticeWallBuilt = true;
                latticeState = LatticeState.NOT_ON_LATTICE;
                // not sure if I'm supposed to return here
                return;
            }

            // move towards the wall location
            if (!pathfinding.travelTo(next_coordinate)) {
                pathfinding.reset();
                // if we're on top of the location, start depositing dirt here
                // TODO: store the current wall location so that the current landscaper doesn't do the above work to find a new location
                if (rc.getLocation().x == next_coordinate.x && rc.getlocation().y == next_coordinate.y) {
                    if (rc.canDepositDirt(Direction.CENTER)) {
                        rc.depositDirt(Direction.CENTER);
                    }
                    Direction digDirtFrom = getNextDig();
                    if (digDirtFrom != null && rc.canDigDirt(digDirtFrom)) {
                        rc.digDirt(digDirtFrom);
                    }
                }
                // TODO: if you're not able to reach the wall location and you're 'inside the wall', then wait for a drone to pick you up
            }
        }
    }

    // if not on the lattice, get on a square that will be a part of the lattice and attach yourself to it
    static void getOnLattice() {
        // TODO: not sure if this function is even necessary but if so implement it
    }

    // build the lattice out
    static void lattice() {
        // TODO: decide the next location in getNextLattice() based on where enemyHQ is
        Direction nextLattice = getNextLattice();
        // if no nextLattice, then the next level of lattice blocks to build have been finished and progress can be made
        if (nextLattice == null) {
            // NOTE: latticeLocation is a location you're building the general lattice towards. For example, enemyHQ.
            boolean result = pathfinding.travelTo(latticeLocation);
            if (!result) {
                // if can't move towards it, you're stuck in some weird corner.
            }
        } else {
            // add to the lattice
            depositDirt(nextLattice);
        }
    }

    static void addToLattice() {
        // depositDirt if carrying it
        // fetch next digDirt location
        // fetch next addToDirt location
        Direction nextLattice = getNextLattice();
        if (rc.canDepositDirt(nextLattice)) {
            rc.depositDirt(nextLattice);
        }

        Direction digFrom = getNextDig();
        if (rc.canDigDirt(digFrom)) {
            rc.digDirt(digFrom);
        }
    }

    // get one nearby location that you can dig from to build lattice
    static Direction getNextDig() {
        boolean xIsOdd = localHQ.x - rc.getLocation().x % 2 == 1;
        boolean yIsOdd = localHQ.y - rc.getLocation().y % 2 == 1;

        List<Direction> digOptions = new ArrayList<>();
        if (xIsOdd && yIsOdd) {
            digOptions.add(Direction.NORTHEAST);
            digOptions.add(Direction.NORTHWEST);
            digOptions.add(Direction.SOUTHEAST);
            digOptions.add(Direction.SOUTHWEST);
        } else if (xIsOdd) {
            digOptions.add(Direction.EAST);
            digOptions.add(Direction.WEST);
        } else {
            digOptions.add(Direction.NORTH);
            digOptions.add(Direction.SOUTH);
        }

        for (Direction dir: digOptions) {
            if (rc.canDigDirt(dir)) {
                return dir;
            }
        }

        return null;
    }

    // get next direction that is part of the lattice
    static Direction getNextLattice() {
        List<Direction> nextQueue = new ArrayList<>();

        int xDifference;
        int yDifference;

        if (enemyHQ == null) {
            xDifference = rc.getLocation().x - localHQ.x;
            yDifference = rc.getLocation().y - localHQ.y;
        } else {
            xDifference = enemyHQ.x - rc.getLocation().x;
            yDifference = enemyHQ.y - rc.getLocation().y;
        }

        Direction startDirection = null;
        if (xDifference >= 0 && yDifference >= 0) {
            startDirection = Direction.NORTHEAST;
        } else if (xDifference < 0 && yDifference >= 0) {
            startDirection = Direction.NORTHWEST;
        } else if (xDifference >= 0 && yDifference < 0) {
            startDirection = Direction.SOUTHEAST;
        } else {
            startDirection = Direction.SOUTHWEST;
        }

        nextQueue.add(startDirection);
        nextQueue.add(startDirection.rotateLeft());
        nextQueue.add(startDirection.rotateRight());
        nextQueue.add(startDirection.rotateLeft().rotateLeft());
        nextQueue.add(startDirection.rotateRight().rotateRight());

        for (Direction dir: nextQueue) {
            if (validLatticeLoc(new MapLocation(rc.getLocation().x + dir.dx, rc.getLocation().y + dir.dy))) {
                return dir;
            }
        }

        return null;
    }

    // checks if the lattice could exist at this location
    static boolean validLatticeLoc(MapLocation loc) {
        if (!rc.canSenseLocation(loc)) {
            return false;
        }

        if (rc.onTheMap(loc) && onLattice(loc)
            && rc.senseElevation(loc) - rc.senseElevation(rc.getLocation()) <= 20 
            && rc.senseElevation(loc) - rc.senseElevation(rc.getLocation()) >= -20) {
            return true;
        }

        return false;
    }

    // coordinate check for the lattice: do the coordinates match up?
    static boolean onLattice(MapLocation loc) {
        return localHQ.x - loc.x % 2 == 1 || localHQ.y - loc.y % 2 == 1;
    }

    // get the next coordinate on the lattice wall that needs to be build
    // TODO: if a landscaper gets stuck or can't find the next one, it goes to regular lattice mode and starts building out
    // TODO: store your map location
    static MapLocation nextLatticeWallCoordinate() {
        if (!latticeWallBuiltBroadcast()) {
            for (MapLocation coordinate: latticeWallCoordinates) {
                if (rc.canSenseLocation(coordinate) && rc.senseElevation(coordinate) < LATTICE_ELEVATION) {
                    // move on top of that location
                    // build it up to 8
                    // grab the index of that location so you can try the other locations
                    return coordinate;
                }
                // if can see it and not at specified elevation: go there and build coordinate
                // in the future, if you already have a coordinate, check the next and previous
                //      if both built, then you're free to start terraforming outwards
            }
        }
        return null;
    }


    // manually initialize all the lattice wall coordinates
    // TODO: probably want to have HQ do this as well
    static void initializeLatticeWall() {
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 0, localHQ.y + 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 1, localHQ.y + 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 2, localHQ.y + 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 3, localHQ.y + 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 3, localHQ.y + 2));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 3, localHQ.y + 1));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 3, localHQ.y + 0));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 3, localHQ.y - 1));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 3, localHQ.y - 2));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 3, localHQ.y - 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 2, localHQ.y - 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 1, localHQ.y - 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x + 0, localHQ.y - 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 1, localHQ.y - 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 2, localHQ.y - 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 3, localHQ.y - 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 3, localHQ.y - 2));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 3, localHQ.y - 1));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 3, localHQ.y + 0));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 3, localHQ.y + 1));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 3, localHQ.y + 2));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 3, localHQ.y + 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 2, localHQ.y + 3));
        tryAddLatticeWallCoordinate(new MapLocation(localHQ.x - 1, localHQ.y + 3));
    }

    static void tryAddLatticeWallCoordinate(MapLocation loc) {
        if (rc.onTheMap(loc)) {
            latticeWallCoordinates.add(loc);
        }
    }

    // I want HQ to broadcast until wall is finished
    // corner case bug to consider: you haven't labeled yourself as the wall yet but you're standing where the wall would be

    static boolean tryBuildWall() throws GameActionException {
        Direction empty = null;
        // Rebuild wall queue with direction opposite design school first
        if (wallQueue == null) {
            wallQueue = new ArrayList<>();
            MapLocation DS = rc.getLocation();
            RobotInfo[] nearby = rc.senseNearbyRobots(2, rc.getTeam());
            for (RobotInfo ri : nearby) {
                if (ri.getType() == RobotType.DESIGN_SCHOOL) {
                    DS = ri.location;
                }
            }

            Direction temp;
            Direction origin = DS.directionTo(localHQ);

            temp = origin;
            if (rc.onTheMap(localHQ.add(temp))
                    && !ut.dirtDifferenceAboveX(localHQ, localHQ.add(temp))
                    && !ut.locationIsWallDeadzone(temp)) {
                wallQueue.add(temp);
            }

            for (int i = 1; i <= 4; i++) {
                temp = Utility.rotateXTimesLeft(origin, i);
                if (rc.onTheMap(localHQ.add(temp))
                        && !ut.dirtDifferenceAboveX(localHQ, localHQ.add(temp))
                        && !ut.locationIsWallDeadzone(temp)) {
                    wallQueue.add(temp);
                }

                if (i != 4) {
                    temp = Utility.rotateXTimesRight(origin, i);
                    if (rc.onTheMap(localHQ.add(temp))
                            && !ut.dirtDifferenceAboveX(localHQ, localHQ.add(temp))
                            && !ut.locationIsWallDeadzone(temp)) {
                        wallQueue.add(temp);
                    }
                }
            }
        }

        for (Direction dir: wallQueue) {
            MapLocation temp = new MapLocation(localHQ.x + dir.dx, localHQ.y + dir.dy);
            if (!rc.canSenseLocation(temp) || !rc.isLocationOccupied(temp) || rc.getLocation().equals(temp)) {
                empty = dir;
                break;
            }
        }

        if (empty == null) {
            wallBuilt = true;
            return false;
        }

        MapLocation chosenLocation = new MapLocation(localHQ.x + empty.dx, localHQ.y + empty.dy);

        if (rc.getLocation().equals(chosenLocation)) {
            wallDirection = empty;
            return true;
        }

        try {
            pathfinding.travelTo(chosenLocation);
        } catch (GameActionException e) {}

        if (rc.getLocation().equals(chosenLocation)) {
            wallDirection = empty;
            return true;
        }
        return true;
    }

}
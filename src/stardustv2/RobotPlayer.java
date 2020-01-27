package stardustv2;
import battlecode.common.*;

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
    EXTRA_DEFENSE
}

enum DroneState {
    PASSIVE,
    TRANSPORT,
    OFFENSE
}

public strictfp class RobotPlayer {
    static RobotController rc;

    static RobotType[] spawnedByMiner = {
        RobotType.REFINERY,
        RobotType.VAPORATOR,
        RobotType.DESIGN_SCHOOL,
        RobotType.FULFILLMENT_CENTER,
        RobotType.NET_GUN
    };

    static int mapHeight;
    static int mapWidth;
    static int localHQID;
    static MapLocation localHQ;
    static MapLocation enemyHQ = new MapLocation(99,99); // assigned value is temporary
    static MapLocation[] possibleEnemyLocations;
    static int currentlyScoutingEnemyAt;

    // HQ VARIABLES
    static int HQHealth = 50;
    static int minerCount = 0;
    static boolean hasBuiltDefensiveDesignSchool = false;
    static boolean hasBuildOffensiveDesignSchool = false;
    static Direction[] bestMinerSpawns;

    // PATHFINDING VARIABLES
    static Pathfinding pathfinding;
    static Deque<MapLocation> travelQueue;
    static int nextMiningSector;

    // BLOCKCHAIN VARIABLES
    static Communication communication;

    // UTILITY VARIABLES
    static Utility ut;

    // MINER VARIABLES
    static MinerState minerState;
    static MiningState miningState;
    static Set<Integer> soupSectors;
    static MapLocation lastSoupDeposit;
    static ArrayList<MapLocation> refineries;
    static int refineryAccessAttempts = 0;
    static int unfruitfulRounds = 0;
    static int HQLockout = 0;

    // MINER (BUILDER) VARIABLES
    static boolean minerHasBuiltDefensiveDS         = false;
    static boolean minerHasBuiltAuxiliaryRefinery   = false;
    static boolean minerHasBuiltDroneCenter         = false;

    // DESIGN SCHOOL VARIABLES
    static int landscapersBuilt = 0;
    static int offensiveLandscapersBuilt = 0;
    static boolean refineriesExist = false;

    // LANDSCAPER VARIABLES
    static boolean wallBuilt = false;
    static int roundsSinceWallBuilt = 0;
    static Direction wallDirection;
    static ArrayList<Direction> wallQueue;
    static ArrayList<MapLocation> extraDefenseQueue;
    static boolean extraDefensePlaced = false;
    static Direction extraDefenseDigHere;
    static Direction extraDefenseDepositA;
    static Direction extraDefenseDepositB;
    static LandscaperState landscaperState = LandscaperState.UNASSIGNED;

    // DRONE VARIABLES
    static int dronesBuilt = 0;
    static DroneUtil droneUtil;

    // DECISION CONSTANTS
    final static int soupNeededToBuildDesignSchool  = 365;
    final static int soupNeededToBuildDroneCenter   = 380;
    final static int landscaperRound                = 40;

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

        // Calculate possible enemy HQ location
        currentlyScoutingEnemyAt = 0;
        possibleEnemyLocations = new MapLocation[3];
        possibleEnemyLocations[0] = new MapLocation(mapWidth - 1 - localHQ.x, localHQ.y);
        possibleEnemyLocations[1] = new MapLocation(mapWidth - 1 - localHQ.x, mapHeight - 1 -localHQ.y);
        possibleEnemyLocations[2] = new MapLocation(localHQ.x, mapHeight - 1 - localHQ.y);

        // Initialize HQ variables
        if (rc.getType() == RobotType.HQ) {
            bestMinerSpawns = Utility.getBestMinerSpawns(rc.getLocation(), mapHeight, mapWidth);
        }

        // Initialize miner variables
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
//            } else if (rc.getRoundNum() > landscaperRound && rc.getTeamSoup() > 140) {
            } else if (rc.getRoundNum() > landscaperRound) {
                // TODO: BUILD ORDER ERROR HERE: if offensive design school is built before this robot, it will never build defensive design school as team soup will be less than 140
                minerState = MinerState.BUILDING;
            } else {
                minerState = MinerState.UNASSIGNED;
            }
        }

        if (rc.getType() == RobotType.DELIVERY_DRONE) {
            droneUtil = new DroneUtil(rc, localHQ);
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
        for (Direction dir : bestMinerSpawns) {
            if (minerCount < 4 && ut.tryBuild(RobotType.MINER, dir)) {
                minerCount++;
                break;
            }
        }

        // Build auxiliary miner to build design school and scout enemy HQ
        if (!hasBuiltDefensiveDesignSchool && rc.getTeamSoup() >= soupNeededToBuildDesignSchool) {
            for (Direction dir : bestMinerSpawns) {
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
                // If HQ is no longer the only refinery, stop miners from going anywhere near it
                // TODO: check this out
//                if (HQLockout == 0 && !refineries.contains(localHQ)) {
//                    HQLockout = 8;
//                }
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

        if (!refineriesExist) {
            Transaction[] currentBlock = rc.getBlock(rc.getRoundNum() - 1);
            ArrayList<MapLocation> newRefineries = communication.checkNewRefineries(currentBlock);
            if (newRefineries.size() > 0) {
                refineriesExist = true;
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
        if (refineriesExist) {
            for (Direction dir : wallQueue) {
                if (rc.getRoundNum() > landscaperRound && landscapersBuilt < 16) {
                    if (ut.tryBuild(RobotType.LANDSCAPER, dir)) {
                        landscapersBuilt++;
                    }
                }
            }
        }
    }

    static void runFulfillmentCenter() throws GameActionException {
        // Generate drone in random direction if able
        for (Direction dir : Utility.getDirections()) {
            if (ut.tryBuild(RobotType.DELIVERY_DRONE, dir) && dronesBuilt < 4) {
                dronesBuilt++;
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
            case EXTRA_DEFENSE: runExtraDefenseLandscaper(); break;
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
            droneUtil.travelTo(localHQ, "linear", false, false);
        } else {
            for (Direction dir : Utility.getDirections()) {
                if (rc.senseFlooding(rc.adjacentLocation(dir))) {
                    if (rc.canDropUnit(dir)) {
                        rc.dropUnit(dir);
                    }
                }
            }
            droneUtil.travelTo(localHQ, "linear", false, false);
        }
//        ut.tryMove(ut.randomDirection());
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // MINER FUNCTIONS
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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
            if (!pathfinding.travelTo(travelQueue.peekFirst(), HQLockout)) {
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
                        pathfinding.travelTo(travelQueue.peekFirst(), HQLockout);
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
                        if (!pathfinding.travelTo(travelQueue.peekFirst(), HQLockout)) {
                            travelQueue.clear();
                            pathfinding.reset();
                            System.out.println("Can't reach refinery...");
                            // TODO: try reaching next closest refinery, if available
                            minerState = MinerState.RANDOM;
                        }
                    }
                }
            }
            System.out.println("Done with refining subroutine!");
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
            if (!rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 5)
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
            if (!rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 20)) {
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
            pathfinding.travelTo(possibleEnemyLocations[currentlyScoutingEnemyAt], HQLockout);
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
                pathfinding.travelTo(enemyHQ, HQLockout);
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // LANDSCAPER FUNCTIONS
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    static void assignLandscaper() throws GameActionException {
        if (rc.getCooldownTurns() < 2) {
            System.out.println("assignLandscaper: enemy HQ is " + enemyHQ);
            if (!Pathfinding.locIsNull(enemyHQ) && rc.getLocation().isWithinDistanceSquared(enemyHQ, 18)) {
                landscaperState = LandscaperState.OFFENSE;
            } else {
                if (isWallBuilt() && rc.getLocation().distanceSquaredTo(localHQ) > 2) {
                    landscaperState = LandscaperState.EXTRA_DEFENSE;
                } else {
                    landscaperState = LandscaperState.DEFENSE;
                }
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
                    rc.depositDirt(enemyHQDir);
                }
                while (rc.canDigDirt(digHere)) {
                    rc.digDirt(digHere);
                }
            } else {
                System.out.println("TRAVELING TO ENEMY HQ AT: " + enemyHQ);
                pathfinding.travelTo(enemyHQ, 0);
            }
        }
    }

    static void runDefenseLandscaper() throws GameActionException {
        // if part of the wall, build the wall
        if (wallDirection != null) {
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

            RobotInfo otherBot;
            if (rc.isReady() && rc.getDirtCarrying() < 25) {
                if (wallDirection == Direction.NORTHWEST
                        || wallDirection == Direction.NORTHEAST
                        || wallDirection == Direction.SOUTHWEST
                        || wallDirection == Direction.SOUTHEAST) {
                    Direction cardinalWallDirectionL = Utility.rotateXTimesLeft(wallDirection, 2);
                    Direction cardinalWallDirectionR = Utility.rotateXTimesRight(wallDirection, 2);

                    if (rc.onTheMap(rc.adjacentLocation(cardinalWallDirectionL))) {
                        otherBot = rc.senseRobotAtLocation(new MapLocation(
                                rc.getLocation().x + cardinalWallDirectionL.dx,
                                rc.getLocation().y + cardinalWallDirectionL.dy));
                        if (rc.canDigDirt(cardinalWallDirectionL) &&
                                (otherBot == null || otherBot.team == rc.getTeam() || otherBot.getType() == RobotType.LANDSCAPER)) {
                            rc.digDirt(cardinalWallDirectionL);
                        }
                    }

                    if (rc.onTheMap(rc.adjacentLocation(cardinalWallDirectionR))) {
                        otherBot = rc.senseRobotAtLocation(new MapLocation(
                                rc.getLocation().x + cardinalWallDirectionR.dx,
                                rc.getLocation().y + cardinalWallDirectionR.dy));
                        if (rc.canDigDirt(cardinalWallDirectionR) &&
                                (otherBot == null || otherBot.team == rc.getTeam() || otherBot.getType() == RobotType.LANDSCAPER)) {
                            rc.digDirt(cardinalWallDirectionR);
                        }
                    }
                } else {
                    otherBot = rc.senseRobotAtLocation(new MapLocation(rc.getLocation().x + wallDirection.dx, rc.getLocation().y + wallDirection.dy));
                    if (rc.canDigDirt(wallDirection) &&
                            (otherBot == null || otherBot.team == rc.getTeam() || otherBot.getType() == RobotType.LANDSCAPER)) {
                        rc.digDirt(wallDirection);
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
            pathfinding.travelTo(localHQ, 0);
        }
    }

    static void runExtraDefenseLandscaper() throws GameActionException {
        System.out.println("I'm an extra defense landscaper!");
        if (extraDefenseQueue == null) {
            extraDefenseQueue = new ArrayList<>();
            // get initial and ending points
            int initDX;
            int initDY;
            MapLocation DS = rc.getLocation();
            RobotInfo[] nearby = rc.senseNearbyRobots(2, rc.getTeam());
            for (RobotInfo ri : nearby) {
                if (ri.getType() == RobotType.DESIGN_SCHOOL) {
                    DS = ri.location;
                }
            }
            Direction toHQ = DS.directionTo(localHQ);
            if (toHQ.getDeltaX() < 0) {
                initDX = -2;
            } else {
                initDX = 2;
            }
            if (toHQ.getDeltaY() <= 0) {
                initDY = -2;
            } else {
                initDY = 2;
            }

            System.out.println("initDX is " + initDX);
            System.out.println("InitDY is " + initDY);

            // fill-forwards (closest to design school first), so no particular order
            for (int dx = initDX; dx != -1.5*initDX; dx+=(-0.5*initDX)) {
                for (int dy = initDY; dy != -1.5*initDY; dy+=(-0.5*initDY)) {
                    System.out.println("Creating defense queue: attempt is: dx=" + dx + " and dy=" + dy);
                    if ((Math.abs(dx) == 1 && Math.abs(dy) == 1)
                            || (Math.abs(dx) == 2 && Math.abs(dy) == 2)
                            || dx == 0 || dy == 0) {
                        continue;
                    }
                    MapLocation temp = localHQ.translate(dx, dy);
                    if (rc.onTheMap(temp)) {
                        extraDefenseQueue.add(temp);
                    }
                }
            }
        }

        System.out.println("Extra defense placed: " + extraDefensePlaced);
        System.out.println("ExtraDefenseQueue: " + extraDefenseQueue);

        if (!extraDefensePlaced && extraDefenseQueue != null) {
            for (MapLocation loc : extraDefenseQueue) {
                if (rc.getLocation().equals(loc)) {
                    extraDefensePlaced = true;
                    extraDefenseQueue.clear();
                    break;
                }
            }
            if (!extraDefensePlaced) {
                for (MapLocation spot : extraDefenseQueue) {
                    if (rc.canSenseLocation(spot)
                            && rc.isLocationOccupied(spot)) {
                        extraDefenseQueue.remove(spot);
                    } else {
                        break;
                    }
                }
                if (!extraDefenseQueue.isEmpty()) {
                    System.out.println("Extra defense: trying to travel to " + extraDefenseQueue.get(0));
                    pathfinding.travelTo(extraDefenseQueue.get(0), 0);
                    return;
                }
            }
        }

        if (extraDefensePlaced) {
            if (extraDefenseDigHere == null) {
                switch(localHQ.y - rc.getLocation().y) {
                    case -2:
                        extraDefenseDigHere = rc.getLocation().directionTo(localHQ.translate(0, 2));
                        break;
                    case -1:
                    case 1:
                        if (localHQ.x - rc.getLocation().x > 0) {
                            extraDefenseDigHere = rc.getLocation().directionTo(localHQ.translate(-2, 0));
                        } else {
                            extraDefenseDigHere = rc.getLocation().directionTo(localHQ.translate(2, 0));
                        }
                        break;
                    case 2:
                        extraDefenseDigHere = rc.getLocation().directionTo(localHQ.translate(0, -2));
                        break;
                }
                if (Math.abs(localHQ.y - rc.getLocation().y) == 2
                        && Math.abs(localHQ.x - rc.getLocation().x) == 2) {
                    extraDefenseDigHere = rc.getLocation().directionTo(localHQ).opposite();
                }
            }

            if (extraDefenseDepositA == null) {
                if (extraDefenseDigHere == Direction.SOUTHWEST
                        || extraDefenseDigHere == Direction.SOUTHEAST
                        || extraDefenseDigHere == Direction.NORTHWEST
                        || extraDefenseDigHere == Direction.NORTHEAST) {
                    extraDefenseDepositA = extraDefenseDigHere.opposite();
                } else {
                    if (localHQ.y - rc.getLocation().y == 2) {
                        extraDefenseDepositA = Direction.NORTH;
                        if (localHQ.x - rc.getLocation().x == 1) {
                            extraDefenseDepositB = extraDefenseDepositA.rotateRight();
                        } else {
                            extraDefenseDepositB = extraDefenseDepositA.rotateLeft();
                        }
                    } else if (localHQ.y - rc.getLocation().y == -2) {
                        extraDefenseDepositA = Direction.SOUTH;
                        if (localHQ.x - rc.getLocation().x == 1) {
                            extraDefenseDepositB = extraDefenseDepositA.rotateLeft();
                        } else {
                            extraDefenseDepositB = extraDefenseDepositA.rotateRight();
                        }
                    } else if (localHQ.x - rc.getLocation().x == 2) {
                        extraDefenseDepositA = Direction.EAST;
                        if (localHQ.y - rc.getLocation().y == 1) {
                            extraDefenseDepositB = extraDefenseDepositA.rotateLeft();
                        } else {
                            extraDefenseDepositB = extraDefenseDepositA.rotateRight();
                        }
                    } else {
                        extraDefenseDepositA = Direction.WEST;
                        if (localHQ.y - rc.getLocation().y == 1) {
                            extraDefenseDepositB = extraDefenseDepositA.rotateRight();
                        } else {
                            extraDefenseDepositB = extraDefenseDepositA.rotateLeft();
                        }
                    }
                }
            }

            System.out.println("time to get to work!");

            // check if self is going to flood, if so, deposit on self
            if (rc.canDepositDirt(Direction.CENTER)) {
                if (rc.senseElevation(rc.getLocation()) <= 10) {
                    rc.depositDirt(Direction.CENTER);
                } else if (rc.senseElevation(rc.getLocation()) <= 25 && rc.getRoundNum() > 1700) {
                    rc.depositDirt(Direction.CENTER);
                } else if (rc.senseElevation(rc.getLocation()) <= 50 && rc.getRoundNum() > 2150) {
                    rc.depositDirt(Direction.CENTER);
                }
            }

            // try locations A and B
            if (extraDefenseDepositB == null) {
                if (rc.canDepositDirt(extraDefenseDepositA)) {
                    rc.depositDirt(extraDefenseDepositA);
                }
            } else {
                if (rc.senseElevation(rc.adjacentLocation(extraDefenseDepositB)) + 10
                        < rc.senseElevation(rc.adjacentLocation(extraDefenseDepositA))
                        && rc.canDepositDirt(extraDefenseDepositB)) {
                    rc.depositDirt(extraDefenseDepositB);
                } else if (rc.canDepositDirt(extraDefenseDepositA)) {
                    rc.depositDirt(extraDefenseDepositA);
                }
            }

            // Dig dirt
            if (rc.canDigDirt(extraDefenseDigHere)) {
                rc.digDirt(extraDefenseDigHere);
            }
        }
    }

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
            // TODO: fix the following line because if a cow comes near HQ, landscaper can't sense location
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
            pathfinding.travelTo(chosenLocation, 0);
        } catch (GameActionException e) {}

        if (rc.getLocation().equals(chosenLocation)) {
            wallDirection = empty;
            return true;
        }
        return true;
    }

}
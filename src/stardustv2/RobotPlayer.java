package stardustv2;
import battlecode.common.*;

import java.nio.file.Path;
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
    static Direction wallDirection;
    static ArrayList<Direction> wallQueue;
    static LandscaperState landscaperState = LandscaperState.UNASSIGNED;

    // Some constants
    final static int soupNeededToBuildDesignSchool = 215;
    final static int soupNeededToBuildDroneCenter = 230;
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
        Transaction genesisBlock = communication.getGenesisBlock();
        if (genesisBlock != null) {
            localHQ = communication.getHQCoordinates(genesisBlock);
            localHQID = communication.getHQID(genesisBlock);
        } else {
            localHQ = rc.getLocation();
            localHQID = -1;
        }
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
        for (Direction dir : directions) {
            if (minerCount < 4 && tryBuild(RobotType.MINER, dir)) {
                minerCount++;
                break;
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
            for (Direction dir : directions) {
                if (rc.getRoundNum() > landscaperRound && offensiveLandscapersBuilt < 2) {
                    if (tryBuild(RobotType.LANDSCAPER, dir)) {
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
            wallQueue.add(rotateXTimesLeft(wallQueue.get(0), 1));
            wallQueue.add(rotateXTimesRight(wallQueue.get(0), 1));
            wallQueue.add(rotateXTimesLeft(wallQueue.get(0), 2));
            wallQueue.add(rotateXTimesRight(wallQueue.get(0), 2));
            wallQueue.add(rotateXTimesLeft(wallQueue.get(0), 3));
            wallQueue.add(rotateXTimesRight(wallQueue.get(0), 3));
            wallQueue.add(rotateXTimesLeft(wallQueue.get(0), 4));
        }

        // Generate landscaper in random direction if able
        for (Direction dir : wallQueue) {
            if (rc.getRoundNum() > landscaperRound && landscapersBuilt < 9) {
                if (tryBuild(RobotType.LANDSCAPER, dir)) {
                    landscapersBuilt++;
                }
            }
        }
    }

    static void runFulfillmentCenter() throws GameActionException {
        // Generate drone in random direction if able
        for (Direction dir : directions) {
            if (tryBuild(RobotType.DELIVERY_DRONE, dir)) {
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
            for (Direction dir : directions) {
                if (rc.senseFlooding(rc.adjacentLocation(dir))) {
                    if (rc.canDropUnit(dir)) {
                        rc.dropUnit(dir);
                        System.out.println("I yeeted an enemy robot!");
                    }
                }
            }
        }
        tryMove(randomDirection());
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

    static boolean dirtDifferenceAbove3(MapLocation a, MapLocation b) throws GameActionException {
        if (!rc.canSenseLocation(a) || !rc.canSenseLocation(b)) return true; // fallback clause
        int a_elev = rc.senseElevation(a);
        int b_elev = rc.senseElevation(b);
        return Math.abs(a_elev - b_elev) > 6; // TODO: fix this temporary fix (used to be 3)
    }

    static Direction rotateXTimesLeft(Direction dir, int times) {
        Direction result = dir;
        for (int i = 0; i < times; i++) {
            result = result.rotateLeft();
        }
        return result;
    }

    static Direction rotateXTimesRight(Direction dir, int times) {
        Direction result = dir;
        for (int i = 0; i < times; i++) {
            result = result.rotateRight();
        }
        return result;
    }

    static boolean locationIsWallDeadzone(Direction dir) {
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
                    System.out.println("LOCATION IS WALL DEADZONE! - DIRECTION " + dir);
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
        System.out.println("DIRECTION " + dir + " NOT DEADZONE!");
        return false;
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
    static void goBuildDefensiveDS() throws GameActionException {
        for (Direction dir : directions) {
            if (!rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 2)
                    && rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 50)) {
                if (tryBuild(RobotType.DESIGN_SCHOOL, dir)) {
                    minerHasBuiltDefensiveDS = true;
                    break;
                }
            }
        }
        tryMove(randomDirection());
    }

    static void goBuildAuxiliaryRefinery() throws GameActionException {
        for (Direction dir : directions) {
            if (!rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 25)) {
                if (tryBuild(RobotType.REFINERY, dir)) {
                    minerHasBuiltAuxiliaryRefinery = true;
                    communication.announceNewRefinery(rc.adjacentLocation(dir));
                    break;
                }
            }
        }
        tryMove(randomDirection());
        staticDetectSoup();
    }

    static void goBuildDroneCenter() throws GameActionException {
        if (rc.getTeamSoup() >= soupNeededToBuildDroneCenter) {
            for (Direction dir : directions) {
                if (!rc.adjacentLocation(dir).isWithinDistanceSquared(localHQ, 25)) {
                    if (tryBuild(RobotType.FULFILLMENT_CENTER, dir)) {
                        minerHasBuiltDroneCenter = true;
                        break;
                    }
                }
            }
        }
        tryMove(randomDirection());
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
                    && !dirtDifferenceAbove3(localHQ, localHQ.add(temp))
                    && !locationIsWallDeadzone(temp)) {
                wallQueue.add(temp);
            }

            for (int i = 1; i <= 4; i++) {
                temp = rotateXTimesLeft(origin, i);
                if (rc.onTheMap(localHQ.add(temp))
                        && !dirtDifferenceAbove3(localHQ, localHQ.add(temp))
                        && !locationIsWallDeadzone(temp)) {
                    wallQueue.add(temp);
                }

                if (i != 4) {
                    temp = rotateXTimesRight(origin, i);
                    if (rc.onTheMap(localHQ.add(temp))
                            && !dirtDifferenceAbove3(localHQ, localHQ.add(temp))
                            && !locationIsWallDeadzone(temp)) {
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
                Direction digHere = enemyHQDir.opposite();
                while (rc.canDepositDirt(enemyHQDir)) {
//                     TODO: REMOVE THIS!!! TEMPORARY PLACEHOLDER TO TEST DEFENSE IN DEVELOPMENT!
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

                RobotInfo otherBot = rc.senseRobotAtLocation(new MapLocation(rc.getLocation().x + wallDirection.dx, rc.getLocation().y + wallDirection.dy));

                if (!wallBuilt && isWallBuilt()) {
                    wallBuilt = true;
                    System.out.println(">>>>>>>>>>>> WALL IS FULLY BUILT!!!");
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
                    leftSideLoc = rc.adjacentLocation(rotateXTimesLeft(oppositeHQ, 2));
                    rightSideLoc = rc.adjacentLocation(rotateXTimesRight(oppositeHQ, 2));
                } else {
                    leftSideLoc = rc.adjacentLocation(rotateXTimesLeft(oppositeHQ, 3));
                    rightSideLoc = rc.adjacentLocation(rotateXTimesRight(oppositeHQ, 3));
                }

                if (wallBuilt
                        || (rc.isLocationOccupied(leftSideLoc) && rc.senseRobotAtLocation(leftSideLoc).getType() == RobotType.LANDSCAPER)) {
                    alternateDeposit.add(leftSideLoc);
                }
                if (wallBuilt
                        || (rc.isLocationOccupied(rightSideLoc) && rc.senseRobotAtLocation(rightSideLoc).getType() == RobotType.LANDSCAPER)) {
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

                if (rc.isReady() && rc.getDirtCarrying() < 25) {
                    if (rc.canDigDirt(wallDirection)
                            && !(otherBot != null && otherBot.team == rc.getTeam() && otherBot.getType() != RobotType.DESIGN_SCHOOL)) {
                        rc.digDirt(wallDirection);
                    } else if (rc.canDigDirt(wallDirection.rotateLeft())) {
                        System.out.println("CAN DIG DIRT ROTATE LEFT");
                        System.out.println("CHECKING ROBOT OCCUPATION (ROTATE LEFT) AT " + rc.adjacentLocation(wallDirection.rotateLeft()));
                        RobotInfo temp = rc.senseRobotAtLocation(rc.adjacentLocation(wallDirection.rotateLeft()));
                        if (!(otherBot != null && otherBot.team == rc.getTeam() && otherBot.getType() != RobotType.DESIGN_SCHOOL)) {
                            rc.digDirt(wallDirection.rotateLeft());
                        }
                    } else if (rc.canDigDirt(wallDirection.rotateRight())) {
                        System.out.println("CAN DIG DIRT ROTATE RIGHT");
                        System.out.println("CHECKING ROBOT OCCUPATION (ROTATE RIGHT) AT " + rc.adjacentLocation(wallDirection.rotateRight()));
                        RobotInfo temp = rc.senseRobotAtLocation(rc.adjacentLocation(wallDirection.rotateRight()));
                        if (!(otherBot != null && otherBot.team == rc.getTeam() && otherBot.getType() != RobotType.DESIGN_SCHOOL)) {
                            rc.digDirt(wallDirection.rotateRight());
                        }
                    }
                }
                return;
            }
        } catch (GameActionException e) {}

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
                    && !dirtDifferenceAbove3(localHQ, localHQ.add(temp))
                    && !locationIsWallDeadzone(temp)) {
                wallQueue.add(temp);
            }

            for (int i = 1; i <= 4; i++) {
                temp = rotateXTimesLeft(origin, i);
                if (rc.onTheMap(localHQ.add(temp))
                        && !dirtDifferenceAbove3(localHQ, localHQ.add(temp))
                        && !locationIsWallDeadzone(temp)) {
                    wallQueue.add(temp);
                }

                if (i != 4) {
                    temp = rotateXTimesRight(origin, i);
                    if (rc.onTheMap(localHQ.add(temp))
                            && !dirtDifferenceAbove3(localHQ, localHQ.add(temp))
                            && !locationIsWallDeadzone(temp)) {
                        wallQueue.add(temp);
                    }
                }
            }
        }

        for (Direction dir: wallQueue) {
            System.out.println("TRYING WALLQUEUE DIRECTION: " + dir + " AT COORDINATES " + new MapLocation(localHQ.x + dir.dx, localHQ.y + dir.dy));
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

        System.out.println("CHOSENLOCATION IS " + chosenLocation);
        if (rc.getLocation().equals(chosenLocation)) {
            System.out.println("We have been set into the wall");
            wallDirection = empty;
            return true;
        }

        try {
            pathfinding.travelTo(chosenLocation);
        } catch (GameActionException e) {}

        if (rc.getLocation().equals(chosenLocation)) {
            System.out.println("We have been set into the wall");
            wallDirection = empty;
            return true;
        }

        return true;
    }

}
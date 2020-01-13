package stardustv1;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;

import battlecode.common.*;

enum MinerState {UNASSIGNED, SCOUTING, MINING, BUILDING}
enum MiningState {ENROUTE, IN_PROGRESS, REFINING}

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
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;
    
    // Miner specific variables
    static int minerCounter;
    static int scoutTurns;
    static boolean minerScouting; // not needed after introducing enums
    static MinerState minerState = MinerState.UNASSIGNED;
    static MiningState miningState = MiningState.ENROUTE;
    static ArrayList<MapLocation> refineries;
    static ArrayList<MapLocation> soupLocations;
    static MapLocation lastSoupDeposit = new MapLocation(-1, -1);

    static int mapHeight;
    static int mapWidth;
    static int HQHealth;
    static int HQElevation;
    static int rebroadcastMsg;
    
    static MapLocation localHQ;
    static MapLocation enemyHQ;
    static Deque<MapLocation> travelQueue;
    static Deque<MapLocation> previousLocations;
    
    static ArrayList<MapLocation> waterBodies;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        turnCount = 0;

        scoutTurns = 0;
        minerCounter = 0;
        minerScouting = true;
        refineries = new ArrayList<>();
        soupLocations = new ArrayList<>();

        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();
        
        travelQueue = new LinkedList<>();
        previousLocations = new LinkedList<>();

        System.out.println("I'm a " + rc.getType() + " and I just got created!");

        if (rc.getType() == RobotType.MINER) {
            localHQ = getHQCoordinates();
            refineries.add(localHQ);
        }

        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You can add the missing ones or rewrite this into your own control structure.
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

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    static void runHQ() throws GameActionException {       
        // If first round, broadcast HQ coordinates
        if (rc.getRoundNum() == 1) {
            localHQ = rc.getLocation();
            refineries.add(localHQ);
            int localHQSerialized = locSerializer(localHQ);
            int[] gMsg = new int[]{101, localHQSerialized};
            txHandler(gMsg, 5);
        }

        // Update HQ health
        int prevHQHealth = HQHealth;
        HQHealth = 50 - (rc.senseElevation(localHQ) - HQElevation);
        if (HQHealth < prevHQHealth) {
            System.out.println("HQ IS UNDER ATTACK!");
        }

        // Builds up to 4 miners if able to
        switch (minerCounter) {
            case 0:
                if (tryBuild(RobotType.MINER, Direction.NORTH)) minerCounter++;
                break;
            case 1:
                if (tryBuild(RobotType.MINER, Direction.SOUTH)) minerCounter++;
                break;
            case 2:
                if (tryBuild(RobotType.MINER, Direction.WEST)) minerCounter++;
                break;
            case 3:
                if (tryBuild(RobotType.MINER, Direction.EAST)) minerCounter++;
                break;
            default:
                break;
        }            
    }

    static void runMiner() throws GameActionException {
        if (rc.isReady()) {
            minerDoRandomScout();
        }
        System.out.println(">>>>>>> BYTECODES USED BY MINER: " + Clock.getBytecodeNum());
    }

    static void runRefinery() throws GameActionException {

    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
        // Generate landscaper in random direction if able
        for (Direction dir : directions) {
            tryBuild(RobotType.LANDSCAPER, dir);
        }
    }

    static void runFulfillmentCenter() throws GameActionException {
        // Generate drone in random direction if able
        for (Direction dir : directions) {
            tryBuild(RobotType.DELIVERY_DRONE, dir);
        }
    }

    static void runLandscaper() throws GameActionException {

    }

    static void runDeliveryDrone() throws GameActionException {
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
            // No close robots, so search for robots within sight radius
            tryMove(randomDirection());
        }
    }

    static void runNetGun() throws GameActionException {

    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random RobotType spawned by miners.
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }

    static boolean tryMove() throws GameActionException {
        for (Direction dir : directions)
            if (tryMove(dir))
                return true;
        return false;
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to build a given robot in a given direction.
     *
     * @param type The type of the robot to build
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to refine soup in a given direction.
     *
     * @param dir The intended direction of refining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }

    /**
     * Transforms MapLocation object into an integer XXYY
     * 
     * @param loc the given MapLocation object
     * @return integer representing location
     * @throws GameActionException
     */
    static int locSerializer(MapLocation loc) throws GameActionException {
        int result = loc.y;
        result += loc.x * 100;
        return result;
    }

    /**
     * Transforms integer XXYY into a MapLocation object
     * 
     * @param loc the given integer corresponding to a location
     * @return MapLocation object
     * @throws GameActionException
     */
    static MapLocation locDeserializer(int loc) throws GameActionException {
        return new MapLocation(loc / 100, loc % 100);
    }

    static boolean locIsNull(MapLocation loc) throws GameActionException {
        if (loc.x < 0 || loc.y < 0) return true;
        return false;
    }

    /**
     * Handles sending transactions to blockchain.
     * Allows for multiple retries with greater cost.
     * 
     * @param msg[] message integer array to send
     * @param cost the amount of soup to attempt sending with
     * @return true if message sent, false otherwise
     * @throws GameActionException
     */
    static boolean txHandler(int[] msg, int cost) throws GameActionException {
        if (cost < 1) return false;
        if (msg.length < 1 || msg.length > 7) return false;

        if (rc.canSubmitTransaction(msg, cost)) {
            rc.submitTransaction(msg, cost);
            System.out.println("TX SEND SUCCESS. COST: " + cost);
            return true;
        }

        System.out.println("TX SEND FAILURE. COST: " + cost);
        return false;
    }

    static void checkBlockchainSoup() throws GameActionException {
        Transaction[] lastBlock = rc.getBlock(rc.getRoundNum() - 1);

        for (Transaction t : lastBlock) {
            if (t.getMessage()[0] == 201) {
                MapLocation temp = locDeserializer(t.getMessage()[1]);
                boolean soupLocationAlreadyEntered = false;
                for (MapLocation sl : soupLocations) {
                    if (temp.isWithinDistanceSquared(sl, 25)) {
                        soupLocationAlreadyEntered = true;
                    }
                }
                if (!soupLocationAlreadyEntered) {
                    soupLocations.add(temp);
                }
            }
        }
    }

    /**
     * Moves the calling robot towards the given destination coordinates,
     * in single-tile increments. Turns to avoid obstacles if necessary.
     * 
     * @param dest the destination location to move towards
     * @return if robot was successfully moved or not
     * @throws GameActionException
     */
    static boolean moveToTarget(MapLocation dest) throws GameActionException {
        if (rc.getCooldownTurns() > 0) {
            System.out.println("moveToTarget error: robot on cooldown.");
            return false;
        }

        MapLocation currentLoc = rc.getLocation();
        if (currentLoc == dest) {
            System.out.println("moveToTarget error: already at dest.");
            return false;
        }

        Direction moveTowards = currentLoc.directionTo(dest);

        if (rc.canMove(moveTowards) && !rc.senseFlooding(rc.adjacentLocation(moveTowards))) {
            rc.move(moveTowards);
            return true;
        } else {
            // Build alternate direction queue
            Direction[] queue = new Direction[6];
            queue[0] = moveTowards.rotateLeft();
            queue[1] = moveTowards.rotateRight();
            queue[2] = queue[0].rotateLeft();
            queue[3] = queue[1].rotateRight();
            queue[4] = queue[2].rotateLeft();
            queue[5] = queue[3].rotateRight();

            for (Direction d : queue) {
                if (rc.canMove(d) && !rc.senseFlooding(rc.adjacentLocation(d))) {
                    if(!previousLocations.contains(rc.adjacentLocation(d))) {
                        // Make sure previousLocations doesn't get larger than 10
                        if (previousLocations.size() >= 10) {
                            previousLocations.removeFirst();
                        }
                        // Push back current location to previousLocations, then perform move
                        previousLocations.addLast(currentLoc);
                        rc.move(d);
                        return true;
                    }
                }
            }
        }
        
        if (previousLocations.size() > 0) {
            Direction backtrack = currentLoc.directionTo(previousLocations.peekLast());
            if (rc.canMove(backtrack) && !rc.senseFlooding(rc.adjacentLocation(backtrack))) {
                if (previousLocations.size() >= 10) {
                    previousLocations.removeFirst();
                    previousLocations.addLast(currentLoc);
                    rc.move(backtrack);
                    return true;
                }
            }
        }
        return false;
    }

    static MapLocation getHQCoordinates() throws GameActionException {
        Transaction[] genesisBlock = rc.getBlock(1);
        for (Transaction t : genesisBlock) {
            if (t.getMessage()[0] == 101) {
                return locDeserializer(t.getMessage()[1]);
            }
        }
        // Find a way to fix this - temporary fallback case if genesis message not found
        return new MapLocation(0, 0);
    }

    static boolean HQRebroadcast(int priority) throws GameActionException {
        // resubmits rebroadcastMsg to blockchain on every round which is a multiple of 10
        // priority number corresponds to how much cost should be allocated to rebroadcasting
        // returns false if rebroadcast failed

        return false;
    }

    static void minerDoRandomScout() throws GameActionException {
        MapLocation topLeft = new MapLocation(8, mapHeight - 8);
        MapLocation topRight = new MapLocation(mapWidth - 8, mapHeight - 8);
        MapLocation botLeft = new MapLocation(8, 8);
        MapLocation botRight = new MapLocation(mapWidth - 8, 8);

        if (travelQueue.isEmpty()) {
            if (rc.getLocation().y > localHQ.y) {
                travelQueue.addLast(topLeft);
            } else if (rc.getLocation().y < localHQ.y) {
                travelQueue.addLast(topRight);
            } else if (rc.getLocation().x < localHQ.x) {
                travelQueue.addLast(botLeft);
            } else {
                travelQueue.addLast(botRight);
            }
        }

        if (rc.getLocation().equals(travelQueue.peekFirst()) || scoutTurns > 50) {
            minerScouting = false;
            travelQueue.removeFirst();
        }
        
        if (minerScouting) {
            moveToTarget(travelQueue.peekFirst());
            scoutTurns++;
        } else {
            tryMove(randomDirection());
        }
        for (Direction dir : directions)
            tryMine(dir);
        if (minerDoBruteSoupSearch()) {
            System.out.println("FOUND SOME SOUP!");
        }
    }

    static boolean minerDoBruteSoupSearch() throws GameActionException {
        MapLocation cLoc = rc.getLocation();
        MapLocation soupLoc = new MapLocation(-1, -1);
        int maxSoup = 0;
        for (int i = cLoc.x - 5; i <= cLoc.x + 5; i++) {
            for (int j = cLoc.y - 5; j <= cLoc.y + 5; j++) {
                MapLocation temp = new MapLocation(i, j);
                if (rc.canSenseLocation(temp)) {
                    int soupAmt = rc.senseSoup(temp);
                    if (soupAmt > 0) {
                        rc.setIndicatorDot(temp, 0, 0, 0);
                    }
                    if (soupAmt > maxSoup) {
                        maxSoup = soupAmt;
                        soupLoc = temp;
                    }
                }
            }
        }

        if (!locIsNull(soupLoc)) {
            // CHECK WHETHER THIS SOUP DEPOSIT IS WITHIN 5 TILE RADIUS OF EXISTING ENTRIES IN soupLocations!
            // IF NOT, THEN ADD IT TO soupLocations AND BROADCAST ON BLOCKCHAIN
            boolean soupLocationAlreadyEntered = false;

            for (MapLocation sl : soupLocations) {
                if (soupLoc.isWithinDistanceSquared(sl, 25)) {
                    soupLocationAlreadyEntered = true;
                }
            }
            
            if (!soupLocationAlreadyEntered) {
                soupLocations.add(soupLoc);
                int slocSerial = locSerializer(soupLoc);
                int[] soupMsg = new int[]{201, slocSerial};
                txHandler(soupMsg, 2);
            }
            
            return true;
        }

        return false;
    }

    static MapLocation minerDoStaticSoupSearch() throws GameActionException {
        MapLocation cLoc = rc.getLocation();
        MapLocation soupLoc = new MapLocation(-1, -1);
        int maxSoup = 0;
        for (int i = cLoc.x - 5; i <= cLoc.x + 5; i++) {
            for (int j = cLoc.y - 5; j <= cLoc.y + 5; j++) {
                MapLocation temp = new MapLocation(i, j);
                if (rc.canSenseLocation(temp)) {
                    int soupAmt = rc.senseSoup(temp);
                    if (soupAmt > maxSoup) {
                        maxSoup = soupAmt;
                        soupLoc = temp;
                    }
                }
            }
        }
        return soupLoc;
    }

    static boolean minerDoMine() throws GameActionException {
        // Retrieves soup deposit location from rebroadcast
        // Scouts x tiles around soup deposit location, mines soup if found
        // If no soup found in radius, send broadcast indicating deposit might be empty, and move to another deposit
        // If HQ receives multiple deposit empty messages, it removes deposit from rebroadcast
        // Returns to nearestRefinery once soup storage is full
        // Returns False if no more soup deposits in rebroadcast

        switch (miningState) {
            case ENROUTE:
                if (travelQueue.isEmpty()) {
                    if (locIsNull(lastSoupDeposit)) {
                        if (soupLocations.size() > 0) {
                            lastSoupDeposit = soupLocations.get(0);
                            travelQueue.add(lastSoupDeposit);
                        }
                    } else {
                        travelQueue.add(lastSoupDeposit);
                    }
                }
                if (!travelQueue.isEmpty()) {
                    if (rc.getLocation().equals(travelQueue.peekFirst())) {
                        travelQueue.clear();
                        miningState = MiningState.IN_PROGRESS;
                    } else {
                        if (rc.isReady()) {
                            moveToTarget(travelQueue.peekFirst());
                        }
                    }
                }
                break;

            case IN_PROGRESS:
                if (!rc.isReady()) break;
                if (!tryMine(Direction.CENTER)) {
                    for (Direction dir : directions) {
                        if (tryMine(dir)) {
                            lastSoupDeposit = rc.adjacentLocation(dir);
                            miningState = MiningState.ENROUTE;
                            break;
                        }
                    }
                    if (miningState == MiningState.IN_PROGRESS) {
                        MapLocation nearestNeighbor = minerDoStaticSoupSearch();
                        if (!locIsNull(nearestNeighbor)) {
                            lastSoupDeposit = nearestNeighbor;
                            miningState = MiningState.ENROUTE;
                        } else {
                            if (soupLocations.size() > 1) {
                                MapLocation temp = soupLocations.get(0);
                                soupLocations.remove(0);
                                soupLocations.add(temp);
                                lastSoupDeposit = soupLocations.get(0);
                                travelQueue.clear();
                                if (rc.getSoupCarrying() > 0) {
                                    miningState = MiningState.REFINING;
                                } else {
                                    miningState = MiningState.ENROUTE;
                                }
                            }
                        }
                    }
                }

                if (rc.getSoupCarrying() == RobotType.MINER.soupLimit) {
                    travelQueue.clear();
                    miningState = MiningState.REFINING;
                }
                break;

            case REFINING:
                MapLocation cLoc = rc.getLocation();
                if (travelQueue.isEmpty()) {
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

                if (!travelQueue.isEmpty()) {
                    if (cLoc.isWithinDistanceSquared(travelQueue.peekFirst(), 2)) {
                        if (rc.canDepositSoup(cLoc.directionTo(travelQueue.peekFirst()))) {
                            rc.depositSoup(cLoc.directionTo(travelQueue.peekFirst()), rc.getSoupCarrying());
                            travelQueue.clear();
                            miningState = MiningState.ENROUTE;
                        }
                    } else {
                        if (rc.isReady()) {
                            moveToTarget(travelQueue.peekFirst());
                        }
                    }
                }
                break;
        
            default:
                break;
        }

        // ENROUTE TO SOUP LOCATION

            // IF NO PREVIOUS DEPOSIT, HEAD TO FIRST ENTRY IN DEPOSIT ARRAY

        // AT DEPOSIT LOCATION

            // IF PREVIOUS DEPOSIT LOCATION NOT EMPTY, THEN MINE UNTIL FULL

            // IF DEPOSIT IS EMPTY, BECOMES EMPTY, OR THERE IS NO PREVIOUSLY RECORDED DEPOSIT,
            // THEN SEARCH THE AREA (~5 tile radius).

            // IF ROBOT IS FULL, HEAD BACK TO CLOSEST REFINERY

        // ENROUTE TO REFINERY

            // CALCULATE CLOSEST REFINERY BY DISTANCE. IF REFINERY NOT FOUND OR CANNOT BE REACHED WITHIN 25 TURNS, HEAD TO HQ

        return false;
    }

    static boolean minerBuildRefinery(MapLocation target) throws GameActionException {
        // Builds a refinery near soup deposits, but not on top of an existing deposit

        return false;
    }

    static boolean minerBuildSchool(MapLocation target) throws GameActionException {
        // Builds a design school near given target (such as an enemy building)

        return false;
    }

    static boolean minerBuildDroneFactory(MapLocation[] target) throws GameActionException {
        // Builds a drone factory near given target

        return false;
    }

    static boolean minerBuildNetgun(MapLocation target) throws GameActionException {
        // If only one point provided, build single netgun there
        // Returns false if battlefront is already fully occupied with netguns

        return false;
    }

    static boolean minerBuildVaporator(MapLocation[] target) throws GameActionException {
        // Builds a vaporator near given target

        return false;
    }

    static boolean droneToTarget(MapLocation dest) throws GameActionException {
        // Sends blockchain request for a drone to pick up self and drop off at dest

        return false;
    }

    static boolean droneAcrossWater(Direction dir) throws GameActionException {
        // Sends a blockchain requet for a drone to pick up self,
        // move in direction given by dir, and drop off when water boundary meets land
        // If edge of map is reached and still no land, pick a random direction (left/right),
        // turn to it, then hug edge of map and drop off when land is reached.

        return false;
    }

}

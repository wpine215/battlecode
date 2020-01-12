package examplefuncsplayer;
import java.util.Arrays;
import java.util.ArrayList;

import battlecode.common.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    static Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;
    
    static int rebroadcastMsg;
    static MapLocation localHQ;
    static MapLocation enemyHQ;
    static MapLocation previousLocation;
    static ArrayList<MapLocation> destinationQueue;
    static ArrayList<MapLocation> refineries;
    static ArrayList<MapLocation> soupDeposits;

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

        System.out.println("I'm a " + rc.getType() + " and I just got created!");
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
        if (rc.getRoundNumber() == 1) {
            int currSoup = rc.getTeamSoup();
            int msg = 1990010000;
            MapLocation currLoc = rc.getLocation();
            refineries.add(currLoc);
            msg += currLoc.x * 100;
            msg += currLoc.y;
            txHandler(msg, 1, 5, 3);
        }

        // Builds up to 3 miners if able to
        for (Direction dir : directions) {
            if (rc.getRobotCount() < 3)
                tryBuild(RobotType.MINER, dir);
        }
            
    }

    static void runMiner() throws GameActionException {
        tryMove(randomDirection());
        if (tryMove(randomDirection()))
            System.out.println("I moved!");

        for (Direction dir : directions)
            if (tryRefine(dir))
                System.out.println("I refined soup! " + rc.getTeamSoup());
        for (Direction dir : directions)
            if (tryMine(dir))
                System.out.println("I mined soup! " + rc.getSoupCarrying());
    }

    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
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
        // Protect HQ
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
        // MapLocation loc = rc.getLocation();
        // if (loc.x < 10 && loc.x < loc.y)
        //     return tryMove(Direction.EAST);
        // else if (loc.x < 10)
        //     return tryMove(Direction.SOUTH);
        // else if (loc.x > loc.y)
        //     return tryMove(Direction.WEST);
        // else
        //     return tryMove(Direction.NORTH);
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

    static void tryBlockchain() throws GameActionException {
        if (turnCount < 3) {
            int[] message = new int[10];
            for (int i = 0; i < 10; i++) {
                message[i] = 123;
            }
            if (rc.canSubmitTransaction(message, 10))
                rc.submitTransaction(message, 10);
        }
        // System.out.println(rc.getRoundMessages(turnCount-1));
    }


    /////////////////////////////////////////////////////////
    // CUSTOM CODE
    ////////////////////////////////////////////////////////


    /**
     * Handles sending transactions to blockchain.
     * Allows for multiple retries with greater cost.
     * 
     * @param msg message integer to send
     * @param base the initial cost to attempt sending with
     * @param multiplier the amount the cost is multiplied by on each retry
     * @param retries the maximum number of time to retry sending a message
     * @return true if message sent, false otherwise
     * @throws GameActionException
     */
    static boolean txHandler(int msg, int base, int multiplier, int retries) throws GameActionException {
        if (base < 1 || multiplier < 1 || retries < 1) return false;
        int currentTxAmount = base;
        int currentTry = 0;
        
        while (!rc.canSubmitTransaction(Arrays.asList(msg), currentTxAmount) && currentTry < retries) {
            currentTxAmount *= multiplier;
            currentTry++;
        }

        if (currentTry < retries) {
            submitTransaction(msg, currentTxAmount);
            System.out.println("TX SEND SUCCESS. MSG: " + msg + "; COST: " + currentTxAmount);
            return true;
        }

        System.out.println("TX SEND FAILURE. MSG: " + msg + "; COST: " + currentTxAmount);
        return false;
    }

    /**
     * Queries blockchain for any messages in current round pertaining
     * to location of soup deposits (relevant for miners/refineries)
     * 
     * @return array of message integers from previous round's block
     * @throws GameActionException
     */
    static int[] checkBlockchainSoup() throws GameActionException {
        int temp;
        List<integer> result = new ArrayList<integer>();
        Transaction[] t = rc.getBlock(rc.getRoundNumber() - 1);
        for (Transaction i : t) {
            temp = i.getMessage();

            // Broadcast of Soup Location
            if (temp / 1000 == 198001) {
                result.add(temp % 1980010000);
            }
        }
        return result.toArray();
    }

    /**
     * Queries blockchain for any messages in the current round
     * pertaining to actions which the landscaper should carry out.
     * 
     * @return array of message integers from previous round's block
     * @throws GameActionException
     */
    static int[] checkBlockchainLandscaper() throws GameActionException {
        int temp;
        List<integer> result = new ArrayList<integer>();
        Transaction[] t = rc.getBlock(rc.getRoundNumber() - 1);

        for (Transaction i : t) {
            temp = i.getMessage();

            // broadcast of HQ location
            if (temp / 10000 == 199001) {
                result.add(temp % 1990010000);
            }
        }
        return result.toArray();
    }

    static MapLocation[] crudeSoupScan() throws GameActionException {
        // Scan visible radius for soup able to be mined.
        // Keep in mind robots have different scan radiuses.
        // Also keep in mind a full scan is almost never necessary,
        // since assuming the robot just moved, it only needs to scan the
        // new, unscanned tiles which have entered its visible radius due to the movement.
        // This requires the robot instance to keep track of its previous location.
        List<MapLocation> result = new ArrayList<MapLocation>();

        // to-do finish function

        return result.toArray();
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

        if (rc.canMove(moveTowards) && !rc.senseFlooding(moveTowards)) {
            rc.move(moveTowards);
            result[0] = true;
            return true;
        } else {
            // Build alternate direction queue
            Direction[] queue = new Direction[4];
            queue[0] = moveTowards.rotateLeft();
            queue[1] = moveTowards.rotateRight();
            queue[2] = queue[0].rotateLeft();
            queue[3] = queue[1].rotateRight();

            for (Direction d : queue) {
                if (rc.canMove(d) && !rc.senseFlooding(d)) {
                    rc.move(d);
                    return true;
                }
            }
        }
        // Keep in mind: if the robot enters a dead end or concave obstacle,
        // it will become trapped. Pathfinding code still needs to account for this.
        // Maybe modify this to use A* next?
        return false;
    }

    static boolean HQRebroadcast(int priority) throws GameActionException {
        // resubmits rebroadcastMsg to blockchain on every round which is a multiple of 10
        // priority number corresponds to how much cost should be allocated to rebroadcasting
        // returns false if rebroadcast failed
    }

    static boolean minerDoScout() throws GameActionException {
        // Follows direction queue and broadcasts location of soup deposits or enemy HQ if found
        // Returns True while still scouting, returns False once scout run complete
    }

    static boolean minerDoMine(MapLocation nearestRefinery) throws GameActionException {
        // Retrieves soup deposit location from rebroadcast
        // Scouts x tiles around soup deposit location, mines soup if found
        // Returns to nearestRefinery once soup storage is full
        // Returns False if zero soup has been mined (may indicate deposit is empty)
    }

    static boolean minerDoDefend(MapLocation point, int radiusFromPoint) throws GameActionException {
        // Positions miner along closest available tile on given radius from point(such as HQ)
        // Used to build defensive line of robots so enemy can't get through easily
        // Returns false if the given radius is already fully occupied by defending miners
    }

    static boolean minerBuildNetline(MapLocation[] battlefront) throws GameActionException {
        // Builds netgun somewhere along line in between battlefront[0] and battlefront[1]
        // If only one point provided, build single netgun there
        // Makes sure to leave ample space from other netguns if in a line
        // Returns false if battlefront is already fully occupied with netguns
    }

    static boolean minerBuildRefinery(MapLocation[] target) throws GameActionException {
        // Builds a refinery near soup deposits, but not on top of an existing deposit
    }

    static boolean minerBuildSchool(MapLocation[] target) throws GameActionException {
        // Builds a design school near given target (such as an enemy building)
    }

    static boolean minerBuildDroneFactory(MapLocation[] target) throws GameActionException {
        // Builds a drone factory near given target
    }

    static boolean minerBuildVaporator(MapLocation[] target) throws GameActionException {
        // Builds a vaporator near given target
    }

    static boolean droneToTarget(MapLocation dest) throws GameActionException {
        // Sends blockchain request for a drone to pick up self and drop off at dest
    }

    static boolean droneAcrossWater(Direction dir) throws GameActionException {
        // Sends a blockchain requet for a drone to pick up self,
        // move in direction given by dir, and drop off when water boundary meets land
        // If edge of map is reached and still no land, pick a random direction (left/right),
        // turn to it, then hug edge of map and drop off when land is reached.
    }

}

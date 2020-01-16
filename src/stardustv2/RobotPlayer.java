package stardustv2;
import battlecode.common.*;

import java.util.Deque;
import java.util.ArrayList;
import java.util.LinkedList;

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

    static int sequentialID;

    // HQ-specific variables
    static int HQHealth = 50;
    static int HQElevation;
    static int minerCount = 0;

    // Pathfinding-specific variables
    static Pathfinding pathfinding;

    // Blockchain
    static Communication communication;

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

        // Instantiate pathfinding instance
        pathfinding = new Pathfinding(rc);

        // Instantiate communications
        communication = new Communication(rc);

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
        pathfinding.drawPersistentMLine();
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
            if (!pathfinding.travelTo(scoutTo)) {
                System.out.println("Miner " + sequentialID + " movement unsuccessful");
                minerState = MinerState.MINING;
            }
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
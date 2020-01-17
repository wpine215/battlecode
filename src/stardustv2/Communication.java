package stardustv2;

import battlecode.common.*;

import java.util.ArrayList;

public strictfp class Communication {
    static RobotController rc;
    final static int VALIDATION_OFFSET = 475;
    final static int FORWARD_DELTA = 3;
    final static int BACKWARDS_DELTA = -3;
    final static int LOWER_CODE_BITS = 1000;

    final static int CODE_GENESIS = 101;
    final static int CODE_REBROADCAST = 102;

    final static int CODE_SOUP_LOCATED = 201;
    final static int SOUP_BROADCAST_COST = 1;

    final static int REBROADCAST_MULTIPLIER = 10;
    final static int REBROADCAST_OFFSET = 5;

    public Communication(RobotController rc) {
        Communication.rc = rc;
    }

    public void trySendGenesisBlock(MapLocation HQ, int cost) throws GameActionException {
        if (rc.getRoundNum() == 1) {
            int HQSerialized = serializeLoc(HQ);
            int firstChunk = (generateValidationInt(1) * LOWER_CODE_BITS) + CODE_GENESIS;
            int[] gMsg = new int[]{firstChunk, HQSerialized, 0, 0, 0, 0, 0};
            send(gMsg, cost);
        }
    }

    public boolean trySendRebroadcastBlock(int cost) throws GameActionException {
        return false;
    }

    public void broadcastSoup(int sector) throws GameActionException {
        int firstChunk = (generateValidationInt(rc.getRoundNum()) * LOWER_CODE_BITS) + CODE_SOUP_LOCATED;
        int[] spMsg = new int[]{firstChunk, sector, 0, 0, 0, 0, 0};
        send(spMsg, SOUP_BROADCAST_COST);
    }

    public void broadcastEmptySoup(int sector) throws GameActionException {
        System.out.println("Sector " + sector + " is out of soup!");
    }

    public ArrayList<Integer> checkSoupBroadcast() throws GameActionException {
        ArrayList<Integer> result = new ArrayList<>();
        Transaction[] currentBlock = rc.getBlock(rc.getRoundNum() - 1);
        for (Transaction t : currentBlock) {
            if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_SOUP_LOCATED) {
                result.add(t.getMessage()[1]);
            }
        }
        return result;
    }

    public MapLocation getHQCoordinates() throws GameActionException {
        if (rc.getRoundNum() <= 1) return rc.getLocation();
        Transaction[] genesisBlock = rc.getBlock(1);
        for (Transaction t : genesisBlock) {
            if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_GENESIS) {
                return deserializeLoc(t.getMessage()[1]);
            }
        }
        Transaction[] nextGenesisBlock = rc.getBlock(2);
        for (Transaction t : nextGenesisBlock) {
            if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_GENESIS) {
                return deserializeLoc(t.getMessage()[1]);
            }
        }
        return rc.getLocation();
    }

    public boolean send(int[] msg, int cost) throws GameActionException {
        if (cost < 1) return false;
        if (msg.length != 7) return false;

        if (rc.canSubmitTransaction(msg, cost)) {
            rc.submitTransaction(msg, cost);
            return true;
        }
        return false;
    }

    public ArrayList<Transaction> receive(int round, int msgCode) throws GameActionException {
        ArrayList<Transaction> result = new ArrayList<>();
        Transaction[] roundTx = rc.getBlock(round);
        for (Transaction t : roundTx) {
            int firstChunk = t.getMessage()[0];
            if (firstChunk % LOWER_CODE_BITS == msgCode
                    && validate(firstChunk / LOWER_CODE_BITS, round)) {
                result.add(t);
            }
        }
        return result;
    }

    public static int serializeLoc(MapLocation loc) throws GameActionException {
        int serialized = loc.y;
        serialized += loc.x * 100;
        return serialized;
    }

    public static MapLocation deserializeLoc(int loc) throws GameActionException {
        return new MapLocation(loc / 100, loc % 100);
    }

    /*
    private static int[] encrypt(int[] cleartext)  throws GameActionException {
        int[] result = new int[7];
        for (int i : cleartext) {

        }
        return result;
    }

    private static int[] decrypt(int[] encrypted) throws GameActionException {
        int[] result = new int[7];
        for (int i : encrypted) {

        }
        return result;
    }

    private static int rotateDigit(int digit, int delta) throws GameActionException {
        assert(delta < 10 && delta > -10);
        if (digit + delta > 9) {
            return digit + delta - 10;
        } else if (digit + delta < 0) {
            return digit + delta + 10;
        }
        return digit + delta;
    }
    */

    private static int generateValidationInt(int round) throws GameActionException {
        return (round*2) + VALIDATION_OFFSET;
    }

    private static boolean validate(int hash, int round) throws GameActionException {
        int validRound = (hash - VALIDATION_OFFSET) / 2;
        return validRound <= round && validRound > round - 10;
    }

    private static boolean validate(int hash, int round, int cost) throws GameActionException {
        int validRound = (hash - VALIDATION_OFFSET) / 2;
        if (validRound == round) {
            return true;
        } else return validRound < round && validRound > round - 10 && lockedOut(round - 1, cost);
    }

    private static boolean lockedOut(int round, int cost) throws GameActionException {
        if (round < 1) return false;
        if (round > rc.getRoundNum()) return false;
        Transaction[] roundTx = rc.getBlock(round);
        if (roundTx.length < 7) return false;
        for (Transaction t : roundTx) {
            if (t.getCost() < cost) {

            }
        }
        return true;
    }
}

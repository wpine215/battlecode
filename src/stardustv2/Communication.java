package stardustv2;

import battlecode.common.*;
import java.util.*;

public strictfp class Communication {
    static RobotController rc;
    final static int VALIDATION_OFFSET = 475;
    final static int LOWER_CODE_BITS = 1000;

    final static int CODE_GENESIS           = 101;
    final static int CODE_REBROADCAST       = 102;
    final static int CODE_SOUP_LOCATED      = 201;
    final static int CODE_SOUP_EMPTY        = 202;
    final static int CODE_ENEMY_HQ_LOCATED  = 220;
    final static int CODE_REFINERY_BUILT    = 230;

    final static int SOUP_BROADCAST_COST        = 1;
    final static int EMPTY_SOUP_BROADCAST_COST  = 1;
    final static int ENEMY_HQ_LOCATED_COST      = 2;
    final static int ANNOUNCE_REFINERY_COST     = 2;

    final static int REBROADCAST_OFFSET = 5;
    final static int REBROADCAST_COST = 1;

    public Communication(RobotController rc) {
        Communication.rc = rc;
    }

    //////////////////////////////////////////////////
    // HQ INITIAL BROADCAST FUNCTIONS
    //////////////////////////////////////////////////

    public void trySendGenesisBlock(MapLocation HQ, int HQID, int cost) throws GameActionException {
        if (rc.getRoundNum() == 1) {
            int HQSerialized = serializeLoc(HQ);
            int firstChunk = (generateValidationInt(1) * LOWER_CODE_BITS) + CODE_GENESIS;
            int[] gMsg = new int[]{firstChunk, HQSerialized, HQID, 0, 0, 0, 0};
            send(gMsg, cost);
        }
    }

    public Transaction getGenesisBlock() throws GameActionException {
        if (rc.getRoundNum() <= 1) return null;
        Transaction[] genesisBlock = rc.getBlock(1);
        for (Transaction t : genesisBlock) {
            if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_GENESIS) {
                return t;
            }
        }
        Transaction[] nextGenesisBlock = rc.getBlock(2);
        for (Transaction t : nextGenesisBlock) {
            if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_GENESIS) {
                return t;
            }
        }
        return null;
    }

    public MapLocation getHQCoordinates(Transaction genesisBlock) throws GameActionException {
        if (genesisBlock != null) {
            return deserializeLoc(genesisBlock.getMessage()[1]);
        }
        return rc.getLocation();
    }

    public int getHQID(Transaction genesisBlock) throws GameActionException {
        if (genesisBlock != null) {
            return genesisBlock.getMessage()[2];
        }
        return -1;
    }

    //////////////////////////////////////////////////
    // HQ REBROADCAST FUNCTIONS
    //////////////////////////////////////////////////

    public boolean trySendRebroadcastBlock(int cost,
                                           Set<Integer> soupLocations,
                                           ArrayList<MapLocation> refineries,
                                           MapLocation enemyHQ,
                                           int health) throws GameActionException {
        // Only broadcast on rounds ending in 5
        if (rc.getRoundNum() % 10 != REBROADCAST_OFFSET) {
            return false;
        }

        // Get the 8 closest soup sectors to HQ
        // Key: distanceSquared, Value:sector
        TreeMap<Integer, Integer> eightClosest = new TreeMap<>();
        if (soupLocations.size() <= 8) {
            for (int sector : soupLocations) {
                eightClosest.put(0, sector);
            }
        } else {
            for (int sector : soupLocations) {
                if (eightClosest.size() >= 8) {
                    int d2sector = rc.getLocation().distanceSquaredTo(
                            Sector.getCenter(sector, rc.getMapHeight(), rc.getMapWidth())
                    );
                    int highestKey = eightClosest.lastKey();
                    if (d2sector < highestKey) {
                        eightClosest.remove(highestKey);
                        eightClosest.put(d2sector, sector);
                    }
                } else {
                    int d2sector = rc.getLocation().distanceSquaredTo(
                            Sector.getCenter(sector, rc.getMapHeight(), rc.getMapWidth())
                    );
                    eightClosest.put(d2sector, sector);
                }
            }
        }

        ArrayList<Integer> broadcastSoupLocations = new ArrayList<>(eightClosest.values());

        // Get first 4 refinery locations
        ArrayList<MapLocation> broadcastRefineryLocations =
                new ArrayList<>(refineries.subList(0, Math.min(refineries.size(), 4)));

        // Pack second chunk
        int secondChunk = 0;
        for (int i = 0, j = 1000000; i < 4; i++, j /= 100) {
            int currentBits;
            if (i >= broadcastSoupLocations.size()) {
                currentBits = 99;
            } else {
                currentBits = broadcastSoupLocations.get(i);
            }
            secondChunk += currentBits * j;
        }

        // Pack third chunk
        int thirdChunk = 0;
        for (int i = 0, j = 1000000; i < 4; i++, j /= 100) {
            int currentBits;
            if (i+4 > broadcastSoupLocations.size()) {
                currentBits = 99;
            } else {
                currentBits = broadcastSoupLocations.get(i+4);
            }
            thirdChunk += currentBits * j;
        }

        // Pack fourth & fifth chunks
        int fourthChunk = 99999999;
        int fifthChunk = 99999999;
        int numRefineryLocations = broadcastRefineryLocations.size();
        switch (numRefineryLocations) {
            case 0:
                break;
            case 1:
                fourthChunk = (serializeLoc(broadcastRefineryLocations.get(0)) * 10000) + 9999;
                fifthChunk = 99999999;
                break;
            case 2:
                fourthChunk = serializeLoc(broadcastRefineryLocations.get(0)) * 10000;
                fourthChunk += serializeLoc(broadcastRefineryLocations.get(1));
                fifthChunk = 99999999;
                break;
            case 3:
                fourthChunk = serializeLoc(broadcastRefineryLocations.get(0)) * 10000;
                fourthChunk += serializeLoc(broadcastRefineryLocations.get(1));
                fifthChunk = (serializeLoc(broadcastRefineryLocations.get(2)) * 10000) + 9999;
                break;
            case 4:
                fourthChunk = serializeLoc(broadcastRefineryLocations.get(0)) * 10000;
                fourthChunk += serializeLoc(broadcastRefineryLocations.get(1));
                fifthChunk = serializeLoc(broadcastRefineryLocations.get(2)) * 10000;
                fifthChunk += serializeLoc(broadcastRefineryLocations.get(3));
                break;
        }

        int sixthChunk = serializeLoc(enemyHQ);
        int firstChunk = (generateValidationInt(rc.getRoundNum()) * LOWER_CODE_BITS) + CODE_REBROADCAST;

        int[] rebroadcastMsg = new int[]{
                firstChunk,
                secondChunk,
                thirdChunk,
                fourthChunk,
                fifthChunk,
                sixthChunk,
                health
        };
        return send(rebroadcastMsg, REBROADCAST_COST);
    }

    public Transaction getLastRebroadcast() throws GameActionException {
        if (rc.getRoundNum() > 5) {
            int lastRebroadcast = ((rc.getRoundNum()/10) * 10) + 5;

            if (rc.getRoundNum() % 10 <= 5) {
                lastRebroadcast -= 10;
            }

            Transaction[] rbBlock = rc.getBlock(lastRebroadcast);
            for (Transaction t : rbBlock) {
                if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_REBROADCAST) {
                    return t;
                }
            }
        }
        System.out.println("getLastRebroadcast fallback condition!");
        return new Transaction(1, new int[]{0, 99999999, 99999999, 99999999, 99999999, 9999, 50}, 0);
    }

    public ArrayList<Integer> getSoupFromRebroadcast(Transaction t) throws GameActionException {
        ArrayList<Integer> result = new ArrayList<>();
        int firstFour = t.getMessage()[1];
        int lastFour = t.getMessage()[2];

        for (int i = 100; i <= 100000000; i *= 100) {
            if (((firstFour % i) / (i/100)) != 99) {
                System.out.println("getSoupFromRebroadcast firstFour: received soup sector #" + ((firstFour % i) / (i/100)));
                result.add(((firstFour % i) / (i/100)));
            }
            if (((lastFour % i) / (i/100)) != 99) {
                System.out.println("getSoupFromRebroadcast lastFour: received soup sector #" + ((lastFour % i) / (i/100)));
                result.add(((lastFour % i) / (i/100)));
            }
        }
        return result;
    }
    
    public ArrayList<MapLocation> getRefineriesFromRebroadcast(Transaction t) throws GameActionException {
        ArrayList<MapLocation> result = new ArrayList<>();
        int firstTwo = t.getMessage()[3];
        int lastTwo = t.getMessage()[4];
        if (firstTwo / 10000 != 9999) {
            result.add(deserializeLoc(firstTwo / 10000));
        }
        if (firstTwo % 10000 != 9999) {
            result.add(deserializeLoc(firstTwo % 10000));
        }
        if (lastTwo / 10000 != 9999) {
            result.add(deserializeLoc(lastTwo / 10000));
        }
        if (lastTwo % 10000 != 9999) {
            result.add(deserializeLoc(lastTwo % 10000));
        }
        return result;
    }

    public MapLocation getEnemyHQFromRebroadcast(Transaction t) throws GameActionException {
        return deserializeLoc(t.getMessage()[5]);
    }

    //////////////////////////////////////////////////
    // MINER/SCOUTING UNIT BROADCAST FUNCTIONS
    //////////////////////////////////////////////////

    public void broadcastSoup(int sector) throws GameActionException {
        int firstChunk = (generateValidationInt(rc.getRoundNum()) * LOWER_CODE_BITS) + CODE_SOUP_LOCATED;
        int[] spMsg = new int[]{firstChunk, sector, 0, 0, 0, 0, 0};
        send(spMsg, SOUP_BROADCAST_COST);
    }

    public void broadcastEmptySoup(ArrayList<Integer> emptySectors) throws GameActionException {
        int[] seMsg = new int[]{0, 0, 0, 0, 0, 0, 0};
        int firstChunk = (generateValidationInt(rc.getRoundNum()) * LOWER_CODE_BITS) + CODE_SOUP_EMPTY;
        seMsg[0] = firstChunk;
        seMsg[1] = emptySectors.size();
        for (int i = 0; i < Math.min(5, emptySectors.size()); i++) {
            System.out.println("Sector " + emptySectors.get(i) + " is out of soup!");
            seMsg[i+2] = emptySectors.get(i);
        }
        send(seMsg, EMPTY_SOUP_BROADCAST_COST);
    }

    public void announceNewRefinery(MapLocation loc) throws GameActionException {
        int firstChunk = (generateValidationInt(rc.getRoundNum()) * LOWER_CODE_BITS) + CODE_REFINERY_BUILT;
        int[] refMsg = new int[]{firstChunk, serializeLoc(loc), 0, 0, 0, 0, 0};
        send(refMsg, ANNOUNCE_REFINERY_COST);
    }

    public void announceEnemyHQ(MapLocation loc) throws GameActionException {
        int firstChunk = (generateValidationInt(rc.getRoundNum()) * LOWER_CODE_BITS) + CODE_ENEMY_HQ_LOCATED;
        int[] enMsg = new int[]{firstChunk, serializeLoc(loc), 0, 0, 0, 0, 0};
        send(enMsg, ENEMY_HQ_LOCATED_COST);
    }

    public ArrayList<MapLocation> checkNewRefineries(Transaction[] currentBlock) throws GameActionException {
        ArrayList<MapLocation> result = new ArrayList<>();
        for (Transaction t : currentBlock) {
            if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_REFINERY_BUILT) {
                if (validate(t.getMessage()[0], rc.getRoundNum())) {
                    result.add(deserializeLoc(t.getMessage()[1]));
                } else {
                    System.out.println("TAMPERED MESSAGE DETECTED - IGNORING");
                }
            }
        }
        return result;
    }

    public ArrayList<Integer> checkSoupBroadcast(Transaction[] currentBlock) throws GameActionException {
        ArrayList<Integer> result = new ArrayList<>();
        for (Transaction t : currentBlock) {
            if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_SOUP_LOCATED) {
                if (validate(t.getMessage()[0], rc.getRoundNum())) {
                    result.add(t.getMessage()[1]);
                } else {
                    System.out.println("TAMPERED MESSAGE DETECTED - IGNORING");
                }
            }
        }
        return result;
    }

    public ArrayList<Integer> checkEmptySoupBroadcast(Transaction[] currentBlock) throws GameActionException {
        ArrayList<Integer> result = new ArrayList<>();
        for (Transaction t : currentBlock) {
            if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_SOUP_EMPTY) {
                if (validate(t.getMessage()[0], rc.getRoundNum())) {
                    for (int i = 2; i < 2 + t.getMessage()[1]; i++) {
                        result.add(t.getMessage()[i]);
                    }
                } else {
                    System.out.println("TAMPERED MESSAGE DETECTED - IGNORING");
                }
            }
        }
        return result;
    }

    public MapLocation checkEnemyHQBroadcast(Transaction[] currentBlock) throws GameActionException {
        MapLocation result = new MapLocation(99, 99);
        for (Transaction t : currentBlock) {
            if (t.getMessage()[0] % LOWER_CODE_BITS == CODE_ENEMY_HQ_LOCATED) {
                if (validate(t.getMessage()[0], rc.getRoundNum())) {
                    result = deserializeLoc(t.getMessage()[1]);
                    break;
                } else {
                    System.out.println("TAMPERED MESSAGE DETECTED - IGNORING");
                }
            }
        }
        return result;
    }

    //////////////////////////////////////////////////
    // UTILITY FUNCTIONS FOR BLOCKCHAIN
    //////////////////////////////////////////////////

    public boolean send(int[] msg, int cost) throws GameActionException {
        if (cost < 1) return false;
        if (msg.length != 7) return false;

        if (rc.canSubmitTransaction(msg, cost)) {
            rc.submitTransaction(msg, cost);
            return true;
        }
        return false;
    }

    public static int serializeLoc(MapLocation loc) throws GameActionException {
        int serialized = loc.y;
        serialized += loc.x * 100;
        return serialized;
    }

    public static MapLocation deserializeLoc(int loc) throws GameActionException {
        return new MapLocation(loc / 100, loc % 100);
    }

    private static int generateValidationInt(int round) throws GameActionException {
        return (round*2) + VALIDATION_OFFSET;
    }

    private static boolean validate(int hash, int round) throws GameActionException {
        int parsedHash = hash / LOWER_CODE_BITS;
        int validRound = (parsedHash - VALIDATION_OFFSET) / 2;
        return validRound <= round && validRound > round - 5;
    }
}

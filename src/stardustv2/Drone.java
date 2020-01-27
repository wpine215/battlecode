package stardustv2;
import battlecode.common.*;

import java.util.*;

public strictfp class Drone {
    static RobotController rc;

    enum ObstacleDir {
        UNASSIGNED,
        LEFT,
        RIGHT
    }

    static boolean followingObstacle;
    static Direction currentDirection;
    static ArrayList<MapLocation> currentMLine;
    static Set<MapLocatio>

    public Drone(RobotController rc) {
        Drone.rc = rc;
    }

//    public void circleAround(MapLocation loc, int radiusSquared) {
//        if (!rc.isReady()) {
//            return;
//        }
//    }

    public boolean travelTo(MapLocation dest, int avoidR2HQ) throws GameActionException {
        // Returns true if movement in progress
        // Returns false if journey complete or obstacle encountered
        if (!currentMLine.isEmpty()) {
            drawPersistentMLine();
        }

        Random rand = new Random();

        if (resetRounds > 0) {
            ut.tryMove(ut.randomDirection());
            resetRounds--;
            return true;
        }

        if (iskDoubleRepeating()) {
            System.out.println(">>>>> Double repeating! Resetting...");
            reset();
            resetRounds = 2;
            return true;
        }

        if (isQuadRepeating()) {
            System.out.println(">>>>> Quad repeating! Resetting...");
            reset();
            resetRounds = 2;
            return true;
        }

        // If no m-line exists, we haven't performed path calculations yet
        if (currentMLine.isEmpty()) {
            getMLine(rc.getLocation(), dest);
            currentDirection = rc.getLocation().directionTo(dest);
        }

        // Already at destination
        if (rc.getLocation().equals(dest)) {
            System.out.println(">>>>> Already at destination!");
            currentMLine.clear();
            currentMLineSet.clear();
            locationHistory.clear();
            return false;
        }

        // We encountered obstacle point again
        if (rc.getLocation().equals(obstacleEncounteredAt) && !alreadyHitMapEdge) {
            System.out.println(">>>>> Encountered obstacle point again! Resetting...");
            reset();
//            resetRounds = 3;
//            return true; // used to be false btw
            return false;
        }

        // We encountered a map edge
        if (isLocationMapEdge(rc.getLocation()) && lastEightLocations.size() > 1) {
            if (alreadyHitMapEdge && lastMapEdge != getMapEdge(rc.getLocation())) {
                System.out.println(">>>>> Hit two map edges!");
                alreadyHitMapEdge = false;
                lastMapEdge = Pathfinding.MapEdge.UNASSIGNED;
                currentMLine.clear();
                currentMLineSet.clear();
                locationHistory.clear();
                return false;
            } else {
                alreadyHitMapEdge = true;
                lastMapEdge = getMapEdge(rc.getLocation());
                currentDirection = currentDirection.opposite();
                if (obstacleDir == Pathfinding.ObstacleDir.LEFT) {
                    obstacleDir = Pathfinding.ObstacleDir.RIGHT;
                } else {
                    obstacleDir = Pathfinding.ObstacleDir.LEFT;
                }
            }
        }

        // If we're on m-line, try moving directly to target
        if (locationOnMLine(rc.getLocation()) && !alreadyHitMapEdge) {
            // We have found the m-line again after following obstacle
            if (!locIsNull(obstacleEncounteredAt)) {
                obstacleEncounteredAt = new MapLocation(-1, -1);
                obstacleDir = Pathfinding.ObstacleDir.UNASSIGNED;
            }

            // Get next point on m-line and try moving to it
            MapLocation next = getNextPointOnMLine(rc.getLocation());
            Direction nextDir = rc.getLocation().directionTo(next);
            if (rc.canMove(nextDir) && !rc.senseFlooding(next) && (avoidR2HQ == 0 || !next.isWithinDistanceSquared(localHQ, avoidR2HQ))) {
                locationHistory.add(rc.getLocation());
                locationPushBack(rc.getLocation());
                rc.move(nextDir);
                currentDirection = nextDir;
                return true;
            } else {
                // Obstacle at next point on m-line, so do some following
                locationHistory.add(rc.getLocation());
                obstacleEncounteredAt = rc.getLocation();
                int initialDir;
                if (rc.canMove(nextDir.rotateLeft())
                        && !rc.senseFlooding(rc.adjacentLocation(nextDir.rotateLeft()))
                        && (avoidR2HQ == 0 || !rc.adjacentLocation(nextDir.rotateLeft()).isWithinDistanceSquared(localHQ, avoidR2HQ))) {
                    initialDir = 0;
                } else if (rc.canMove(nextDir.rotateRight())
                        && !rc.senseFlooding(rc.adjacentLocation(nextDir.rotateRight()))
                        && (avoidR2HQ == 0 || !rc.adjacentLocation(nextDir.rotateRight()).isWithinDistanceSquared(localHQ, avoidR2HQ))) {
                    initialDir = 1;
                } else {
                    initialDir = rand.nextInt(2);
                }

                if (initialDir == 0) {
                    System.out.println(">>>>> Starting to follow obstacle left!");
                    obstacleDir = Pathfinding.ObstacleDir.LEFT;
                    return followObstacleLeft(true, avoidR2HQ);
                } else {
                    System.out.println(">>>>> Starting to follow obstacle right!");
                    obstacleDir = Pathfinding.ObstacleDir.RIGHT;
                    return followObstacleRight(true, avoidR2HQ);
                }

            }
        } else {
            // Still following obstacle
            if (obstacleDir == Pathfinding.ObstacleDir.LEFT) {
                System.out.println(">>>>> STILL following obstacle left!");
                return followObstacleLeft(false, avoidR2HQ);
            } else if (obstacleDir == Pathfinding.ObstacleDir.RIGHT) {
                System.out.println(">>>>> STILL following obstacle right!");
                return followObstacleRight(false, avoidR2HQ);
            }
        }
        return false;
    }

}

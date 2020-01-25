package stardustvlattice;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public strictfp class DroneUtil {
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

    enum ObstacleDir {
        UNASSIGNED,
        LEFT,
        RIGHT
    }
    static int mapHeight;
    static int mapWidth;
    static boolean followingObstacle;
    static boolean alreadyHitMapEdge;
    static boolean rewindingToObstacle;
    static MapLocation obstacleEncounteredAt;
    static ObstacleDir obstacleDir;
    static Direction currentDirection;
    static Set<MapLocation> locationHistory;

    public DroneUtil(RobotController rc) {
        DroneUtil.rc = rc;
        mapHeight = rc.getMapHeight();
        mapWidth = rc.getMapWidth();
        followingObstacle = false;
        alreadyHitMapEdge = false;
        rewindingToObstacle = false;
        obstacleEncounteredAt = new MapLocation(-1, -1);
        obstacleDir = ObstacleDir.UNASSIGNED;
        currentDirection = Direction.CENTER;
        locationHistory = new HashSet<>();
    }

    public void reset() throws GameActionException {
        locationHistory.clear();
        currentDirection = Direction.CENTER;
        obstacleDir = ObstacleDir.UNASSIGNED;
        obstacleEncounteredAt = new MapLocation(-1, -1);
        followingObstacle = false;
        alreadyHitMapEdge = false;
        rewindingToObstacle = false;
    }

    public boolean travelTo(MapLocation dest, String style) throws GameActionException {
        // Returns true if movement on course
        // Returns false if journey complete or obstacle encountered

        int dx = dest.x-rc.getLocation().x;
        int dy = dest.y-rc.getLocation().y;

        // Already at destination
        if (dy == 0 && dx == 0) {
            System.out.println(">>>>> Already at destination!");
            locationHistory.clear();
            return false;
        }

        locationHistory.add(rc.getLocation());

        Direction nextDir = Direction.CENTER;

        // Styles for choosing direction
        if (style == "linear") {
          nextDir = ttLinFirst(dx, dy);
        } else {
          nextDir = ttDiagFirst(dx, dy);
        }

        if (rc.canMove(nextDir)) { // Drone can move

            rc.move(nextDir);
            currentDirection = nextDir;
            return true;

        } else {
          
            // Obstacle at next point, so try closest spin around
            
            obstacleEncounteredAt = rc.getLocation();

            boolean moved = false;
            boolean clockwise = true;
            Direction dirCW = nextDir.rotateRight();
            Direction dirCCW = nextDir.rotateLeft();
            int variance = 1;
            
            Direction finalDir = nextDir;

            // Priorize spin based on direction
            double slope = dy/dx;

            // CW preferred (using XOR)
            if((Math.abs(slope) < 1) ^ (slope < 0)) {
              while (variance < 4 && moved == false) {
                if (rc.canMove(dirCW)) {
                  System.out.println(">>>>> Avoiding obstacle CW x" + variance);
                  finalDir = dirCW;
                  moved = true;
                } else if (rc.canMove(dirCCW)){
                  System.out.println(">>>>> Avoiding obstacle CCW x" + variance);
                  finalDir = dirCCW;
                  moved = true;
                }
                else {
                  variance++;
                  dirCW = dirCW.rotateRight();
                  dirCCW = dirCCW.rotateRight();
                }
              }
            } else { // CCW preferred
              while (variance < 4 && moved == false) {
                if (rc.canMove(dirCCW)) {
                  System.out.println(">>>>> Avoiding obstacle CCW x" + variance);
                  finalDir = dirCCW;
                  moved = true;
                } else if (rc.canMove(dirCW)){
                  System.out.println(">>>>> Avoiding obstacle CW x" + variance);
                  finalDir = dirCW;
                  moved = true;
                }
                else {
                  variance++;
                  dirCW = dirCW.rotateRight();
                  dirCCW = dirCCW.rotateRight();
                }
              }
            }

            if (finalDir != nextDir) {
              rc.move(finalDir);
            } else if (rc.canMove(dirCW.rotateRight())) {
              System.out.println(">>>>> Cannot advance, retreating");
              rc.move(dirCW.rotateRight());
            } else {
              System.out.println(">>>>> Cannot move");
            }
        }
        return false;
    }

    // This looks disgusting but it's effective
    public static Direction ttDiagFirst(int dx, int dy) {
      if (dx == 0) {
        if (dy == 0)
          return Direction.CENTER;
        else if (dy > 0)
          return Direction.NORTH;
        else if (dy < 0)
          return Direction.SOUTH;
      } else if (dx > 0) {
        if (dy == 0)
          return Direction.EAST;
        else if (dy > 0)
          return Direction.NORTHEAST;
        else if (dy < 0)
          return Direction.SOUTHEAST;
      } else if (dx < 0) {
        if (dy == 0)
          return Direction.WEST;
        else if (dy > 0)
          return Direction.NORTHWEST;
        else if (dy < 0)
          return Direction.SOUTHWEST;
      }
      return Direction.CENTER;
    }

    // Uses ttDiagFirst if diagonal
    public static Direction ttLinFirst(int dx, int dy) {
      int ax = Math.abs(dx);
      int ay = Math.abs(dy);
      if (ax == ay) {
        return ttDiagFirst(dx, dy);
      } else if (ax > ay) {
        if (dx < 0)
          return Direction.WEST;
        else if (dx > 0)
          return Direction.EAST;
      } else if (ax < ay) {
        if (dy < 0)
          return Direction.SOUTH;
        else if (dy > 0)
          return Direction.NORTH;
      } 
      return Direction.CENTER;
    }



    public static boolean isLocationMapEdge(MapLocation loc) throws GameActionException {
        return loc.x == 0 || loc.x == mapWidth-1 || loc.y == 0 || loc.y == mapHeight-1;
    }

    public static boolean locIsNull(MapLocation loc) throws GameActionException {
        return loc.x < 0 || loc.y < 0;
    }

    private boolean locationAlreadyVisited(MapLocation loc) throws GameActionException {
        return locationHistory.contains(loc);
    }
    
}

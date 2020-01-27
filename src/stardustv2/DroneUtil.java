package stardustv2;

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
    static MapLocation localHQ;
    static MapLocation enemyHQ;
    static boolean followingObstacle;
    static boolean alreadyHitMapEdge;
    static boolean rewindingToObstacle;
    static MapLocation obstacleEncounteredAt;
    static ObstacleDir obstacleDir;
    static Direction currentDirection;
    static Set<MapLocation> locationHistory;

    public DroneUtil(RobotController rc, MapLocation localHQ) {
        DroneUtil.rc = rc;
        DroneUtil.localHQ = localHQ;
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

    public int collectByID(MapLocation goal, int collectID) throws GameActionException {
      
      // Returns -1 if carrying other robot 
      // Returns 0 if robot picked up
      // Returns 1 if already complete

      // Returns 10 if robot not found once approaching destination
      // Returns 11 if on course to location
      // Returns 12 if on course to location with obstacle

      // Returns 13 if reached destination and robot not visible (should be impossible)
      // Returns 14 if on course to visible robot
      // Returns 15 if on course to visible robot with obstacle
     
      
      int outputCode = 10;
    
      RobotInfo self = rc.senseRobot(rc.getID());
      int heldID = self.getHeldUnitID();

      if(heldID == collectID)
        outputCode = 0;
      else if (heldID > 0)
        outputCode = -1;

      else {

        MapLocation dest = goal;

        boolean targetVisible = false;
        RobotInfo target;

        if (self.getHeldUnitID() == collectID){
          outputCode = 1;
        }

        else if (rc.canSenseRobot(collectID)) {
          target = rc.senseRobot(collectID);
          dest = target.getLocation();
          targetVisible = true;
          int dx = dest.x-rc.getLocation().x;
          int dy = dest.y-rc.getLocation().y;

          if ((dx == -1 || dx == 0 || dx == 1) &&
              (dy == -1 || dy == 0 || dy == 1)) {
            if(rc.canPickUpUnit(collectID)) {
              rc.pickUpUnit(collectID);
              outputCode = 20;
            }
          }
        }
      
        int travelToCode = 0;
        
        travelToCode = travelTo(dest, "linear");

        outputCode += travelToCode;

        if (targetVisible)
          outputCode += 3;

      }

      return outputCode;

    }


    public int travelTo(MapLocation dest, String style, Boolean overrideNets, Boolean overrideHQ) throws GameActionException {
      
        // Returns 0 if travel complete
        // Returns 1 if on course
        // Returns 2 if on course with obstacle
        // Returns 3 if trying to exit hq or enemy net gun radius

        MapLocation loc = rc.getLocation();

        int dx = dest.x-loc.x;
        int dy = dest.y-loc.y;

        // Already at destination
        if (dy == 0 && dx == 0) {
            System.out.println(">>>>> Already at destination!");
            locationHistory.clear();
            return 0;
        }

        locationHistory.add(loc);

        Direction nextDir = Direction.CENTER;

        // Styles for choosing direction
        if (style == "linear") {
          nextDir = ttLinFirst(dx, dy);
        } else {
          nextDir = ttDiagFirst(dx, dy);
        }

        RobotInfo[] visibleRobots = rc.senseNearbyRobots();

        ArrayList<MapLocation> netguns = new ArrayList<MapLocation>;

        

        if (enemyHQ == null)
          checkForEnemyHQ(visibleRobots);

        boolean inNetgunRange = isInNetgunRange(loc, visibleRobots);
        
        for(RobotInfo n : visibleRobots) {
          if (n.getTeam() != rc.getTeam() && n.getType() == RobotType.NET_GUN)
            netguns.add(n.getLocation());
        } // A bit redundant but ok

        if (
          rc.canMove(nextDir) 
          && (!loc.add(nextDir).isWithinDistanceSquared(localHQ, 8) || overrideHQ)
          && (!isInNetgunRange(loc.add(nextDir), visibleRobots) || overrideNets) 
          && (!inNetgunRange || overrideNets)
          ) { // Drone can move normally

            rc.move(nextDir);
            currentDirection = nextDir;
            return 1;

        } else if (
          (!loc.isWithinDistanceSquared(localHQ, 8) || overrideHQ)
          && (!inNetgunRange || overrideNets)
        ) { // leave netgun or hq range

        } else if (
          (!loc.add(nextDir).isWithinDistanceSquared(localHQ, 8) || overrideHQ)
          && (!inNetgunRange || overrideNets)
         ) { // Obstacle (or net range/HQ) at next point, so try closest spin around but not in HQ range
            
            obstacleEncounteredAt = loc;

            boolean moved = false;
            Direction dirCW = nextDir.rotateRight();
            Direction dirCCW = nextDir.rotateLeft();
            int variance = 1;
            
            Direction finalDir = nextDir;

            // Priorize spin based on direction
            double slope = dy/dx;

            // CW preferred (using XOR)
            if((Math.abs(slope) < 1) ^ (slope < 0)) {
              while (variance < 4 && !moved) {
                if (rc.canMove(dirCW) && (!isInNetgunRange(loc.add(dirCW), visibleRobots) || overrideNets))  {
                  System.out.println(">>>>> Avoiding obstacle CW x" + variance);
                  finalDir = dirCW;
                  moved = true;
                } else if (rc.canMove(dirCCW) && (!isInNetgunRange(loc.add(dirCCW), visibleRobots) || overrideNets)) {
                  System.out.println(">>>>> Avoiding obstacle CCW x" + variance);
                  finalDir = dirCCW;
                  moved = true;
                } else {
                  variance++;
                  dirCW = dirCW.rotateRight();
                  dirCCW = dirCCW.rotateRight();
                }
              }
            } else { // CCW preferred
              while (variance < 4 && !moved) {
                if (rc.canMove(dirCCW)&& (!isInNetgunRange(loc.add(dirCCW), visibleRobots) || overrideNets) ) {
                  System.out.println(">>>>> Avoiding obstacle CCW x" + variance);
                  finalDir = dirCCW;
                  moved = true;
                } else if (rc.canMove(dirCW) && (!isInNetgunRange(loc.add(dirCW), visibleRobots) || overrideNets) ){
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
            } else if (rc.canMove(dirCW.rotateRight()) && !loc.add(dirCW).isWithinDistanceSquared(localHQ, 8)) {
              System.out.println(">>>>> Cannot advance, retreating");
              rc.move(dirCW.rotateRight());
            } else {
              System.out.println(">>>>> Cannot move");
            }
        }
        return 2;
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

    public static boolean checkForEnemyHQ(RobotInfo[] visibleRobots){
      for(RobotInfo r : visibleRobots) {
        if(r.getType() == RobotType.HQ && r.getTeam() != rc.getTeam()) {
            enemyHQ = r.getLocation();
            return true;
          }
        }
      return false;
    }

    public static boolean isInNetgunRange(MapLocation loc, RobotInfo[] visibleRobots) {

      if (enemyHQ != null)
        if (loc.isWithinDistanceSquared(enemyHQ, 15))
          return true;

      for(RobotInfo r : visibleRobots){
        if (r.getTeam() != rc.getTeam())
          if(loc.isWithinDistanceSquared(r.getLocation(), 15))
            return true;

      }

      return false;
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

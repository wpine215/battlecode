package lectureplayer;
import battlecode.common.*;

public class Landscaper extends Unit {

    public Landscaper(RobotController r) {
        super(r);
    }

    public void takeTurn() throws GameActionException {
        super.takeTurn();

        // first, save HQ by trying to remove dirt from it
        if (hqLoc != null && hqLoc.isAdjacentTo(rc.getLocation())) {
            Direction dirtohq = rc.getLocation().directionTo(hqLoc);
            if(rc.canDigDirt(dirtohq)){
                rc.digDirt(dirtohq);
            }
        }

        if(rc.getDirtCarrying() == 0){
            tryDig();
        }

        MapLocation bestPlaceToBuildWall = null;
        // find best place to build
        if(hqLoc != null) {
            int lowestElevation = 9999999;
            for (Direction dir : Util.directions) {
                MapLocation tileToCheck = hqLoc.add(dir);
                if(rc.getLocation().distanceSquaredTo(tileToCheck) < 4
                        && rc.canDepositDirt(rc.getLocation().directionTo(tileToCheck))) {
                    if (rc.senseElevation(tileToCheck) < lowestElevation) {
                        lowestElevation = rc.senseElevation(tileToCheck);
                        bestPlaceToBuildWall = tileToCheck;
                    }
                }
            }
        }

        if (Math.random() < 0.8){
            // build the wall
            if (bestPlaceToBuildWall != null) {
                rc.depositDirt(rc.getLocation().directionTo(bestPlaceToBuildWall));
                rc.setIndicatorDot(bestPlaceToBuildWall, 0, 255, 0);
                System.out.println("building a wall");
            }
        }

        // otherwise try to get to the hq
        if(hqLoc != null){
            nav.goTo(hqLoc);
        } else {
            nav.goTo(Util.randomDirection());
        }
    }

    boolean tryDig() throws GameActionException {
        Direction dir;
        if(hqLoc == null){
            dir = Util.randomDirection();
        } else {
            dir = hqLoc.directionTo(rc.getLocation());
        }
        if(rc.canDigDirt(dir)){
            rc.digDirt(dir);
            rc.setIndicatorDot(rc.getLocation().add(dir), 255, 0, 0);
            return true;
        }
        return false;
    }
}
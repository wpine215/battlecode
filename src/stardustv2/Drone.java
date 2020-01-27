package stardustv2;
import battlecode.common.*;

import java.util.*;

public strictfp class Drone {
    enum DroneDirection {
        CW,
        CCW
    }

    static RobotController rc;

    public Drone(RobotController rc) {
        Drone.rc = rc;
    }

    public void circleAround(MapLocation loc, int radiusSquared) {
        if (!rc.isReady()) {
            return;
        }


    }
}

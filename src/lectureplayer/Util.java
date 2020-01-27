package lectureplayer;
import battlecode.common.*;

// This is a file to accumulate all the random helper functions
// which don't interact with the game, but are common enough to be used in multiple places.
// For example, lots of logic involving MapLocations and Directions is common and ubiquitous.
public class Util {
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

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }
}

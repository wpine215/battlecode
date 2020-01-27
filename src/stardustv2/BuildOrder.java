package stardustv2;

public strictfp class BuildOrder {
    final static int[] DRONECENTER_WAVES        = new int[]{1, 3};
    final static int[] DRONECENTER_PRIORITIES   = new int[]{0, 2};

    final static int[] DRONE_WAVES              = new int[]{2, 4, 10};
    final static int[] DRONE_PRIORITIES         = new int[]{0, 1, 1};

    final static int[] DESIGN_WAVES             = new int[]{1, 2};
    final static int[] DESIGN_PRIORITIES        = new int[]{0, 2};

    final static int[] LANDSCAPER_WAVES         = new int[]{8, 16};
    final static int[] LANDSCAPER_PRIORITIES    = new int[]{0, 1};

    final static int[] MINER_WAVES              = new int[]{4, 5};
    final static int[] MINER_PRIORITIES         = new int[]{0, 0};

    // Refineries are handled on-the-fly, and capped at this value
    final static int MAX_REFINERIES = 3;

    // Vaporators are handled on-the-fly, and capped at this value
    final static int MAX_VAPORATORS = 2;
}

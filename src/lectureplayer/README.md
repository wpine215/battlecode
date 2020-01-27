# lectureplayer
example player for battlecode 2020

The goal of this repo is to give you a starting point and example setup for collaborating with your team.

See the battlecode [YouTube channel](https://www.youtube.com/channel/UCOrfTSnyimIXfYzI8j_-CTQ) for recordings of the accompanying lectures.

## prerequisites

First, make sure you have battlecode installed, following the quick setup [here](http://2020.battlecode.org/getting-started) to install the [scaffold](https://github.com/battlecode/battlecode20-scaffold).

Also, install [git](https://git-scm.com/) and understand the basic commands.

## setup

All bots must be put in the `src` folder inside the `battlecode20-scaffold` folder.
You should see `examplefuncsplayer` already, the example player which comes with the scaffold.
We're going to add a new bot, `lectureplayer`, which is vastly superior.

To add `lectureplayer`, navigate in your terminal (or git bash for windows) to `battlecode20-scaffold/src/` and do
```bash
git clone https://github.com/battlecode/lectureplayer.git
```
Now you can run matches between `lectureplayer` and `examplefuncsplayer`, and `git pull` updates to lecture player as it improves over the coming weeks.

## your bot

You can use this exact same setup for your own bot!
Create a **private** GitHub repository with your team, and make sure the folder name matches the package name on the first line of `RobotPlayer.java`.
Clone that repo into the `src` folder and you're good to go!

# OthelloBot

OthelloBot is a Discord Bot used to play othello in discord text channels against other players or a bot. It includes graphical interface to see the othello board and a database with statistics for each player.

## Start the Bot

Requires PostgreSQL and Java installed.

Create a [bot](https://discord.com/developers/applications) on discord. You should be given a token when bot creation is complete.

Make sure the bot has permissions to read and write messages.

Create a Postgres database called "pandaothello" (schema will be generated when you run the app).

cd to the root of this cloned repo.

run `mvn package` to build the project.

cd to the built jar file in `/target`.

run `java -cp JarName.jar Main <your_discord_token>`.

The bot should be ready to respond to commands!

## Commands

`!challenge @user`

Challenges another user to an othello game. Another player can accept the challenge with the `!accept` command.

`!challengebot level`

Challenges the bot to an othello game. The bot can be level 1-15, level 1 searching until depth 1 and level 15 searching until depth 15 (for the bot to work past level 10 you need very good hardware)

`!accept @user`

Accept a challenge from a user.

`!forfeit`

Forfets the game currently being played.

`!move move`

Make a move on the specific file. Move format is column-row.

`!view`

View the current board state the game the user is playing, and all avaliable moves.

`!analyze level`

Performs an analysis on the current game. Displays the bot's heuristic ranking for each move.

`!stats`

Fetches the stats for the current user. Displays rating, winrate, wins, losses, and draws.

`!leaderboard`

Shows the top users with the highest elo in the entire database.


## Images

<img src="https://user-images.githubusercontent.com/58538077/181820016-f7f330ee-481b-4eb7-ab93-9047336fef0d.png" width="35%" height="35%">

<img src="https://user-images.githubusercontent.com/58538077/181821354-1f19f262-2679-4969-8204-75d8f251a847.png" width="35%" height="35%">

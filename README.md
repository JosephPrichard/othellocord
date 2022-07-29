# OthelloBot

OthelloBot is a Discord Bot used to play othello in discord text channels against other players or a bot. It includes graphical interface to see the chess board and a database with statistics for each player.

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

<img src="">

`!challengebot level`

Challenges the bot to an othello game. The bot can be level 1-15, level 1 searching until depth 1 and level 15 searching until depth 15 (for the bot to work past level 10 you need very good hardware)

<img src="" width="35%" height="35%">

`!accept @user`

Accept a challenge from a user.

<img src="" width="35%" height="35%">

`!forfeit`

Forfets the game currently being played.

<img src="" width="35%" height="35%">

`!move move`

Make a move on the specific file. Move format is column-row.

<img src="" width="35%" height="35%">

`!view`

View the current board state the game the user is playing, and all avaliable moves.

<img src="" width="35%" height="35%">

`!analyze level`

Performs an analysis on the current game. Displays the bot's heuristic ranking for each move.

<img src="" width="55%" height="55%">

`!stats`

Fetches the stats for the current user. Displays rating, winrate, wins, losses, and draws.

<img src="" width="55%" height="55%">

`!leaderboard`

Shows the top users with the highest elo in the entire database.

<img src="" width="55%" height="55%">

# OthelloCord

OthelloCord is a self-hosted Discord Bot used to play othello in discord text channels against other players or a bot. It includes graphical interface to see the othello board and a database with statistics for each player.

## Build & Execution

Install Dependencies
`go mod tidy`

Create a .env file
```
DISCORD_TOKEN=<your-bots-token>
DISCORD_APP_ID=<your-bots-api-key>
```

Run the Program
`go run cmd/bot/main.go`

## Commands

`/challenge @user`

Challenges another user to an othello game. Another player can accept the challenge with the `/accept` discord.

`/challengebot level`

Challenges the bot to an othello game. The bot can be level 1-6, each level using a different depth 
(for the bot to feel snappy on level 6 you need very good hardware).

`/accept @user`

Accept a challenge from a user.

`/forfeit`

Forfeits the game currently being played.

`/move move`

Make a move on the current game. Move format is column-row.

`/view`

View the current board state the game the user is playing, and all available moves.

`/analyze level`

Performs an analysis on the current game. Displays the bot's heuristic ranking for each move.

`/stats`

Fetches the stats for the current user. Displays rating, win rate, wins, losses, and draws.

`/leaderboard`

Shows the top users with the highest elo in the entire database.

`/simulate`

Run a game between two bots real time in a text channel.

## Engine

OthelloCord contains a rudimentary engine for players to play against.

The evaluation algorithm uses a standard implementation of the Minimax algorithm with an Alpha-Beta pruning optimization. The Minimax algorithm is the backbone of the algorithm responsible for performing the search and deciding which nodes should be pruned.

The algorithm's heuristic evaluation function is responsible for deciding how good a board state is.
The primary heuristic function is implemented as a combination of 4 other heuristic functions (Parity Heuristic, Corner Heuristic, XCSquare Heuristic, and Mobility Heuristic).
All heuristic components are normalized to a weight between 100 and 0, with 100 being the highest weight and 0 being the lowest.

The Othello Board implementation uses 2 longs (8 bytes) (128 bits) to store the bit board. "White" is represented by 10, "Black" is represented by 01, and "Empty" (no disc) is represented by 00. Since each tile needs 2 bits to represent and there are a total of 64 tiles, we can represent the board using 64 * 2 = 128 bits.

The algorithm keeps track of previously evaluated boards with a transposition table. The transposition table is implemented using a cache with a Deep2 replacement scheme.
Each cache line has 2 buckets, one which is only replaced if the new board is found at a greater depth, the other is replaced if the first bucket is not.

## Examples

<img src="https://github.com/JosephPrichard/OthelloCord/assets/58538077/0096a164-cfb9-44a1-be89-30896e93f0ff" width="45%" height="45%">
<img src="https://github.com/JosephPrichard/OthelloCord/assets/58538077/c53ecbc3-800b-4767-8553-498f9c529874" width="45%" height="45%">

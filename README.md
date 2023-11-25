# OthelloBot

OthelloBot is a self-hosted Discord Bot used to play othello in discord text channels against other players or a bot. It includes graphical interface to see the othello board and a database with statistics for each player.

## AI Algorithm

### Minimax 

The AI uses a standard implementation of the Minimax algorithm with an Alpha-Beta pruning optimization. The Minimax algorithm is the backbone of the AI responsible for performing the search and deciding which nodes should be pruned.

### Heuristics

The AI's heuristic evaluation function is responisble for deciding how good a board state is. The primary heuristic function is implemented as a combination of the 5 following specified heuristic functions

- Parity Heuristic (Measures the number of caputured discs. Useful for closing out games. The weightage increases gradually as the game goes on.)

- Corner Heuristic (Meaures the number of captured corners. Highly weighted at all stages of the game.)

- XCSquare Heuristic (Measures the number of captured X squares and C squares. Lowly weighted at all stages of the game.)

- Mobility Heuristic (Measures the number of avaliable moves. The weightage decreases gradually as the game goes on.)

- Stability Heuristic (Measures the number of moves that cannot be flipped. Highly weighted at all stages of the game.)

All heuristic components are normalized to a weight between 100 and 0, with 100 being the highest weight and 0 being the lowest.

### Bit Board

The Othello Board implementation uses 2 longs (8 bytes) (128 bits) to store the bit board. "White is represented by 10, "Black" is represented by 01, and "Empty" (no disc) is represented by 00. Since each tile needs 2 bits to represent and there are a total of 64 tiles, we can represent the board using 64 * 2 = 128 bits. 

This optimization provides both significant memory and time performance increases.

### Transposition Table

The AI keeps track of previously evaluated boards with a transposition table. The transposition table is implemented using a cache with a Deep2 replacement scheme.
Each cache line has 2 buckets, one which is only replaced if the new board is found at a greater depth, the other is replaced if the first bucket is not.

## UML Diagrams

![commandia](https://github.com/JosephPrichard/OthelloBot/assets/58538077/650df48d-0f5c-4126-a002-4ffcee972b62)
![othellodia](https://github.com/JosephPrichard/OthelloBot/assets/58538077/cc016c02-794e-47ae-953b-e7eb0d63084d)
![servicedia](https://github.com/JosephPrichard/OthelloBot/assets/58538077/4bfd3bee-7121-4a60-8cc2-a4da4f0b6982)

## Commands

`/challenge @user`

Challenges another user to an othello game. Another player can accept the challenge with the `!accept` command.

`/challengebot level`

Challenges the bot to an othello game. The bot can be level 1-15, level 1 searching until depth 1 and level 15 searching until depth 15 (for the bot to work past level 10 you need very good hardware)

`/accept @user`

Accept a challenge from a user.

`/forfeit`

Forfets the game currently being played.

`/move move`

Make a move on the current game. Move format is column-row.

`/view`

View the current board state the game the user is playing, and all avaliable moves.

`/analyze level`

Performs an analysis on the current game. Displays the bot's heuristic ranking for each move.

`/stats`

Fetches the stats for the current user. Displays rating, winrate, wins, losses, and draws.

`/leaderboard`

Shows the top users with the highest elo in the entire database.

`/theme`

Allows you to see your current theme or change it to a new one.

## Images

<img src="https://user-images.githubusercontent.com/58538077/181820016-f7f330ee-481b-4eb7-ab93-9047336fef0d.png" width="35%" height="35%">
<img src="https://user-images.githubusercontent.com/58538077/216801119-b08ff083-74d8-49d7-96bf-40e904348004.png" width="35%" height="35%">

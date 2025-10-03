package app

import (
	"fmt"
	"github.com/joho/godotenv"
	"github.com/stretchr/testify/assert"
	"os"
	"testing"
	"time"
)

func setupShell(t *testing.T) *NTestShell {
	if err := godotenv.Load(); err != nil {
		t.Log("failed to load .env file")
	}

	path := os.Getenv("NTEST_PATH")
	t.Logf("making ntest shell with path: %s", path)

	sh, err := StartNTestShell(path)
	if err != nil {
		t.Fatalf("failed to start ntest shell: %v", err)
	}
	return sh
}

func TestNTestShell_FindBestMove(t *testing.T) {
	game := OthelloGame{WhitePlayer: MakePlayer("id1", "name1"), BlackPlayer: MakePlayer("id2", "name2"), Board: MakeInitialBoard()}
	t.Logf("find best move test board:\n%s", game.Board.String())

	var err error
	stopChan := make(chan struct{})

	go func() {
		_, err = setupShell(t).findBestMove(game, 5)
		stopChan <- struct{}{}
	}()

	timer := time.NewTimer(time.Second * 1)
	defer timer.Stop()

	select {
	case <-stopChan:
		// we're just testing that this does not error out or time out, the actual response is non-deterministic
		assert.Nil(t, err)
	case <-timer.C:
		t.Fatalf("ntest find best move test has timed out")
	}
}

func TestNTestShell_FindRankedMoves(t *testing.T) {
	// we need to run this twice to account for 'search' and 'book' lines
	cnstBoard := MakeInitialBoard()
	rndBoard, moveList := RandomBoard(50)

	player1 := MakePlayer("id1", "name1")
	player2 := MakePlayer("id2", "name2")

	type Test struct {
		game OthelloGame
	}

	tests := []Test{
		// this will get 'book' or 'search' depending on whether this is the first run or not
		{game: OthelloGame{WhitePlayer: player1, BlackPlayer: player2, Board: cnstBoard}},
		// this will get 'book' because the previous search has the same board
		{game: OthelloGame{WhitePlayer: player1, BlackPlayer: player2, Board: cnstBoard}},
		// this will get 'search' always since the board is cryptographically so random there is *ZERO* chance it could be in the book
		{game: OthelloGame{WhitePlayer: player1, BlackPlayer: player2, Board: rndBoard, MoveList: moveList}},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("test/%d", i), func(t *testing.T) {
			t.Logf("find ranked move test board:\n%s%s", test.game.Board.String(), test.game.MarshalGGF())

			var moves []RankTile
			var err error
			stopChan := make(chan struct{})

			go func() {
				moves, err = setupShell(t).findRankedMoves(test.game, 6)
				stopChan <- struct{}{}
			}()

			timer := time.NewTimer(time.Second * 1)
			defer timer.Stop()

			select {
			case <-stopChan:
				assert.Nil(t, err)
				assert.Equal(t, len(test.game.Board.FindCurrentMoves()), len(moves))
			case <-timer.C:
				t.Fatalf("ntest find ranked moves test has timed out")
			}
		})
	}
}

package app

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/joho/godotenv"
	"github.com/stretchr/testify/assert"
)

func setupTestShell(t *testing.T) NTestShellAPI {
	if err := godotenv.Load(); err != nil {
		t.Log("failed to load .env file")
	}

	path := os.Getenv("NTEST_PATH")
	t.Logf("making ntest shell with path: %s", path)

	sh, err := MakeNTestShellPool(path, 2)
	if err != nil {
		t.Fatalf("failed to start ntest shell: %v", err)
	}
	return sh
}

func TestNTestShell_FindBestMove(t *testing.T) {
	game := OthelloGame{WhitePlayer: MakePlayer("id1", "name1"), BlackPlayer: MakePlayer("id2", "name2"), Board: MakeInitialBoard()}
	t.Logf("find best move test board:\n%s", game.Board.String())

	ctx, cancel := context.WithTimeout(t.Context(), time.Second*1)
	defer cancel()

	stopChan := make(chan error)

	go func() {
		_, err := setupTestShell(t).FindBestMove(ctx, game, 5)
		stopChan <- err
	}()

	select {
	case err := <-stopChan:
		// we're just testing that this does not error out or time out; the actual response is non-deterministic
		assert.Nil(t, err)
	case <-ctx.Done():
		t.Fatalf("ntest find best move test has timed out")
	}
}

func TestNTestShell_FindBestMove_TimesOut(t *testing.T) {
	game := OthelloGame{WhitePlayer: MakePlayer("id1", "name1"), BlackPlayer: MakePlayer("id2", "name2"), Board: MakeInitialBoard()}
	t.Logf("find best move test board:\n%s", game.Board.String())

	ctx, cancel := context.WithTimeout(t.Context(), time.Second*1)
	defer cancel()

	stopChan := make(chan error)

	go func() {
		// guaranteed timeout
		ctx, cancel := context.WithTimeout(t.Context(), time.Nanosecond*1)
		defer cancel()

		_, err := setupTestShell(t).FindBestMove(ctx, game, 5)
		stopChan <- err
	}()

	select {
	case err := <-stopChan:
		assert.ErrorIs(t, err, context.DeadlineExceeded)
	case <-ctx.Done():
		t.Fatalf("ntest find best move test has timed out")
	}
}

func TestNTestShell_FindRankedMoves(t *testing.T) {
	// we need to run this twice to account for 'search' and 'book' lines
	cnstBoard := MakeInitialBoard()
	rndBoard, moveList := RandomBoard(50)

	player1 := MakePlayer("id1", "name1")
	player2 := MakePlayer("id2", "name2")

	tests := []struct {
		name string
		game OthelloGame
	}{
		// this will get 'book' or 'search' depending on whether this is the first run or not
		{
			name: "FirstSearch",
			game: OthelloGame{WhitePlayer: player1, BlackPlayer: player2, Board: cnstBoard},
		},
		// this will get 'book' because the previous search has the same board
		{
			name: "RepeatSearch",
			game: OthelloGame{WhitePlayer: player1, BlackPlayer: player2, Board: cnstBoard},
		},
		// this will get 'search' always since the board is cryptographically so random there are *ZERO* chances it could be in the book
		{
			name: "RandomBoard",
			game: OthelloGame{WhitePlayer: player1, BlackPlayer: player2, Board: rndBoard, MoveList: moveList},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Logf("find ranked move test board:\n%s%s", tt.game.Board.String(), tt.game.MarshalGGF())

			ctx, cancel := context.WithTimeout(t.Context(), time.Second*1)
			defer cancel()

			type findRankedMovesResult struct {
				ok  MoveResult
				err error
			}
			stopChan := make(chan findRankedMovesResult)

			go func() {
				result, err := setupTestShell(t).FindRankedMoves(ctx, tt.game, 6)
				stopChan <- findRankedMovesResult{ok: result, err: err}
			}()

			select {
			case result := <-stopChan:
				assert.Nil(t, result.err)
				assert.Equal(t, len(tt.game.Board.FindCurrentMoves()), len(result.ok.Moves))
			case <-ctx.Done():
				t.Fatalf("ntest find ranked moves test has timed out")
			}
		})
	}
}

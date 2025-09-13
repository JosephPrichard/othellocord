package app

import (
	"context"
	"database/sql"
	"fmt"
	"testing"

	"github.com/stretchr/testify/assert"
)

func setupGamesTest(t *testing.T) (*sql.DB, func()) {
	db, cleanup := createTestDB()
	ctx := context.WithValue(context.Background(), TraceKey, "seed-insert-games")

	games := []OthelloGame{
		{
			Board:       InitialBoard(),
			BlackPlayer: Player{ID: "id1", Name: "Player1"},
			WhitePlayer: Player{ID: "id2", Name: "Player2"},
			MoveList:    []Tile{},
		},
	}

	for _, game := range games {
		if err := SetGame(ctx, db, game); err != nil {
			t.Fatal("failed to insert games:", err)
		}
	}

	return db, cleanup
}

func TestGameStore_CreateGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-create-game")
	game, err := CreateGame(ctx, db, Player{ID: "id3", Name: "Player3"}, Player{ID: "id4", Name: "Player4"})
	if err != nil {
		t.Fatalf("failed to create the game: %v", err)
	}

	dbGame, err := GetGame(ctx, db, "id3")
	if err != nil {
		t.Fatalf("failed to get game: %v", err)
	}

	expGame := OthelloGame{Board: InitialBoard(), BlackPlayer: Player{ID: "id3", Name: "Player3"}, WhitePlayer: Player{ID: "id4", Name: "Player4"}}

	assert.Equal(t, expGame, game)
	assert.Equal(t, expGame, dbGame)
}

func TestGameStore_CreateBotGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-create-bot-game")
	game, err := CreateBotGame(ctx, db, Player{ID: "id3", Name: "Player3"}, 5)
	if err != nil {
		t.Fatalf("failed to create the game: %v", err)
	}

	dbGame, err := GetGame(ctx, db, "id3")
	if err != nil {
		t.Fatalf("failed to get game: %v", err)
	}

	expGame := OthelloGame{Board: InitialBoard(), BlackPlayer: Player{ID: "id3", Name: "Player3"}, WhitePlayer: MakeBotPlayer(5)}

	assert.Equal(t, expGame, game)
	assert.Equal(t, expGame, dbGame)
}

func TestGameStore_GetGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-get-game")

	game, err := GetGame(ctx, db, "id1")
	if err != nil {
		t.Fatalf("failed to get the game: %v", err)
	}

	expGame := OthelloGame{Board: InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id2", Name: "Player2"}}
	assert.Equal(t, game, expGame)
}

func TestGameStore_MakeMove(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	initialGame := OthelloGame{Board: InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id3", Name: "Player3"}}
	testMove := initialGame.Board.FindCurrentMoves()[0]
	expGame := initialGame
	expGame.CurrPotentialMoves = []Tile{{Row: 4, Col: 2}, {Row: 2, Col: 4}, {Row: 2, Col: 2}}
	expGame.Board.MakeMove(testMove)

	type Test struct {
		playerID string
		move     Tile
		expGame  OthelloGame
		expErr   error
	}
	tests := []Test{
		{playerID: "id1", move: testMove, expGame: expGame},
		{playerID: "id5", expErr: ErrGameNotFound},
		{playerID: "id2", expErr: ErrTurn},
		{playerID: "id1", move: Tile{Row: 0, Col: 1}, expErr: ErrInvalidMove},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-make-move-validated")

			game, err := MakeMoveValidated(ctx, db, test.playerID, test.move)

			assert.Equal(t, test.expErr, err)
			if err != nil {
				assert.Equal(t, test.expGame, game)
			}
		})
	}
}

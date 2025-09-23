package app

import (
	"context"
	"database/sql"
	"fmt"
	"testing"
	"time"

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
		},
		{
			Board:       InitialBoard(),
			BlackPlayer: Player{ID: "id10", Name: "Player10"},
			WhitePlayer: Player{ID: "id20", Name: "Player20"},
		},
	}

	for _, game := range games {
		if err := SetGame(ctx, db, game, time.Time{}); err != nil {
			t.Fatal("failed to insert games:", err)
		}
	}

	return db, cleanup
}

func TestGameStore_CreateGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-create-Game")
	game, err := CreateGame(ctx, db, Player{ID: "id3", Name: "Player3"}, Player{ID: "id4", Name: "Player4"})
	if err != nil {
		t.Fatalf("failed to create the Game: %v", err)
	}

	dbGame, err := GetGame(ctx, db, "id3")
	if err != nil {
		t.Fatalf("failed to get Game: %v", err)
	}

	expGame := OthelloGame{Board: InitialBoard(), BlackPlayer: Player{ID: "id3", Name: "Player3"}, WhitePlayer: Player{ID: "id4", Name: "Player4"}}

	assert.Equal(t, expGame, game)
	assert.Equal(t, expGame, dbGame)
}

func TestGameStore_CreateBotGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-create-bot-Game")
	game, err := CreateBotGame(ctx, db, Player{ID: "id3", Name: "Player3"}, 5)
	if err != nil {
		t.Fatalf("failed to create the Game: %v", err)
	}

	dbGame, err := GetGame(ctx, db, "id3")
	if err != nil {
		t.Fatalf("failed to get Game: %v", err)
	}

	expGame := OthelloGame{Board: InitialBoard(), BlackPlayer: Player{ID: "id3", Name: "Player3"}, WhitePlayer: MakeBotPlayer(5)}

	assert.Equal(t, expGame, game)
	assert.Equal(t, expGame, dbGame)
}

func TestGameStore_GetGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-get-Game")

	game, err := GetGame(ctx, db, "id1")
	if err != nil {
		t.Fatalf("failed to get the Game: %v", err)
	}

	expGame := OthelloGame{Board: InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id2", Name: "Player2"}}
	assert.Equal(t, game, expGame)
}

func TestGameStore_ExpireGames(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-expire-games")

	countGames := func() int {
		num, err := CountGames(db)
		if err != nil {
			t.Fatalf("failed to count games: %v", err)
		}
		return num
	}

	assert.Equal(t, 2, countGames())

	err := ExpireGames(ctx, db)
	if err != nil {
		t.Fatalf("failed to expire games: %v", err)
	}

	assert.Equal(t, 0, countGames())
}

func TestGameStore_MakeMove(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	initialGame := OthelloGame{Board: InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id2", Name: "Player2"}}
	testMove := initialGame.Board.FindCurrentMoves()[0]
	expGame := initialGame
	expGame.MakeMove(testMove)

	type Test struct {
		playerID string
		move     Tile
		expGame  OthelloGame
		expErr   error
	}
	tests := []Test{
		{playerID: "id5", expErr: ErrGameNotFound},
		{playerID: "id2", expErr: ErrTurn},
		{playerID: "id1", move: Tile{Row: 0, Col: 1}, expErr: ErrInvalidMove},
		{playerID: "id1", move: testMove, expGame: expGame},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			ctx := context.WithValue(context.Background(), TraceKey, "test-make-move-validated")

			game, err := MakeMoveValidated(ctx, db, test.playerID, test.move)

			assert.Equal(t, test.expErr, err)

			if err == nil {
				dbGame, err := GetGame(ctx, db, "id1")
				if err != nil {
					t.Fatalf("failed to get the Game: %v", err)
				}

				assert.Equal(t, test.expGame, game)
				assert.Equal(t, test.expGame, dbGame)
			}
		})
	}
}

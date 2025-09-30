package app

import (
	"context"
	"fmt"
	"github.com/jmoiron/sqlx"
	"math"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func setupGamesTest(t *testing.T) (*sqlx.DB, func()) {
	db, cleanup := createTestDB()
	ctx := context.WithValue(context.Background(), TraceKey, "seed-insert-games")

	games := []OthelloGame{
		{
			ID:          "1",
			Board:       InitialBoard(),
			BlackPlayer: Player{ID: "id1", Name: "Player1"},
			WhitePlayer: Player{ID: "id2", Name: "Player2"},
		},
		{
			ID:          "2",
			Board:       InitialBoard(),
			BlackPlayer: Player{ID: "id10", Name: "Player10"},
			WhitePlayer: Player{ID: "id20", Name: "Player20"},
		},
	}

	for _, game := range games {
		if err := SetGameTimeWithTime(ctx, db, game, time.Time{}); err != nil {
			t.Fatal("failed to insert games:", err)
		}
	}

	return db, cleanup
}

func TestGameStore_CreateGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-create-game")
	game, err := CreateGameTx(ctx, db, Player{ID: "id3", Name: "Player3"}, Player{ID: "id4", Name: "Player4"})
	if err != nil {
		t.Fatalf("failed to create the Game: %v", err)
	}

	dbGame, err := GetGame(ctx, db, "id3")
	if err != nil {
		t.Fatalf("failed to get game: %v", err)
	}

	expGame := OthelloGame{ID: game.ID, Board: InitialBoard(), BlackPlayer: Player{ID: "id3", Name: "Player3"}, WhitePlayer: Player{ID: "id4", Name: "Player4"}}

	assert.Equal(t, expGame, game)
	assert.Equal(t, expGame, dbGame)
}

func TestGameStore_CreateBotGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-create-bot-game")
	game, err := CreateBotGameTx(ctx, db, Player{ID: "id3", Name: "Player3"}, 5)
	if err != nil {
		t.Fatalf("failed to create the game: %v", err)
	}

	dbGame, err := GetGame(ctx, db, "id3")
	if err != nil {
		t.Fatalf("failed to get game: %v", err)
	}

	expGame := OthelloGame{ID: game.ID, Board: InitialBoard(), BlackPlayer: Player{ID: "id3", Name: "Player3"}, WhitePlayer: MakeBotPlayer(5)}

	assert.Equal(t, expGame, game)
	assert.Equal(t, expGame, dbGame)
}

func TestGameStore_GetGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-get-game")

	game, err := GetGame(ctx, db, "id1")
	if err != nil {
		t.Fatalf("failed to get the Game: %v", err)
	}

	expGame := OthelloGame{ID: "1", Board: InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id2", Name: "Player2"}}
	assert.Equal(t, game, expGame)
}

func TestGameStore_ExpireGames(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-expire-games")

	c1, err := CountGames(db)
	if err != nil {
		t.Fatalf("failed to count games: %v", err)
	}
	err = ExpireGames(ctx, db)
	if err != nil {
		t.Fatalf("failed to expire games: %v", err)
	}
	c2, err := CountGames(db)
	if err != nil {
		t.Fatalf("failed to count games: %v", err)
	}
	stats, err := GetTopStats(ctx, db, 10)
	if err != nil {
		t.Fatalf("failed to get top stats: %v", err)
	}

	for i := range stats {
		stats[i].Elo = math.Round(stats[i].Elo)
	}

	expStats := []StatsRow{
		{
			PlayerID: "id20",
			Elo:      1515,
			Won:      1,
			Drawn:    0,
			Lost:     0,
		},
		{
			PlayerID: "id2",
			Elo:      1515,
			Won:      1,
			Drawn:    0,
			Lost:     0,
		},
		{
			PlayerID: "id10",
			Elo:      1486,
			Won:      0,
			Drawn:    0,
			Lost:     1,
		},
		{
			PlayerID: "id1",
			Elo:      1486,
			Won:      0,
			Drawn:    0,
			Lost:     1,
		},
	}

	assert.Equal(t, 2, c1)
	assert.Equal(t, 0, c2)
	assert.Equal(t, expStats, stats)
}

func TestGameStore_MakeMove(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	initialGame := OthelloGame{ID: "1", Board: InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id2", Name: "Player2"}}
	testMove := initialGame.Board.FindCurrentMoves()[0]
	expGame := initialGame
	expGame.MakeMove(testMove)

	type Test struct {
		playerID string
		move     Tile
		expGame  OthelloGame
		expSr    StatsResult
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
			ctx := context.WithValue(context.Background(), TraceKey, "test-make-move")

			game, sr, err := MakeMoveAgainstHuman(ctx, db, test.playerID, test.move)
			if err != nil {
				assert.ErrorIs(t, err, test.expErr)
			} else {
				dbGame, err := GetGame(ctx, db, "id1")
				if err != nil {
					t.Fatalf("failed to get the game: %v", err)
				}
				assert.Equal(t, test.expSr, sr)
				assert.Equal(t, test.expGame, game)
				assert.Equal(t, test.expGame, dbGame)
			}
		})
	}
}

package app

import (
	"github.com/jmoiron/sqlx"
	"math"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func setupGamesTest(t *testing.T) (*sqlx.DB, func()) {
	db, cleanup := createTestDB()
	ctx := t.Context()

	games := []OthelloGame{
		{
			ID:          "1",
			Board:       MakeInitialBoard(),
			BlackPlayer: Player{ID: "id1", Name: "Player1"},
			WhitePlayer: Player{ID: "id2", Name: "Player2"},
		},
		{
			ID:          "2",
			Board:       MakeInitialBoard(),
			BlackPlayer: Player{ID: "id10", Name: "Player10"},
			WhitePlayer: Player{ID: "id20", Name: "Player20"},
			MoveList:    []Move{{Tile: Tile{Row: 0, Col: 0}}},
		},
	}

	for _, game := range games {
		if err := setGameWithTime(ctx, db, game, time.Time{}); err != nil {
			t.Fatal("failed to insert games:", err)
		}
	}

	return db, cleanup
}

func TestGameService_CreateThenGetGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()
	ctx := t.Context()

	gameService := GameService{database: db}

	game, err := gameService.CreateGame(ctx, Player{ID: "id3", Name: "Player3"}, Player{ID: "id4", Name: "Player4"})
	if err != nil {
		t.Fatalf("failed to create the Game: %v", err)
	}

	dbGame, err := gameService.GetGame(ctx, "id3")
	if err != nil {
		t.Fatalf("failed to get game: %v", err)
	}

	expGame := OthelloGame{ID: game.ID, Board: MakeInitialBoard(), BlackPlayer: Player{ID: "id3", Name: "Player3"}, WhitePlayer: Player{ID: "id4", Name: "Player4"}}

	assert.Equal(t, expGame, game)
	assert.Equal(t, expGame, dbGame)
}

func TestGameService_CreateThenGetBotGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()
	ctx := t.Context()

	gameService := GameService{database: db}

	game, err := gameService.CreateBotGame(ctx, Player{ID: "id3", Name: "Player3"}, 5)
	if err != nil {
		t.Fatalf("failed to create the game: %v", err)
	}

	dbGame, err := gameService.GetGame(ctx, "id3")
	if err != nil {
		t.Fatalf("failed to get game: %v", err)
	}

	expGame := OthelloGame{ID: game.ID, Board: MakeInitialBoard(), BlackPlayer: Player{ID: "id3", Name: "Player3"}, WhitePlayer: MakeBotPlayer(5)}

	assert.Equal(t, expGame, game)
	assert.Equal(t, expGame, dbGame)
}

func TestGameService_GetGame(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	gameService := GameService{database: db}

	game, err := gameService.GetGame(t.Context(), "id1")
	if err != nil {
		t.Fatalf("failed to get the game: %v", err)
	}

	expGame := OthelloGame{ID: "1", Board: MakeInitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id2", Name: "Player2"}}
	assert.Equal(t, expGame, game)
}

func TestGameService_GetGameMoves(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()

	gameService := GameService{database: db}

	game, err := gameService.GetGame(t.Context(), "id10")
	if err != nil {
		t.Fatalf("failed to get the moves: %v", err)
	}

	expMoves := []Move{{Tile: Tile{Row: 0, Col: 0}}}
	assert.Equal(t, expMoves, game.MoveList)
}

func TestGameService_ExpireGames_ThenGetTopStats(t *testing.T) {
	db, cleanup := setupGamesTest(t)
	defer cleanup()
	ctx := t.Context()

	gameService := GameService{database: db}

	count1 := countGames(t, db)

	if err := gameService.ExpireGames(ctx); err != nil {
		t.Fatalf("failed to expire games: %v", err)
	}

	count2 := countGames(t, db)

	stats, err := getTopStats(ctx, db, 10)
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

	assert.Equal(t, 2, count1)
	assert.Equal(t, 0, count2)
	assert.Equal(t, expStats, stats)
}

func TestGameService_MakeMove_ThenGet(t *testing.T) {
	initialGame := OthelloGame{ID: "1", Board: MakeInitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id2", Name: "Player2"}}
	firstMove := initialGame.Board.FindCurrentMoves()[0]
	expGame := initialGame
	expGame.MakeMove(firstMove)

	tests := []struct {
		name           string
		playerID       string
		move           Tile
		expGame        OthelloGame
		expStatsResult StatsResult
		expErr         error
	}{
		{
			name:     "GameNotFound",
			playerID: "id5",
			expErr:   ErrGameNotFound,
		},
		{
			name:     "NotYourTurn",
			playerID: "id2",
			expErr:   ErrTurn,
		},
		{
			name:     "InvalidMove",
			playerID: "id1",
			move:     Tile{Row: 0, Col: 1},
			expErr:   ErrInvalidMove,
		},
		{
			name:     "ValidMove",
			playerID: "id1",
			move:     firstMove,
			expGame:  expGame,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := t.Context()

			db, cleanup := setupGamesTest(t)
			defer cleanup()

			gameService := GameService{database: db}

			game, statsResult, err := gameService.MakeMoveAgainstHuman(ctx, MoveAgainstHuman{tt.playerID, tt.move})
			if err != nil {
				assert.ErrorIs(t, err, tt.expErr)
			} else {
				dbGame, err := gameService.GetGame(ctx, tt.playerID)
				if err != nil {
					t.Fatalf("failed to get the game: %v", err)
				}
				assert.Equal(t, tt.expStatsResult, statsResult)
				assert.Equal(t, tt.expGame, game)
				assert.Equal(t, tt.expGame, dbGame)
			}
		})
	}
}

func countGames(t *testing.T, db *sqlx.DB) int {
	var count int
	if err := db.Get(&count, "SELECT COUNT(*) FROM games;"); err != nil {
		t.Fatalf("failed to count games: %v", err)
	}
	return count
}

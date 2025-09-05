package bot

import (
	"context"
	"fmt"
	"othellocord/app/othello"
	"testing"

	"github.com/jellydator/ttlcache/v3"
	"github.com/stretchr/testify/assert"
)

func TestGameStore_CreateGame(t *testing.T) {
	gs := ttlcache.New[string, *GameState]()

	ctx := context.WithValue(context.Background(), TraceKey, "test-create-game")
	game, err := CreateGame(ctx, gs, Player{ID: "id1", Name: "Player1"}, Player{ID: "id2", Name: "Player2"})
	if err != nil {
		t.Fatalf("failed to create the game: %v", err)
	}

	item1 := gs.Get("id1")
	item2 := gs.Get("id2")

	assert.NotNil(t, item1)
	assert.NotNil(t, item2)
	assert.Equal(t, game, Game{Board: othello.InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id2", Name: "Player2"}})
	assert.Equal(t, item1.Value().Game, game)
	assert.Equal(t, item2.Value().Game, game)
}

func TestGameStore_CreateBotGame(t *testing.T) {
	gs := ttlcache.New[string, *GameState]()

	ctx := context.WithValue(context.Background(), TraceKey, "test-create-bot-game")
	game, err := CreateBotGame(ctx, gs, Player{ID: "id1", Name: "Player1"}, 5)
	if err != nil {
		t.Fatalf("failed to create the game: %v", err)
	}

	item1 := gs.Get("id1")

	assert.NotNil(t, item1)
	assert.Equal(t, game, Game{Board: othello.InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "5", Name: GetBotLevelFmt(5)}})
	assert.Equal(t, item1.Value().Game, game)
}

func TestGameStore_GetGame(t *testing.T) {
	gs := ttlcache.New[string, *GameState]()

	expGame := Game{Board: othello.InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id3", Name: "Player3"}}
	gs.Set("id1", &GameState{Game: expGame}, GameStoreTtl)

	ctx := context.WithValue(context.Background(), TraceKey, "test-get-game")
	game, err := GetGame(ctx, gs, "id1")
	if err != nil {
		t.Fatalf("failed to get the game: %v", err)
	}
	assert.Equal(t, game, expGame)
}

func TestGameStore_MakeMoveValidated(t *testing.T) {
	initialGame := Game{Board: othello.InitialBoard(), BlackPlayer: Player{ID: "id1", Name: "Player1"}, WhitePlayer: Player{ID: "id3", Name: "Player3"}}
	testMove := initialGame.FindCurrentMoves()[0]
	expGame := initialGame
	expGame.CurrPotentialMoves = []othello.Tile{{Row: 4, Col: 2}, {Row: 2, Col: 4}, {Row: 2, Col: 2}}
	expGame.Board.MakeMove(testMove)

	type Test struct {
		playerID string
		move     othello.Tile
		expGame  Game
		expErr   error
	}
	tests := []Test{
		{playerID: "id1", move: testMove, expGame: expGame},
		{playerID: "id5", expErr: ErrGameNotFound},
		{playerID: "id3", expErr: ErrTurn},
		{playerID: "id1", move: othello.Tile{Row: 0, Col: 1}, expErr: ErrInvalidMove},
	}

	for i, test := range tests {
		t.Run(fmt.Sprintf("%d", i), func(t *testing.T) {
			gs := ttlcache.New[string, *GameState]()
			state := &GameState{Game: initialGame}
			gs.Set("id1", state, GameStoreTtl)
			gs.Set("id3", state, GameStoreTtl)

			game, err := MakeMoveValidated(gs, test.playerID, test.move)

			assert.Equal(t, test.expErr, err)
			if err != nil {
				assert.Equal(t, test.expGame, game)
			}
		})
	}
}

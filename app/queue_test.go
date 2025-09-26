package app

import (
	"context"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestQueue(t *testing.T) {
	db, cleanup := createTestDB()
	defer cleanup()

	ctx := context.WithValue(context.Background(), TraceKey, "test-queue")

	createAndPush := func(whitePlayer Player, blackPlayer Player) string {
		newGame, err := CreateGame(ctx, db, blackPlayer, whitePlayer)
		if err != nil {
			t.Fatalf("failed to create game: %v", err)
		}
		gameID := newGame.ID
		if err := PushQueueNow(ctx, db, gameID, "1"); err != nil {
			t.Fatalf("failed to push queue: %v", err)
		}
		return gameID
	}

	gameID1 := createAndPush(MakePlayer("1", "user1"), MakeBotPlayer(1))
	gameID2 := createAndPush(MakePlayer("2", "user2"), MakeBotPlayer(2))

	tasksBefore, err := SelectQueue(ctx, db)
	if err != nil {
		t.Fatalf("failed to select queue after pushing: %v", err)
	}
	if err := AckQueue(ctx, db, gameID1); err != nil {
		t.Fatalf("failed to ack queue: %v", err)
	}
	tasksAfter, err := SelectQueue(ctx, db)
	if err != nil {
		t.Fatalf("failed to select queue after acking: %v", err)
	}

	expTasks := []BotTask{
		{channelID: "1", game: OthelloGame{ID: gameID2, Board: InitialBoard(), WhitePlayer: MakePlayer("2", "user2"), BlackPlayer: MakeBotPlayer(2)}},
	}

	assert.Equal(t, 2, len(tasksBefore))
	assert.Equal(t, expTasks, tasksAfter)
}

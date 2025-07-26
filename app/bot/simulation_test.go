package bot

import (
	"context"
	"github.com/stretchr/testify/assert"
	"othellocord/app/othello"
	"testing"
	"time"
)

func TestSimulation(t *testing.T) {
	ctx, cancel := context.WithDeadline(context.WithValue(context.Background(), "trace", "simulation-test"), time.Now().Add(time.Second))
	defer cancel()

	initialGame := Game{
		Board:       othello.InitialBoard(),
		WhitePlayer: Player{ID: "1", Name: "Bot 1"},
		BlackPlayer: Player{ID: "2", Name: "Bot 2"},
	}
	wq := make(chan WorkerRequest)
	simChan := make(chan SimMsg, SimCount)

	countSent := 0
	countRecv := 0

	go func() {
		for request := range wq {
			moves := request.Board.FindCurrentMoves()
			var rankedTiles []othello.RankTile
			if len(moves) > 0 {
				rankedTiles = append(rankedTiles, othello.RankTile{Tile: moves[0], H: 1})
			}
			countSent++
			request.RespChan <- rankedTiles
		}
	}()

	finishChan := make(chan struct{}, 1)
	go func() {
		Simulation(ctx, wq, initialGame, simChan)
		for range simChan {
			countRecv++
		}
		finishChan <- struct{}{}
	}()

	select {
	case <-finishChan:
		assert.Equal(t, countSent, countRecv)
	case <-ctx.Done():
		t.Fatalf("simulation test timed out")
	}
}

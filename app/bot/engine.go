package bot

import (
	"log/slog"
	"othellocord/app/othello"
	"runtime"
)

type MoveRequestKind int

const (
	GetMovesRequestKind MoveRequestKind = iota
	GetMoveRequestKind
)

type WorkerRequest struct {
	Board    othello.Board
	Depth    int
	Kind     MoveRequestKind
	RespChan chan []othello.RankTile
}

func ListenWorkerRequest(w int, wq chan WorkerRequest) {
	engine := othello.MakeEngine()
	for request := range wq {
		slog.Info("received an engine request on worker", "worker", w)

		var moves []othello.RankTile
		switch request.Kind {
		case GetMovesRequestKind:
			moves = engine.FindRankedMoves(request.Board, request.Depth)
		case GetMoveRequestKind:
			if move, ok := engine.FindBestMove(request.Board, request.Depth); ok {
				moves = append(moves, move)
			}
		default:
			slog.Warn("invalid request type", "worker", w)
		}

		request.RespChan <- moves
		close(request.RespChan)
	}
}

func StartWorkers() chan WorkerRequest {
	count := runtime.NumCPU() / 2
	slog.Info("starting workers", "count", count)

	wq := make(chan WorkerRequest, 16)
	for w := range count {
		go ListenWorkerRequest(w, wq)
	}
	return wq
}
